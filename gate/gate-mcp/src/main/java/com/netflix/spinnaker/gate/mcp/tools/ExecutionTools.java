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

import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;

/**
 * MCP tools for finding and inspecting pipeline executions - trigger state, ad-hoc lookups by
 * config id, and failure triage. Mirrors {@code ExecutionsController} in gate-web, reimplemented
 * directly against {@link OrcaServiceSelector} (a gate-core bean) since gate-mcp cannot depend on
 * gate-web.
 */
public class ExecutionTools {

  private final OrcaServiceSelector orcaServiceSelector;

  public ExecutionTools(OrcaServiceSelector orcaServiceSelector) {
    this.orcaServiceSelector = orcaServiceSelector;
  }

  @McpTool(
      name = "search_executions",
      description =
          "Search for pipeline executions by application and trigger criteria (trigger type, event id, trigger field match, "
              + "time range, status). Results are sorted newest-first by trigger time. Use this to answer questions like "
              + "'what deployments ran for this application in the last day' or 'find the execution triggered by build #123'.")
  public List<Map<String, Object>> searchExecutions(
      @McpToolParam(
              description =
                  "Application to search within, or '*' to search across all applications",
              required = true)
          String application,
      @McpToolParam(
              description =
                  "Comma-delimited list of trigger types to filter by, e.g. 'jenkins,webhook'",
              required = false)
          String triggerTypes,
      @McpToolParam(
              description = "Only include executions with this pipeline name",
              required = false)
          String pipelineName,
      @McpToolParam(
              description = "Only include executions triggered by this event id",
              required = false)
          String eventId,
      @McpToolParam(
              description =
                  "Comma-delimited list of execution statuses to filter by, e.g. 'RUNNING,SUCCEEDED'",
              required = false)
          String statuses,
      @McpToolParam(
              description =
                  "Only include executions triggered at or after this Unix timestamp (ms, UTC)",
              required = false)
          Long triggerTimeStartBoundary,
      @McpToolParam(
              description =
                  "Only include executions triggered at or before this Unix timestamp (ms, UTC)",
              required = false)
          Long triggerTimeEndBoundary,
      @McpToolParam(description = "Maximum number of executions to return", required = false)
          Integer size) {
    return Retrofit2SyncCall.execute(
        orcaServiceSelector
            .select()
            .searchForPipelineExecutionsByTrigger(
                application,
                triggerTypes,
                pipelineName,
                eventId,
                null,
                triggerTimeStartBoundary == null ? 0L : triggerTimeStartBoundary,
                triggerTimeEndBoundary == null ? Long.MAX_VALUE : triggerTimeEndBoundary,
                statuses,
                0,
                size == null ? 10 : size,
                false,
                false));
  }

  @McpTool(
      name = "get_latest_executions",
      description =
          "Fetch the most recent execution(s) for one or more pipeline config ids, or fetch specific executions by id.")
  public List<Map<String, Object>> getLatestExecutions(
      @McpToolParam(
              description =
                  "Comma-separated pipeline config ids. Mutually exclusive with executionIds.",
              required = false)
          String pipelineConfigIds,
      @McpToolParam(
              description =
                  "Comma-separated execution ids. Mutually exclusive with pipelineConfigIds.",
              required = false)
          String executionIds,
      @McpToolParam(
              description =
                  "Number of executions to return per pipeline config (ignored if executionIds is set)",
              required = false)
          Integer limit,
      @McpToolParam(
              description = "Comma-delimited list of execution statuses to filter by",
              required = false)
          String statuses) {
    boolean noIds =
        (executionIds == null || executionIds.isBlank())
            && (pipelineConfigIds == null || pipelineConfigIds.isBlank());
    if (noIds) {
      return Collections.emptyList();
    }
    return Retrofit2SyncCall.execute(
        orcaServiceSelector
            .select()
            .getSubsetOfExecutions(pipelineConfigIds, executionIds, limit, statuses, true));
  }

  @McpTool(
      name = "get_failed_stages",
      description =
          "Retrieve the stages in a failed/terminal state for a pipeline execution, traversing into nested pipeline stages.")
  public List<Object> getFailedStages(
      @McpToolParam(description = "Pipeline execution id", required = true) String executionId,
      @McpToolParam(
              description = "Maximum number of nested pipeline executions to descend into",
              required = false)
          Integer limit) {
    return Retrofit2SyncCall.execute(
        orcaServiceSelector
            .select()
            .getFailedStagesForPipelineExecution(executionId, null, limit == null ? 1 : limit));
  }
}
