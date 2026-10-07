package com.edstem.app.task.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edstem.app.common.exception.GlobalExceptionHandler;
import com.edstem.app.task.dto.request.CreateTaskRequest;
import com.edstem.app.task.dto.request.UpdateTaskRequest;
import com.edstem.app.task.dto.response.TaskResponse;
import com.edstem.app.task.entity.TaskStatus;
import com.edstem.app.task.exception.TaskNotFoundException;
import com.edstem.app.task.service.TaskService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TaskController.class)
@Import(GlobalExceptionHandler.class)
class TaskControllerTest {

  private static final String BASE_URL = "/api/v1/tasks";

  @Autowired private MockMvc mvc;
  @MockitoBean private TaskService taskService;

  @Test
  void createReturns201WithTheTask() throws Exception {
    TaskResponse created = response("Write report", TaskStatus.TODO);
    when(taskService.create(any(CreateTaskRequest.class))).thenReturn(created);
    String body = "{\"title\":\"Write report\",\"description\":\"Quarterly\"}";

    mvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(created.id().toString()))
        .andExpect(jsonPath("$.data.title").value("Write report"))
        .andExpect(jsonPath("$.data.status").value("TODO"));
  }

  @Test
  void createReportsEveryInvalidField() throws Exception {
    String longTitle = "x".repeat(101);
    String pastDate = LocalDate.now().minusDays(1).toString();
    String body = "{\"title\":\"%s\",\"dueDate\":\"%s\"}".formatted(longTitle, pastDate);

    mvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.status").value(400))
        .andExpect(jsonPath("$.error.fieldErrors", hasSize(2)))
        .andExpect(
            jsonPath("$.error.fieldErrors[?(@.field=='title')].message")
                .value(hasItem("Title must be at most 100 characters")))
        .andExpect(
            jsonPath("$.error.fieldErrors[?(@.field=='dueDate')].message")
                .value(hasItem("Due date must not be in the past")));
  }

  @Test
  void createRejectsABlankTitle() throws Exception {
    String body = "{\"title\":\"   \"}";

    mvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error.fieldErrors[?(@.field=='title')].message")
                .value(hasItem("Title is required")));
  }

  @Test
  void createAcceptsTodayAsDueDate() throws Exception {
    when(taskService.create(any(CreateTaskRequest.class)))
        .thenReturn(response("Due today", TaskStatus.TODO));
    String body = "{\"title\":\"Due today\",\"dueDate\":\"%s\"}".formatted(LocalDate.now());

    mvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());
  }

  @Test
  void createRejectsAnUnknownStatusValue() throws Exception {
    String body = "{\"title\":\"A\",\"status\":\"STARTED\"}";

    mvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.fieldErrors[0].field").value("status"))
        .andExpect(
            jsonPath("$.error.fieldErrors[0].message")
                .value("Invalid value 'STARTED'. Allowed values: TODO, IN_PROGRESS, DONE"));
  }

  @Test
  void listPassesTheStatusAndPagingToTheService() throws Exception {
    Pageable requested = PageRequest.of(1, 5);
    when(taskService.list(eq(TaskStatus.DONE), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(response("Done task", TaskStatus.DONE)), requested, 11));

    mvc.perform(
            get(BASE_URL)
                .param("status", "DONE")
                .param("page", "1")
                .param("size", "5")
                .param("sort", "title,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.page").value(1))
        .andExpect(jsonPath("$.data.size").value(5))
        .andExpect(jsonPath("$.data.totalElements").value(11))
        .andExpect(jsonPath("$.data.totalPages").value(3));

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(taskService).list(eq(TaskStatus.DONE), pageable.capture());
    assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
    assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    assertThat(pageable.getValue().getSort().getOrderFor("title")).isNotNull();
  }

  @Test
  void listWithoutParametersSortsNewestFirstWithoutAStatusFilter() throws Exception {
    when(taskService.list(any(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    mvc.perform(get(BASE_URL)).andExpect(status().isOk());

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(taskService).list(eq(null), pageable.capture());
    assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
  }

  @Test
  void listCapsThePageSizeAt100() throws Exception {
    when(taskService.list(any(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

    mvc.perform(get(BASE_URL).param("size", "500")).andExpect(status().isOk());

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(taskService).list(eq(null), pageable.capture());
    assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
  }

  @Test
  void listRejectsAnUnknownStatus() throws Exception {
    mvc.perform(get(BASE_URL).param("status", "STARTED"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
  }

  @Test
  void getReturnsTheTask() throws Exception {
    TaskResponse task = response("A", TaskStatus.TODO);
    when(taskService.get(task.id())).thenReturn(task);

    mvc.perform(get(BASE_URL + "/" + task.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(task.id().toString()));
  }

  @Test
  void getReturns404ForAnUnknownTask() throws Exception {
    UUID id = UUID.randomUUID();
    when(taskService.get(id)).thenThrow(new TaskNotFoundException(id));

    mvc.perform(get(BASE_URL + "/" + id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("TASK_NOT_FOUND"))
        .andExpect(jsonPath("$.error.status").value(404))
        .andExpect(jsonPath("$.error.path").value(BASE_URL + "/" + id));
  }

  @Test
  void getRejectsAnIdThatIsNotAUuid() throws Exception {
    mvc.perform(get(BASE_URL + "/not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
  }

  @Test
  void updateReturnsTheChangedTask() throws Exception {
    TaskResponse updated = response("New title", TaskStatus.DONE);
    when(taskService.update(eq(updated.id()), any(UpdateTaskRequest.class))).thenReturn(updated);
    String body = "{\"title\":\"New title\",\"status\":\"DONE\"}";

    mvc.perform(
            put(BASE_URL + "/" + updated.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.title").value("New title"))
        .andExpect(jsonPath("$.data.status").value("DONE"));
  }

  @Test
  void updateReportsEveryInvalidField() throws Exception {
    String pastDate = LocalDate.now().minusDays(1).toString();
    String body = "{\"title\":\"\",\"dueDate\":\"%s\"}".formatted(pastDate);

    mvc.perform(
            put(BASE_URL + "/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.fieldErrors", hasSize(3)))
        .andExpect(
            jsonPath("$.error.fieldErrors[?(@.field=='title')].message")
                .value(hasItem("Title is required")))
        .andExpect(
            jsonPath("$.error.fieldErrors[?(@.field=='status')].message")
                .value(hasItem("Status is required")))
        .andExpect(
            jsonPath("$.error.fieldErrors[?(@.field=='dueDate')].message")
                .value(hasItem("Due date must not be in the past")));
  }

  @Test
  void updateReturns404ForAnUnknownTask() throws Exception {
    UUID id = UUID.randomUUID();
    when(taskService.update(eq(id), any(UpdateTaskRequest.class)))
        .thenThrow(new TaskNotFoundException(id));
    String body = "{\"title\":\"A\",\"status\":\"TODO\"}";

    mvc.perform(put(BASE_URL + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("TASK_NOT_FOUND"));
  }

  @Test
  void deleteReturnsNoContent() throws Exception {
    UUID id = UUID.randomUUID();

    mvc.perform(delete(BASE_URL + "/" + id))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    verify(taskService).delete(id);
  }

  @Test
  void deleteReturns404ForAnUnknownTask() throws Exception {
    UUID id = UUID.randomUUID();
    org.mockito.Mockito.doThrow(new TaskNotFoundException(id)).when(taskService).delete(id);

    mvc.perform(delete(BASE_URL + "/" + id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("TASK_NOT_FOUND"));
  }

  private static TaskResponse response(String title, TaskStatus status) {
    return new TaskResponse(UUID.randomUUID(), title, null, status, null, Instant.now());
  }
}
