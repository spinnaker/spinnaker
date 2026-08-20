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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.gate.mcp.config.McpServerProperties;
import com.netflix.spinnaker.gate.mcp.support.McpAccessGuard;
import com.netflix.spinnaker.gate.mcp.support.McpReadOnlyModeException;
import com.netflix.spinnaker.gate.mcp.support.OrchestrationJobs;
import com.netflix.spinnaker.gate.services.TaskService;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.LinkedHashMap;
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
class PipelineConfigToolsTest {

  @Mock private Front50Service front50Service;
  @Mock private TaskService taskService;

  private PipelineConfigTools pipelineConfigTools;
  private McpServerProperties properties;

  @BeforeEach
  void setUp() {
    properties = new McpServerProperties();
    properties.setReadOnly(false);
    OrchestrationJobs orchestrationJobs =
        new OrchestrationJobs(taskService, new McpAccessGuard(properties));
    pipelineConfigTools = new PipelineConfigTools(front50Service, orchestrationJobs);
  }

  @Test
  void savePipelineConfigSubmitsSavePipelineJob() {
    Map<String, Object> taskResult = Map.of("id", "task-1", "status", "SUCCEEDED");
    doReturn(taskResult).when(taskService).createAndWaitForCompletion(anyMap());

    Map<String, Object> pipeline = new LinkedHashMap<>();
    pipeline.put("name", "my-pipeline");
    pipeline.put("application", "myapp");

    pipelineConfigTools.savePipelineConfig(pipeline, null);

    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(taskService).createAndWaitForCompletion(captor.capture());
    Map<String, Object> operation = captor.getValue();
    assertThat(operation.get("application")).isEqualTo("myapp");

    List<Map<String, Object>> jobs = (List<Map<String, Object>>) operation.get("job");
    Map<String, Object> job = jobs.get(0);
    assertThat(job.get("type")).isEqualTo("savePipeline");
    assertThat(job.get("pipeline")).isEqualTo(pipeline);
    assertThat(job.get("staleCheck")).isEqualTo(false);
  }

  @Test
  void savePipelineConfigRejectedInReadOnlyMode() {
    properties.setReadOnly(true);
    Map<String, Object> pipeline = Map.of("name", "p", "application", "myapp");

    assertThatThrownBy(() -> pipelineConfigTools.savePipelineConfig(pipeline, null))
        .isInstanceOf(McpReadOnlyModeException.class);
  }

  @Test
  void deletePipelineConfigFindsByNameAndSubmitsDeletePipelineJob() {
    Map<String, Object> existingPipeline = new LinkedHashMap<>();
    existingPipeline.put("id", "abc-123");
    existingPipeline.put("name", "My Pipeline");
    existingPipeline.put("application", "myapp");

    when(front50Service.getPipelineConfigsForApplication("myapp", null, null, true))
        .thenReturn(Calls.response(List.of(existingPipeline)));

    Map<String, Object> taskResult = Map.of("id", "task-2", "status", "SUCCEEDED");
    doReturn(taskResult).when(taskService).createAndWaitForCompletion(anyMap());

    pipelineConfigTools.deletePipelineConfig("myapp", "my pipeline");

    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(taskService).createAndWaitForCompletion(captor.capture());
    List<Map<String, Object>> jobs = (List<Map<String, Object>>) captor.getValue().get("job");
    Map<String, Object> job = jobs.get(0);
    assertThat(job.get("type")).isEqualTo("deletePipeline");
    assertThat(job.get("pipeline")).isEqualTo(existingPipeline);
  }

  @Test
  void deletePipelineConfigThrowsWhenPipelineNotFound() {
    when(front50Service.getPipelineConfigsForApplication("myapp", null, null, true))
        .thenReturn(Calls.response(List.of()));

    assertThatThrownBy(() -> pipelineConfigTools.deletePipelineConfig("myapp", "missing"))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void deletePipelineConfigRejectedInReadOnlyMode() {
    properties.setReadOnly(true);

    assertThatThrownBy(() -> pipelineConfigTools.deletePipelineConfig("myapp", "my-pipeline"))
        .isInstanceOf(McpReadOnlyModeException.class);
  }
}
