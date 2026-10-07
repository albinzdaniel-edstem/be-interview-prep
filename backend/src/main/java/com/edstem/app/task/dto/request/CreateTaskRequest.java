package com.edstem.app.task.dto.request;

import com.edstem.app.task.entity.TaskStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateTaskRequest(
    @NotBlank(message = "Title is required")
        @Size(max = 100, message = "Title must be at most 100 characters")
        String title,
    @Size(max = 1000, message = "Description must be at most 1000 characters") String description,
    TaskStatus status,
    @FutureOrPresent(message = "Due date must not be in the past") LocalDate dueDate) {}
