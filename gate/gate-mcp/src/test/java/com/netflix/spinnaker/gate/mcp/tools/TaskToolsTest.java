/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.spinnaker.gate.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.gate.mcp.config.McpServerProperties;
import com.netflix.spinnaker.gate.mcp.support.McpAccessGuard;
import com.netflix.spinnaker.gate.mcp.support.McpReadOnlyModeException;
import com.netflix.spinnaker.gate.services.TaskService;
import com.netflix.spinnaker.gate.services.internal.OrcaService;
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.mock.Calls;

@ExtendWith(MockitoExtension.class)
class TaskToolsTest {

  @Mock private TaskService taskService;
  @Mock private OrcaServiceSelector orcaServiceSelector;
  @Mock private OrcaService orcaService;

  private TaskTools taskTools;
  private McpServerProperties properties;

  @BeforeEach
  void setUp() {
    properties = new McpServerProperties();
    properties.setReadOnly(false);
    taskTools = new TaskTools(taskService, orcaServiceSelector, new McpAccessGuard(properties));
  }

  @Test
  void createTaskSubmitsJobAndReturnsRefWithoutWaiting() {
    Map<String, Object> job = Map.of("type", "createServerGroup");
    when(taskService.createAppTask(eq("myapp"), anyMap())).thenReturn(Map.of("ref", "/tasks/123"));

    Map<String, String> result = taskTools.createTask("myapp", "Deploy", List.of(job));

    assertThat(result).containsEntry("ref", "/tasks/123");

    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(taskService).createAppTask(eq("myapp"), captor.capture());
    assertThat(captor.getValue().get("job")).isEqualTo(List.of(job));
  }

  @Test
  void createTaskRejectedInReadOnlyMode() {
    properties.setReadOnly(true);

    assertThatThrownBy(() -> taskTools.createTask("myapp", "Deploy", List.of()))
        .isInstanceOf(McpReadOnlyModeException.class);
  }

  @Test
  void cancelTaskRejectedInReadOnlyMode() {
    properties.setReadOnly(true);

    assertThatThrownBy(() -> taskTools.cancelTask("task-1"))
        .isInstanceOf(McpReadOnlyModeException.class);
  }

  @Test
  void searchApplicationTasksDelegatesToOrca() {
    when(orcaServiceSelector.select()).thenReturn(orcaService);
    when(orcaService.getTasks("myapp", 2, 10, "RUNNING"))
        .thenReturn(Calls.response(List.of(Map.of("id", "task-1"))));

    List<Map<String, Object>> result = taskTools.searchApplicationTasks("myapp", "RUNNING", 2, 10);

    assertThat(result).hasSize(1);
    assertThat(result.get(0)).containsEntry("id", "task-1");
  }

  @Test
  void getTaskDelegatesToTaskService() {
    when(taskService.getTask("task-1")).thenReturn(Map.of("id", "task-1", "status", "RUNNING"));

    Map<String, Object> result = taskTools.getTask("task-1");

    assertThat(result).containsEntry("status", "RUNNING");
  }
}
