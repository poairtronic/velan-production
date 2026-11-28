package com.velan.production.shared.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.velan.production.shared.error.CorrelationIdFilter;
import com.velan.production.shared.error.ErrorCode;
import com.velan.production.shared.error.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Per-client-IP fixed 1-minute window rate limiter for POST /api/v1/auth/login (P13).
 * Uses a standalone Caffeine cache (not a named Spring cache) bounded to 10,000 IPs.
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final int MAX_TRACKED_IPS = 10_000;

    private final Cache<String, WindowCounter> windows = Caffeine.newBuilder()
            .maximumSize(MAX_TRACKED_IPS)
            .expireAfterWrite(Duration.ofMinutes(2))
            .build();

    private final SecurityProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock utcClock;

    public LoginRateLimitFilter(
            SecurityProperties properties,
            ObjectMapper objectMapper,
            ObjectProvider<Clock> utcClockProvider) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.utcClock = utcClockProvider.getIfAvailable(Clock::systemUTC);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(HttpMethod.POST.matches(request.getMethod())
                && LOGIN_PATH.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Instant now = Instant.now(utcClock);
        long currentMinute = now.getEpochSecond() / 60;
        String clientIp = request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
        int limit = properties.loginRateLimit().perMinute();

        WindowCounter counter = windows.get(clientIp, k -> new WindowCounter(currentMinute));
        boolean allowed;
        synchronized (counter) {
            if (counter.windowMinute != currentMinute) {
                counter.windowMinute = currentMinute;
                counter.count = 1;
                allowed = true;
            } else if (counter.count < limit) {
                counter.count++;
                allowed = true;
            } else {
                allowed = false;
            }
        }

        if (!allowed) {
            long retryAfter = Math.max(1L, 60L - (now.getEpochSecond() % 60L));
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ErrorResponse body = new ErrorResponse(
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    ErrorCode.ERR_RATE_LIMITED.name(),
                    "Too many login attempts. Please try again later.",
                    List.of(),
                    MDC.get(CorrelationIdFilter.MDC_KEY),
                    now);
            objectMapper.writeValue(response.getWriter(), body);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static final class WindowCounter {
        long windowMinute;
        int count;

        WindowCounter(long windowMinute) {
            this.windowMinute = windowMinute;
            this.count = 0;
        }
    }
}
