/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.mcp.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.netflix.spinnaker.gate.mcp.config.McpServerProperties;
import com.netflix.spinnaker.gate.services.TaskService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrchestrationJobsTest {

  @Mock private TaskService taskService;

  private OrchestrationJobs orchestrationJobs;

  @BeforeEach
  void setUp() {
    McpServerProperties properties = new McpServerProperties();
    properties.setReadOnly(false);
    orchestrationJobs = new OrchestrationJobs(taskService, new McpAccessGuard(properties));
  }

  @Test
  void submitsOperationAndReturnsResultOnSuccess() {
    Map<String, Object> job = Map.of("type", "createApplication");
    Map<String, Object> taskResult = Map.of("id", "task-1", "status", "SUCCEEDED");
    doReturn(taskResult).when(taskService).createAndWaitForCompletion(anyMap());

    Map<String, Object> result =
        orchestrationJobs.submit("myapp", "Create application 'myapp'", List.of(job));

    assertThat(result).containsEntry("status", "SUCCEEDED");

    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(taskService).createAndWaitForCompletion(captor.capture());
    Map<String, Object> operation = captor.getValue();
    assertThat(operation.get("application")).isEqualTo("myapp");
    assertThat(operation.get("description")).isEqualTo("Create application 'myapp'");
    assertThat(operation.get("job")).isEqualTo(List.of(job));
  }

  @Test
  void throwsWithExtractedDetailWhenTaskFails() {
    Map<String, Object> errorDetails = Map.of("errors", List.of("name is required"));
    Map<String, Object> exceptionValue = Map.of("details", errorDetails);
    Map<String, Object> variable = Map.of("key", "exception", "value", exceptionValue);
    Map<String, Object> taskResult =
        Map.of("id", "task-2", "status", "TERMINAL", "variables", List.of(variable));
    doReturn(taskResult).when(taskService).createAndWaitForCompletion(anyMap());

    assertThatThrownBy(
            () -> orchestrationJobs.submit("myapp", "Create application 'myapp'", List.of()))
        .isInstanceOf(OrchestrationFailedException.class)
        .hasMessageContaining("task-2")
        .hasMessageContaining("TERMINAL")
        .hasMessageContaining("name is required");
  }

  @Test
  void readOnlyModeRejectsBeforeSubmitting() {
    McpServerProperties properties = new McpServerProperties();
    properties.setReadOnly(true);
    OrchestrationJobs readOnlyJobs =
        new OrchestrationJobs(taskService, new McpAccessGuard(properties));

    assertThatThrownBy(() -> readOnlyJobs.requireWriteAccess("create_application"))
        .isInstanceOf(McpReadOnlyModeException.class);
  }
}
