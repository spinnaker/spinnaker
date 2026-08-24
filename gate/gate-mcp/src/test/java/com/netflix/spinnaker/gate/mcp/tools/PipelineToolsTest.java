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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.gate.mcp.config.McpServerProperties;
import com.netflix.spinnaker.gate.mcp.support.McpAccessGuard;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.gate.services.internal.OrcaService;
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.mock.Calls;

@ExtendWith(MockitoExtension.class)
class PipelineToolsTest {

  @Mock private OrcaServiceSelector orcaServiceSelector;
  @Mock private OrcaService orcaService;
  @Mock private Front50Service front50Service;

  private PipelineTools pipelineTools;

  @BeforeEach
  void setUp() {
    McpServerProperties properties = new McpServerProperties();
    properties.setReadOnly(false);
    pipelineTools =
        new PipelineTools(orcaServiceSelector, front50Service, new McpAccessGuard(properties));
    org.mockito.Mockito.lenient().when(orcaServiceSelector.select()).thenReturn(orcaService);
  }

  @Test
  void evaluatePipelineExpressionUsesExplicitExecutionId() {
    when(orcaService.evaluateExpressionForExecution("exec-1", "${1+1}"))
        .thenReturn(Calls.response(Map.of("result", "2")));

    Map<String, Object> result =
        pipelineTools.evaluatePipelineExpression("${1+1}", "exec-1", null, null, null);

    assertThat(result).containsEntry("result", "2");
  }

  @Test
  void evaluatePipelineExpressionWithStageIdUsesStageEvaluation() {
    when(orcaService.evaluateExpressionForExecutionAtStage("exec-1", "stage-1", "${1+1}"))
        .thenReturn(Calls.response(Map.of("result", "2")));

    pipelineTools.evaluatePipelineExpression("${1+1}", "exec-1", "stage-1", null, null);

    verify(orcaService).evaluateExpressionForExecutionAtStage("exec-1", "stage-1", "${1+1}");
  }

  @Test
  void evaluatePipelineExpressionFindsMostRecentExecutionWhenExecutionIdOmitted() {
    Map<String, Object> older = Map.of("id", "exec-old", "startTime", 1000L);
    Map<String, Object> newer = Map.of("id", "exec-new", "startTime", 2000L);
    when(orcaService.getPipelines(eq("myapp"), eq(1), isNull(), eq(false), isNull(), isNull()))
        .thenReturn(Calls.response(List.of(older, newer)));
    when(orcaService.evaluateExpressionForExecution(eq("exec-new"), any()))
        .thenReturn(Calls.response(Map.of("result", "ok")));

    pipelineTools.evaluatePipelineExpression("${foo}", null, null, "myapp", null);

    verify(orcaService).evaluateExpressionForExecution("exec-new", "${foo}");
  }

  @Test
  void evaluatePipelineExpressionPassesPipelineNameFilterThrough() {
    when(orcaService.getPipelines(
            eq("myapp"), eq(1), isNull(), eq(false), eq("my-pipeline"), isNull()))
        .thenReturn(Calls.response(List.of(Map.of("id", "exec-1", "startTime", 1000L))));
    when(orcaService.evaluateExpressionForExecution(eq("exec-1"), any()))
        .thenReturn(Calls.response(Map.of("result", "ok")));

    pipelineTools.evaluatePipelineExpression("${foo}", null, null, "myapp", "my-pipeline");

    verify(orcaService)
        .getPipelines(eq("myapp"), eq(1), isNull(), eq(false), eq("my-pipeline"), isNull());
  }

  @Test
  void evaluatePipelineExpressionThrowsWhenNoExecutionIdAndNoApplication() {
    assertThatThrownBy(
            () -> pipelineTools.evaluatePipelineExpression("${foo}", null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void evaluatePipelineExpressionThrowsWhenApplicationHasNoExecutions() {
    when(orcaService.getPipelines(eq("myapp"), eq(1), isNull(), eq(false), isNull(), isNull()))
        .thenReturn(Calls.response(List.of()));

    assertThatThrownBy(
            () -> pipelineTools.evaluatePipelineExpression("${foo}", null, null, "myapp", null))
        .isInstanceOf(NotFoundException.class);
  }
}
