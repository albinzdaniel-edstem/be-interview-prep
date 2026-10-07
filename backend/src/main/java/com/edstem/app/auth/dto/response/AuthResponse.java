package com.edstem.app.auth.dto.response;

public record AuthResponse(String accessToken, String tokenType, long expiresIn) {}
