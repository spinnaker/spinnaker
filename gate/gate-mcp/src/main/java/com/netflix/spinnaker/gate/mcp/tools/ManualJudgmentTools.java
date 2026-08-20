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

import com.netflix.spinnaker.gate.mcp.support.ManualJudgments;
import com.netflix.spinnaker.gate.mcp.support.McpAccessGuard;
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;

/**
 * MCP tools for finding and resolving pipelines that are paused on a "manual judgment" stage. There
 * is no dedicated Gate endpoint for this - {@code list_pending_manual_judgments} and {@code
 * get_manual_judgment} derive it from the same stage list Deck renders (see {@link
 * ManualJudgments}), and {@code judge_pipeline_stage} submits the exact stage context ({@code
 * judgmentStatus}/{@code judgmentInput}) that {@code ManualJudgmentStage} in orca-echo reads.
 */
public class ManualJudgmentTools {

  private final OrcaServiceSelector orcaServiceSelector;
  private final McpAccessGuard accessGuard;

  public ManualJudgmentTools(OrcaServiceSelector orcaServiceSelector, McpAccessGuard accessGuard) {
    this.orcaServiceSelector = orcaServiceSelector;
    this.accessGuard = accessGuard;
  }

  @McpTool(
      name = "list_pending_manual_judgments",
      description =
          "List all pipeline executions for an application that are currently paused waiting on a manual judgment stage.")
  public List<Map<String, Object>> listPendingManualJudgments(
      @McpToolParam(description = "Application name", required = true) String application) {
    List<Map<String, Object>> runningExecutions =
        Retrofit2SyncCall.execute(
            orcaServiceSelector
                .select()
                .getPipelines(application, 100, "RUNNING", true, null, null));
    return ManualJudgments.findPendingAcross(runningExecutions);
  }

  @McpTool(
      name = "get_manual_judgment",
      description =
          "Get the pending manual judgment stage(s), if any, for a specific pipeline execution.")
  public List<Map<String, Object>> getManualJudgment(
      @McpToolParam(description = "Pipeline execution id", required = true) String executionId) {
    Map<String, Object> execution =
        Retrofit2SyncCall.execute(orcaServiceSelector.select().getPipeline(executionId));
    return ManualJudgments.findPending(execution);
  }

  @McpTool(
      name = "judge_pipeline_stage",
      description =
          "Resolve a manual judgment stage: continue or stop the pipeline, optionally supplying one of the stage's "
              + "configured judgment input options.")
  public Map<String, Object> judgePipelineStage(
      @McpToolParam(description = "Pipeline execution id", required = true) String executionId,
      @McpToolParam(
              description =
                  "The manual judgment stage's id (see get_manual_judgment/list_pending_manual_judgments)",
              required = true)
          String stageId,
      @McpToolParam(
              description =
                  "'continue' or 'stop', or a custom value matching one of the stage's judgmentInputs",
              required = true)
          String judgmentStatus,
      @McpToolParam(
              description = "Optional free-text judgment input, if the stage collects one",
              required = false)
          String judgmentInput) {
    accessGuard.requireWriteAccess("judge_pipeline_stage");

    Map<String, Object> context = new LinkedHashMap<>();
    context.put("judgmentStatus", judgmentStatus);
    if (judgmentInput != null) {
      context.put("judgmentInput", judgmentInput);
    }
    return Retrofit2SyncCall.execute(
        orcaServiceSelector.select().updatePipelineStage(executionId, stageId, context));
  }
}
