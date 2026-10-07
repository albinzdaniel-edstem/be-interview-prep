package com.edstem.app.task.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.edstem.app.task.entity.Task;
import com.edstem.app.task.entity.TaskStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
class TaskRepositoryTest {

  @Autowired private TaskRepository taskRepository;

  @Test
  void savesTaskWithGeneratedIdAndCreatedDate() {
    Task task = newTask("Write report", TaskStatus.TODO);

    Task saved = taskRepository.saveAndFlush(task);

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getCreatedAt()).isNotNull();
  }

  @Test
  void findsOnlyTasksWithTheGivenStatus() {
    taskRepository.save(newTask("First", TaskStatus.TODO));
    taskRepository.save(newTask("Second", TaskStatus.DONE));
    taskRepository.save(newTask("Third", TaskStatus.TODO));

    Page<Task> result = taskRepository.findByStatus(TaskStatus.TODO, PageRequest.of(0, 10));

    assertThat(result.getContent())
        .extracting(Task::getTitle)
        .containsExactlyInAnyOrder("First", "Third");
    assertThat(result.getTotalElements()).isEqualTo(2);
  }

  @Test
  void pagesTheFilteredResult() {
    taskRepository.save(newTask("First", TaskStatus.IN_PROGRESS));
    taskRepository.save(newTask("Second", TaskStatus.IN_PROGRESS));
    taskRepository.save(newTask("Third", TaskStatus.IN_PROGRESS));

    Page<Task> result = taskRepository.findByStatus(TaskStatus.IN_PROGRESS, PageRequest.of(1, 2));

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getTotalPages()).isEqualTo(2);
  }

  private static Task newTask(String title, TaskStatus status) {
    return Task.builder().title(title).status(status).dueDate(LocalDate.now().plusDays(3)).build();
  }
}
