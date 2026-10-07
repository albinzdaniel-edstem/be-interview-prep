package com.edstem.app.task.repository;

import com.edstem.app.task.entity.Task;
import com.edstem.app.task.entity.TaskStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, UUID> {

  Page<Task> findByStatus(TaskStatus status, Pageable pageable);
}
