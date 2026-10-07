package com.edstem.app.task.mapper;

import com.edstem.app.task.dto.request.CreateTaskRequest;
import com.edstem.app.task.dto.request.UpdateTaskRequest;
import com.edstem.app.task.dto.response.TaskResponse;
import com.edstem.app.task.entity.Task;
import com.edstem.app.task.entity.TaskStatus;
import org.springframework.stereotype.Component;

@Component
public class TaskMapper {

  public Task toEntity(CreateTaskRequest request) {
    return Task.builder()
        .title(request.title().trim())
        .description(request.description())
        .status(request.status() != null ? request.status() : TaskStatus.TODO)
        .dueDate(request.dueDate())
        .build();
  }

  public void update(Task task, UpdateTaskRequest request) {
    task.setTitle(request.title().trim());
    task.setDescription(request.description());
    task.setStatus(request.status());
    task.setDueDate(request.dueDate());
  }

  public TaskResponse toResponse(Task task) {
    return new TaskResponse(
        task.getId(),
        task.getTitle(),
        task.getDescription(),
        task.getStatus(),
        task.getDueDate(),
        task.getCreatedAt());
  }
}
