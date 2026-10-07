package com.edstem.app.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edstem.app.task.dto.request.CreateTaskRequest;
import com.edstem.app.task.dto.request.UpdateTaskRequest;
import com.edstem.app.task.dto.response.TaskResponse;
import com.edstem.app.task.entity.Task;
import com.edstem.app.task.entity.TaskStatus;
import com.edstem.app.task.exception.TaskNotFoundException;
import com.edstem.app.task.mapper.TaskMapper;
import com.edstem.app.task.repository.TaskRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

  @Mock private TaskRepository taskRepository;
  @Spy private TaskMapper taskMapper = new TaskMapper();
  @InjectMocks private TaskService taskService;

  @Test
  void createDefaultsStatusToTodoAndTrimsTitle() {
    LocalDate due = LocalDate.now().plusDays(2);
    CreateTaskRequest request = new CreateTaskRequest("  Write report  ", "Quarterly", null, due);
    when(taskRepository.save(any(Task.class))).thenAnswer(call -> call.getArgument(0));

    TaskResponse response = taskService.create(request);

    assertThat(response.title()).isEqualTo("Write report");
    assertThat(response.status()).isEqualTo(TaskStatus.TODO);
    assertThat(response.dueDate()).isEqualTo(due);
  }

  @Test
  void createKeepsTheGivenStatus() {
    CreateTaskRequest request = new CreateTaskRequest("Review", null, TaskStatus.IN_PROGRESS, null);
    when(taskRepository.save(any(Task.class))).thenAnswer(call -> call.getArgument(0));

    TaskResponse response = taskService.create(request);

    assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
  }

  @Test
  void listWithoutStatusReturnsAllTasks() {
    Pageable pageable = PageRequest.of(0, 10);
    Page<Task> page = new PageImpl<>(List.of(task("A", TaskStatus.TODO)));
    when(taskRepository.findAll(pageable)).thenReturn(page);

    Page<TaskResponse> result = taskService.list(null, pageable);

    assertThat(result.getContent()).extracting(TaskResponse::title).containsExactly("A");
    verify(taskRepository, never()).findByStatus(any(), any());
  }

  @Test
  void listWithStatusFiltersByStatus() {
    Pageable pageable = PageRequest.of(0, 10);
    Page<Task> page = new PageImpl<>(List.of(task("B", TaskStatus.DONE)));
    when(taskRepository.findByStatus(TaskStatus.DONE, pageable)).thenReturn(page);

    Page<TaskResponse> result = taskService.list(TaskStatus.DONE, pageable);

    assertThat(result.getContent()).extracting(TaskResponse::title).containsExactly("B");
    verify(taskRepository, never()).findAll(any(Pageable.class));
  }

  @Test
  void getReturnsTheTask() {
    Task task = task("A", TaskStatus.TODO);
    when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

    TaskResponse response = taskService.get(task.getId());

    assertThat(response.id()).isEqualTo(task.getId());
  }

  @Test
  void getThrowsWhenTaskDoesNotExist() {
    UUID id = UUID.randomUUID();
    when(taskRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> taskService.get(id)).isInstanceOf(TaskNotFoundException.class);
  }

  @Test
  void updateReplacesTheFields() {
    Task task = task("Old", TaskStatus.TODO);
    LocalDate due = LocalDate.now().plusDays(5);
    UpdateTaskRequest request = new UpdateTaskRequest("New", "Changed", TaskStatus.DONE, due);
    when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

    TaskResponse response = taskService.update(task.getId(), request);

    assertThat(response.title()).isEqualTo("New");
    assertThat(response.description()).isEqualTo("Changed");
    assertThat(response.status()).isEqualTo(TaskStatus.DONE);
    assertThat(response.dueDate()).isEqualTo(due);
  }

  @Test
  void updateThrowsWhenTaskDoesNotExist() {
    UUID id = UUID.randomUUID();
    UpdateTaskRequest request = new UpdateTaskRequest("New", null, TaskStatus.DONE, null);
    when(taskRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> taskService.update(id, request))
        .isInstanceOf(TaskNotFoundException.class);
  }

  @Test
  void deleteRemovesTheTask() {
    Task task = task("A", TaskStatus.TODO);
    when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

    taskService.delete(task.getId());

    verify(taskRepository).delete(task);
  }

  @Test
  void deleteThrowsWhenTaskDoesNotExist() {
    UUID id = UUID.randomUUID();
    when(taskRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> taskService.delete(id)).isInstanceOf(TaskNotFoundException.class);
    verify(taskRepository, never()).delete(any(Task.class));
  }

  private static Task task(String title, TaskStatus status) {
    return Task.builder().id(UUID.randomUUID()).title(title).status(status).build();
  }
}
