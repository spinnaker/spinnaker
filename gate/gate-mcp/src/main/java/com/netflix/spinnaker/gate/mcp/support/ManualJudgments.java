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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * There is no dedicated Orca/Gate endpoint for "pending manual judgments" - Gate only exposes
 * generic execution/stage reads. This derives the list by scanning an execution's stages for {@code
 * type == "manualJudgment" && status == "RUNNING"}, matching the stage context fields
 * (`judgmentStatus`, `judgmentInput`, `instructions`, `judgmentInputs`) that {@code
 * ManualJudgmentStage} in orca-echo reads/writes.
 */
public final class ManualJudgments {

  private ManualJudgments() {}

  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> findPending(Map<String, Object> execution) {
    List<Map<String, Object>> pending = new ArrayList<>();
    if (execution == null) {
      return pending;
    }
    Object stagesObj = execution.get("stages");
    if (!(stagesObj instanceof List)) {
      return pending;
    }
    for (Object stageObj : (List<Object>) stagesObj) {
      if (!(stageObj instanceof Map)) {
        continue;
      }
      Map<String, Object> stage = (Map<String, Object>) stageObj;
      if (!"manualJudgment".equals(stage.get("type")) || !"RUNNING".equals(stage.get("status"))) {
        continue;
      }
      Map<String, Object> context =
          stage.get("context") instanceof Map
              ? (Map<String, Object>) stage.get("context")
              : Map.of();

      Map<String, Object> summary = new LinkedHashMap<>();
      summary.put("executionId", execution.get("id"));
      summary.put("pipelineName", execution.get("name"));
      summary.put("application", execution.get("application"));
      summary.put("stageId", stage.get("id"));
      summary.put("stageName", stage.get("name"));
      summary.put("instructions", context.get("instructions"));
      summary.put("judgmentInputs", context.get("judgmentInputs"));
      pending.add(summary);
    }
    return pending;
  }

  public static List<Map<String, Object>> findPendingAcross(List<Map<String, Object>> executions) {
    List<Map<String, Object>> pending = new ArrayList<>();
    if (executions == null) {
      return pending;
    }
    for (Map<String, Object> execution : executions) {
      pending.addAll(findPending(execution));
    }
    return pending;
  }
}
