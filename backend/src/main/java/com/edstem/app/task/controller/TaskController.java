package com.edstem.app.task.controller;

import com.edstem.app.common.dto.response.ApiResponse;
import com.edstem.app.common.dto.response.PageResponse;
import com.edstem.app.task.dto.request.CreateTaskRequest;
import com.edstem.app.task.dto.request.UpdateTaskRequest;
import com.edstem.app.task.dto.response.TaskResponse;
import com.edstem.app.task.entity.TaskStatus;
import com.edstem.app.task.service.TaskService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

  private final TaskService taskService;

  @PostMapping
  public ResponseEntity<ApiResponse<TaskResponse>> create(
      @Valid @RequestBody CreateTaskRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(taskService.create(request)));
  }

  @GetMapping
  public ApiResponse<PageResponse<TaskResponse>> list(
      @RequestParam(required = false) TaskStatus status,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ApiResponse.ok(PageResponse.from(taskService.list(status, pageable)));
  }

  @GetMapping("/{id}")
  public ApiResponse<TaskResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(taskService.get(id));
  }

  @PutMapping("/{id}")
  public ApiResponse<TaskResponse> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateTaskRequest request) {
    return ApiResponse.ok(taskService.update(id, request));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable UUID id) {
    taskService.delete(id);
    return ApiResponse.ok(null, "Task deleted");
  }
}
