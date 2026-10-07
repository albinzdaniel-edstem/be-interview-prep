package com.edstem.app.task.exception;

import com.edstem.app.common.exception.BaseException;
import java.util.UUID;

public class TaskNotFoundException extends BaseException {

  public TaskNotFoundException(UUID id) {
    super(TaskErrorCode.TASK_NOT_FOUND, "Task not found: " + id);
  }
}
