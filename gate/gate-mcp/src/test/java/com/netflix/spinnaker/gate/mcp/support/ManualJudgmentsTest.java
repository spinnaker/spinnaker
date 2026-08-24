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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ManualJudgmentsTest {

  @Test
  void findsOnlyRunningManualJudgmentStages() {
    Map<String, Object> pendingContext =
        Map.of(
            "instructions",
            "Approve prod deploy?",
            "judgmentInputs",
            List.of(Map.of("value", "yes")));
    Map<String, Object> pendingStage = new LinkedHashMap<>();
    pendingStage.put("type", "manualJudgment");
    pendingStage.put("status", "RUNNING");
    pendingStage.put("id", "stage-1");
    pendingStage.put("name", "Approve?");
    pendingStage.put("context", pendingContext);

    Map<String, Object> completedJudgmentStage = new LinkedHashMap<>();
    completedJudgmentStage.put("type", "manualJudgment");
    completedJudgmentStage.put("status", "SUCCEEDED");
    completedJudgmentStage.put("id", "stage-2");

    Map<String, Object> unrelatedRunningStage = new LinkedHashMap<>();
    unrelatedRunningStage.put("type", "deploy");
    unrelatedRunningStage.put("status", "RUNNING");
    unrelatedRunningStage.put("id", "stage-3");

    Map<String, Object> execution = new LinkedHashMap<>();
    execution.put("id", "exec-1");
    execution.put("name", "deploy-prod");
    execution.put("application", "myapp");
    execution.put("stages", List.of(pendingStage, completedJudgmentStage, unrelatedRunningStage));

    List<Map<String, Object>> pending = ManualJudgments.findPending(execution);

    assertThat(pending).hasSize(1);
    Map<String, Object> summary = pending.get(0);
    assertThat(summary.get("executionId")).isEqualTo("exec-1");
    assertThat(summary.get("pipelineName")).isEqualTo("deploy-prod");
    assertThat(summary.get("application")).isEqualTo("myapp");
    assertThat(summary.get("stageId")).isEqualTo("stage-1");
    assertThat(summary.get("stageName")).isEqualTo("Approve?");
    assertThat(summary.get("instructions")).isEqualTo("Approve prod deploy?");
  }

  @Test
  void handlesMissingOrMalformedStages() {
    assertThat(ManualJudgments.findPending(null)).isEmpty();
    assertThat(ManualJudgments.findPending(Map.of("id", "exec-1"))).isEmpty();
    assertThat(ManualJudgments.findPendingAcross(null)).isEmpty();
  }

  @Test
  void findPendingAcrossFlattensMultipleExecutions() {
    Map<String, Object> stage = new LinkedHashMap<>();
    stage.put("type", "manualJudgment");
    stage.put("status", "RUNNING");
    stage.put("id", "stage-1");

    Map<String, Object> execution1 = Map.of("id", "exec-1", "stages", List.of(stage));
    Map<String, Object> execution2 = Map.of("id", "exec-2", "stages", List.of(stage));

    assertThat(ManualJudgments.findPendingAcross(List.of(execution1, execution2))).hasSize(2);
  }
}
