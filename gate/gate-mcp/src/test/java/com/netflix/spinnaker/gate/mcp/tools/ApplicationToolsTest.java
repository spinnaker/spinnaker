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

package com.netflix.spinnaker.gate.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.gate.mcp.config.McpServerProperties;
import com.netflix.spinnaker.gate.mcp.support.McpAccessGuard;
import com.netflix.spinnaker.gate.mcp.support.McpReadOnlyModeException;
import com.netflix.spinnaker.gate.mcp.support.OrchestrationJobs;
import com.netflix.spinnaker.gate.services.TaskService;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
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
class ApplicationToolsTest {

  @Mock private Front50Service front50Service;
  @Mock private TaskService taskService;

  private ApplicationTools applicationTools;
  private McpServerProperties properties;

  @BeforeEach
  void setUp() {
    properties = new McpServerProperties();
    properties.setReadOnly(false);
    OrchestrationJobs orchestrationJobs =
        new OrchestrationJobs(taskService, new McpAccessGuard(properties));
    applicationTools = new ApplicationTools(front50Service, orchestrationJobs);
  }

  @Test
  void createApplicationSubmitsCreateApplicationJobWithAttributes() {
    Map<String, Object> taskResult1 = Map.of("id", "task-1", "status", "SUCCEEDED");
    doReturn(taskResult1).when(taskService).createAndWaitForCompletion(anyMap());

    applicationTools.createApplication(
        "myapp", "owner@example.com", "a test app", "aws,kubernetes");

    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(taskService).createAndWaitForCompletion(captor.capture());
    Map<String, Object> operation = captor.getValue();
    assertThat(operation.get("application")).isEqualTo("myapp");

    List<Map<String, Object>> jobs = (List<Map<String, Object>>) operation.get("job");
    assertThat(jobs).hasSize(1);
    Map<String, Object> job = jobs.get(0);
    assertThat(job.get("type")).isEqualTo("createApplication");

    Map<String, Object> applicationAttrs = (Map<String, Object>) job.get("application");
    assertThat(applicationAttrs)
        .containsEntry("name", "myapp")
        .containsEntry("email", "owner@example.com")
        .containsEntry("description", "a test app")
        .containsEntry("cloudProviders", "aws,kubernetes");
  }

  @Test
  void deleteApplicationSubmitsDeleteApplicationJob() {
    Map<String, Object> taskResult2 = Map.of("id", "task-2", "status", "SUCCEEDED");
    doReturn(taskResult2).when(taskService).createAndWaitForCompletion(anyMap());

    applicationTools.deleteApplication("myapp");

    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(taskService).createAndWaitForCompletion(captor.capture());
    List<Map<String, Object>> jobs = (List<Map<String, Object>>) captor.getValue().get("job");
    assertThat(jobs.get(0).get("type")).isEqualTo("deleteApplication");
    assertThat((Map<String, Object>) jobs.get(0).get("application")).containsEntry("name", "myapp");
  }

  @Test
  void createApplicationRejectedInReadOnlyMode() {
    properties.setReadOnly(true);

    assertThatThrownBy(
            () -> applicationTools.createApplication("myapp", "owner@example.com", null, null))
        .isInstanceOf(McpReadOnlyModeException.class);
  }

  @Test
  void listApplicationsFiltersByOwner() {
    when(front50Service.getAllApplicationsUnrestricted())
        .thenReturn(
            Calls.response(
                List.of(
                    Map.of("name", "app-a", "email", "owner-a@example.com"),
                    Map.of("name", "app-b", "email", "owner-b@example.com"))));

    List<Map<String, Object>> result = applicationTools.listApplications("owner-a@example.com");

    assertThat(result).hasSize(1);
    assertThat(result.get(0)).containsEntry("name", "app-a");
  }
}
