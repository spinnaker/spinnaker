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

package com.netflix.spinnaker.gate.mcp.prompts;

import org.springaicommunity.mcp.annotation.McpArg;
import org.springaicommunity.mcp.annotation.McpPrompt;

/** Reusable prompt templates that guide an MCP client through common CD workflows. */
public class SpinnakerPrompts {

  @McpPrompt(
      name = "triage-failed-pipeline",
      description =
          "Investigate why a pipeline execution failed and suggest a next action (retry, rollback, escalate).")
  public String triageFailedPipeline(
      @McpArg(
              name = "executionId",
              description = "The failed pipeline execution id",
              required = true)
          String executionId) {
    return "A Spinnaker pipeline execution has failed. Investigate execution '"
        + executionId
        + "':\n"
        + "1. Call get_pipeline_execution to get the execution's overall status, application, and pipeline name.\n"
        + "2. Call get_failed_stages with this executionId to find exactly which stage(s) failed and why "
        + "(descending into any nested pipeline stages).\n"
        + "3. If the failure looks infrastructure-related, use get_server_groups / get_clusters for the "
        + "execution's application to check current deployed state.\n"
        + "4. Summarize the root cause in plain language, and recommend one of: retry the failed stage, "
        + "roll back to the previous server group, or escalate to a human with the specific error detail. "
        + "Do not take a mutating action without explicit confirmation from the user.";
  }

  @McpPrompt(
      name = "review-manual-judgment",
      description =
          "Gather context for a pipeline paused on a manual judgment stage before deciding whether to continue or stop it.")
  public String reviewManualJudgment(
      @McpArg(
              name = "executionId",
              description = "The pipeline execution id paused on a manual judgment",
              required = true)
          String executionId,
      @McpArg(name = "stageId", description = "The manual judgment stage id", required = true)
          String stageId) {
    return "A Spinnaker pipeline execution '"
        + executionId
        + "' is paused waiting on manual judgment stage '"
        + stageId
        + "'. Before recommending continue or stop:\n"
        + "1. Call get_manual_judgment with this executionId to read the judgment's instructions and any "
        + "judgmentInputs options.\n"
        + "2. Call get_pipeline_execution to understand what stages ran before this judgment (what's already "
        + "been deployed) and what would run after it if approved.\n"
        + "3. If earlier stages ran canary analysis or tests, note their results.\n"
        + "4. Present a clear recommendation (continue/stop, and which judgmentInput if applicable) with "
        + "supporting evidence, and only call judge_pipeline_stage after the user explicitly confirms.";
  }
}
