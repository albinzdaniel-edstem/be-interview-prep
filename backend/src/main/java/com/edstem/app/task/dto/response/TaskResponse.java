package com.edstem.app.task.dto.response;

import com.edstem.app.task.entity.TaskStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TaskResponse(
    UUID id,
    String title,
    String description,
    TaskStatus status,
    LocalDate dueDate,
    Instant createdAt) {}
