package com.velan.production.iam.api;

import com.velan.production.iam.internal.application.AuthService;
import com.velan.production.shared.error.ErrorCode;
import com.velan.production.shared.error.UnauthorizedException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request) {
        return switch (authService.login(request.login(), request.password())) {
            case AuthService.LoginResult.Success s -> s.response();
            case AuthService.LoginResult.Failure ignored ->
                    throw new UnauthorizedException(ErrorCode.ERR_UNAUTHORIZED, "Invalid credentials");
        };
    }
}
