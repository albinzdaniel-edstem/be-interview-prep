package com.edstem.app.auth.dto.request;

import com.edstem.app.auth.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "Email is required") String email,
    @NotBlank(message = "Password is required")
        @MaxUtf8Bytes(value = 72, message = "Password must be at most 72 bytes long")
        String password) {}
