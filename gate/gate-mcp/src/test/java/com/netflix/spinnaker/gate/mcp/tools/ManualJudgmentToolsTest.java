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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.gate.mcp.config.McpServerProperties;
import com.netflix.spinnaker.gate.mcp.support.McpAccessGuard;
import com.netflix.spinnaker.gate.mcp.support.McpReadOnlyModeException;
import com.netflix.spinnaker.gate.services.internal.OrcaService;
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
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
class ManualJudgmentToolsTest {

  @Mock private OrcaServiceSelector orcaServiceSelector;
  @Mock private OrcaService orcaService;

  private ManualJudgmentTools manualJudgmentTools;
  private McpServerProperties properties;

  @BeforeEach
  void setUp() {
    properties = new McpServerProperties();
    properties.setReadOnly(false);
    manualJudgmentTools =
        new ManualJudgmentTools(orcaServiceSelector, new McpAccessGuard(properties));
  }

  @Test
  void judgePipelineStageSendsJudgmentStatusAndInput() {
    when(orcaServiceSelector.select()).thenReturn(orcaService);
    when(orcaService.updatePipelineStage(eq("exec-1"), eq("stage-1"), anyMap()))
        .thenReturn(Calls.response(Map.of("id", "exec-1")));

    manualJudgmentTools.judgePipelineStage("exec-1", "stage-1", "continue", "custom-option");

    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(orcaService).updatePipelineStage(eq("exec-1"), eq("stage-1"), captor.capture());
    assertThat(captor.getValue())
        .containsEntry("judgmentStatus", "continue")
        .containsEntry("judgmentInput", "custom-option");
  }

  @Test
  void judgePipelineStageOmitsJudgmentInputWhenNotProvided() {
    when(orcaServiceSelector.select()).thenReturn(orcaService);
    when(orcaService.updatePipelineStage(eq("exec-1"), eq("stage-1"), anyMap()))
        .thenReturn(Calls.response(Map.of("id", "exec-1")));

    manualJudgmentTools.judgePipelineStage("exec-1", "stage-1", "stop", null);

    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(orcaService).updatePipelineStage(eq("exec-1"), eq("stage-1"), captor.capture());
    assertThat(captor.getValue())
        .containsEntry("judgmentStatus", "stop")
        .doesNotContainKey("judgmentInput");
  }

  @Test
  void judgePipelineStageRejectedInReadOnlyMode() {
    properties.setReadOnly(true);

    assertThatThrownBy(
            () -> manualJudgmentTools.judgePipelineStage("exec-1", "stage-1", "continue", null))
        .isInstanceOf(McpReadOnlyModeException.class);
  }

  @Test
  void listPendingManualJudgmentsFiltersRunningExecutions() {
    Map<String, Object> stage = new LinkedHashMap<>();
    stage.put("type", "manualJudgment");
    stage.put("status", "RUNNING");
    stage.put("id", "stage-1");
    Map<String, Object> execution = new LinkedHashMap<>();
    execution.put("id", "exec-1");
    execution.put("stages", List.of(stage));

    when(orcaServiceSelector.select()).thenReturn(orcaService);
    when(orcaService.getPipelines("myapp", 100, "RUNNING", true, null, null))
        .thenReturn(Calls.response(List.of(execution)));

    List<Map<String, Object>> pending = manualJudgmentTools.listPendingManualJudgments("myapp");

    assertThat(pending).hasSize(1);
    assertThat(pending.get(0)).containsEntry("stageId", "stage-1");
  }

  @Test
  void getManualJudgmentReadsFromSingleExecution() {
    Map<String, Object> stage = new LinkedHashMap<>();
    stage.put("type", "manualJudgment");
    stage.put("status", "RUNNING");
    stage.put("id", "stage-1");
    Map<String, Object> execution = new LinkedHashMap<>();
    execution.put("id", "exec-1");
    execution.put("stages", List.of(stage));

    when(orcaServiceSelector.select()).thenReturn(orcaService);
    when(orcaService.getPipeline("exec-1")).thenReturn(Calls.response(execution));

    List<Map<String, Object>> pending = manualJudgmentTools.getManualJudgment("exec-1");

    assertThat(pending).hasSize(1);
    assertThat(pending.get(0)).containsEntry("executionId", "exec-1");
  }
}
