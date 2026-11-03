/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.controllers;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.data.task.InMemoryTaskRepository;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.OperationsService;
import com.netflix.spinnaker.clouddriver.orchestration.OrchestrationProcessor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * A task still running when the shutdown wait ends would otherwise stay STARTED until the caller's
 * own timeout. It must be failed explicitly instead, retryable only when a saga can resume it.
 */
class OperationsControllerShutdownTest {

  private final TaskRepository taskRepository = mock(TaskRepository.class);

  // a zero wait skips the polling loop, so destroy() goes straight to failing what is left
  private final OperationsController controller =
      new OperationsController(
          mock(OperationsService.class), mock(OrchestrationProcessor.class), taskRepository, 0);

  @Test
  void failsSagaBackedTaskAsRetryable() {
    Task task = mock(Task.class);
    when(task.hasSagaIds()).thenReturn(true);
    when(taskRepository.listByThisInstance()).thenReturn(List.of(task));

    controller.destroy();

    verify(task).fail(true);
  }

  @Test
  void failsTaskWithoutSagaAsNotRetryable() {
    Task task = mock(Task.class);
    when(task.hasSagaIds()).thenReturn(false);
    when(taskRepository.listByThisInstance()).thenReturn(List.of(task));

    controller.destroy();

    verify(task).fail(false);
  }

  @Test
  void keepsFailingTasksWhenOneCannotBeFailed() {
    Task broken = mock(Task.class);
    doThrow(new IllegalStateException("store down"))
        .when(broken)
        .updateStatus(anyString(), anyString());
    Task other = mock(Task.class);
    when(taskRepository.listByThisInstance()).thenReturn(List.of(broken, other));

    controller.destroy();

    verify(broken, never()).fail(false);
    verify(other).fail(false);
  }

  @Test
  void leavesNothingToFailWhenNoTasksAreRunning() {
    when(taskRepository.listByThisInstance()).thenReturn(List.of());

    controller.destroy();

    verify(taskRepository).listByThisInstance();
  }

  @Test
  void closingTheSpringContextFailsTasksStillRunningOnThisInstance() {
    InMemoryTaskRepository repository = new InMemoryTaskRepository();
    Task task = repository.create("TEST", "running");

    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.registerBean(TaskRepository.class, () -> repository);
      context.registerBean(OperationsService.class, () -> mock(OperationsService.class));
      context.registerBean(OrchestrationProcessor.class, () -> mock(OrchestrationProcessor.class));
      context.registerBean(
          OperationsController.class,
          () ->
              new OperationsController(
                  mock(OperationsService.class),
                  mock(OrchestrationProcessor.class),
                  repository,
                  0));
      context.refresh();
      assertTrue(!task.getStatus().isCompleted());
    }

    assertTrue(task.getStatus().isFailed());
  }
}
