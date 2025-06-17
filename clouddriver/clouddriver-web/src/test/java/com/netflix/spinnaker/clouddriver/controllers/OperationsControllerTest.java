/*
 * Copyright 2025 Wise, PLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.data.task.DefaultTask;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.OperationsService;
import com.netflix.spinnaker.clouddriver.orchestration.OrchestrationProcessor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OperationsControllerTest {

  private OperationsService operationsService;
  private OrchestrationProcessor orchestrationProcessor;
  private TaskRepository taskRepository;

  private OperationsController operationsController;

  @BeforeEach
  void setup() {
    operationsService = mock(OperationsService.class);
    orchestrationProcessor = mock(OrchestrationProcessor.class);
    taskRepository = mock(TaskRepository.class);
    operationsController =
        new OperationsController(operationsService, orchestrationProcessor, taskRepository, 5);
  }

  @Test
  void test_restartCloudProviderTask_restartsTask() {
    // given
    String cloudProvider = "kubernetes";
    String taskId = "12345";

    Task task = new DefaultTask("task");
    task.fail(true);
    when(taskRepository.get(taskId)).thenReturn(task);
    when(orchestrationProcessor.process(eq(cloudProvider), any(), any())).thenReturn(task);

    List<Map<String, Map>> requestBody = List.of(Map.of());

    // when
    operationsController.restartCloudProviderTask(cloudProvider, taskId, requestBody);

    // then
    verify(operationsService).collectAtomicOperations(eq(cloudProvider), any());
    verify(orchestrationProcessor).process(eq(cloudProvider), any(), any());
  }
}
