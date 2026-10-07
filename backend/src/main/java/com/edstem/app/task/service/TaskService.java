package com.edstem.app.task.service;

import com.edstem.app.task.dto.request.CreateTaskRequest;
import com.edstem.app.task.dto.request.UpdateTaskRequest;
import com.edstem.app.task.dto.response.TaskResponse;
import com.edstem.app.task.entity.Task;
import com.edstem.app.task.entity.TaskStatus;
import com.edstem.app.task.exception.TaskNotFoundException;
import com.edstem.app.task.mapper.TaskMapper;
import com.edstem.app.task.repository.TaskRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

  private final TaskRepository taskRepository;
  private final TaskMapper taskMapper;

  @Transactional
  public TaskResponse create(CreateTaskRequest request) {
    Task saved = taskRepository.saveAndFlush(taskMapper.toEntity(request));
    log.info("Created task {}", saved.getId());
    return taskMapper.toResponse(saved);
  }

  @Transactional(readOnly = true)
  public Page<TaskResponse> list(TaskStatus status, Pageable pageable) {
    Page<Task> tasks =
        status == null
            ? taskRepository.findAll(pageable)
            : taskRepository.findByStatus(status, pageable);
    return tasks.map(taskMapper::toResponse);
  }

  @Transactional(readOnly = true)
  public TaskResponse get(UUID id) {
    return taskMapper.toResponse(findOrThrow(id));
  }

  @Transactional
  public TaskResponse update(UUID id, UpdateTaskRequest request) {
    Task task = findOrThrow(id);
    taskMapper.update(task, request);
    log.info("Updated task {}", id);
    return taskMapper.toResponse(task);
  }

  @Transactional
  public void delete(UUID id) {
    taskRepository.delete(findOrThrow(id));
    log.info("Deleted task {}", id);
  }

  private Task findOrThrow(UUID id) {
    return taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
  }
}
