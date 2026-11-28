package com.velan.production.iam.api;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Login is required") String login,
        @NotBlank(message = "Password is required") String password) {
}
