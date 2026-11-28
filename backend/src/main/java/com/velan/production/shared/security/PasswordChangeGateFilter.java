package com.velan.production.shared.security;

import com.velan.production.shared.error.CorrelationIdFilter;
import com.velan.production.shared.error.ErrorCode;
import com.velan.production.shared.error.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Enforces P4: when an authenticated user has mustChangePassword = true,
 * only /auth/change-password, /auth/logout, /auth/refresh, and GET /auth/me are allowed.
 * All other requests return 403 ERR_PASSWORD_CHANGE_REQUIRED.
 */
public class PasswordChangeGateFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final Clock utcClock;

    public PasswordChangeGateFilter(
            ObjectMapper objectMapper,
            ObjectProvider<Clock> utcClockProvider) {
        this.objectMapper = objectMapper;
        this.utcClock = utcClockProvider.getIfAvailable(Clock::systemUTC);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null
                && auth.isAuthenticated()
                && auth.getPrincipal() instanceof AuthenticatedUser user
                && user.mustChangePassword()
                && !isAllowedDuringPasswordChange(request)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ErrorResponse body = new ErrorResponse(
                    HttpStatus.FORBIDDEN.value(),
                    ErrorCode.ERR_PASSWORD_CHANGE_REQUIRED.name(),
                    "Password change required",
                    List.of(),
                    MDC.get(CorrelationIdFilter.MDC_KEY),
                    Instant.now(utcClock));
            objectMapper.writeValue(response.getWriter(), body);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isAllowedDuringPasswordChange(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if (HttpMethod.POST.matches(method)) {
            return "/api/v1/auth/change-password".equals(path)
                    || "/api/v1/auth/logout".equals(path)
                    || "/api/v1/auth/refresh".equals(path);
        }
        if (HttpMethod.GET.matches(method)) {
            return "/api/v1/auth/me".equals(path);
        }
        return false;
    }
}
