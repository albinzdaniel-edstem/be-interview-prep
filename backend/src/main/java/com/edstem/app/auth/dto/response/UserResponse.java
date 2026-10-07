package com.edstem.app.auth.dto.response;

import com.edstem.app.auth.entity.Role;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, Role role, Instant createdAt) {}
