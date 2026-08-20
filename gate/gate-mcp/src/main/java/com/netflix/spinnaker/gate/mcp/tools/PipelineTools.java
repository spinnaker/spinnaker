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

import com.netflix.spinnaker.gate.mcp.support.McpAccessGuard;
import com.netflix.spinnaker.gate.mcp.support.PipelineConfigs;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;

/**
 * MCP tools for triggering and controlling pipeline *executions* - see {@link PipelineConfigTools}
 * for CRUD on pipeline *definitions*. {@code trigger_pipeline} resolves the pipeline config from
 * front50 and starts it via Orca's orchestrate endpoint, mirroring {@code PipelineService.trigger}
 * /{@code PipelineController.invokePipelineConfig} in gate-web (reimplemented here since gate-mcp,
 * a dependency of gate-web, cannot depend on gate-web).
 */
public class PipelineTools {

  private final OrcaServiceSelector orcaServiceSelector;
  private final Front50Service front50Service;
  private final McpAccessGuard accessGuard;

  public PipelineTools(
      OrcaServiceSelector orcaServiceSelector,
      Front50Service front50Service,
      McpAccessGuard accessGuard) {
    this.orcaServiceSelector = orcaServiceSelector;
    this.front50Service = front50Service;
    this.accessGuard = accessGuard;
  }

  @McpTool(
      name = "trigger_pipeline",
      description =
          "Trigger (start) a pipeline execution by pipeline name or config id, optionally supplying pipeline parameters.")
  public Map<String, Object> triggerPipeline(
      @McpToolParam(description = "Application name", required = true) String application,
      @McpToolParam(description = "Pipeline name or config id", required = true)
          String pipelineNameOrId,
      @McpToolParam(
              description =
                  "Pipeline parameters to pass through, matching the pipeline's configured parameter names",
              required = false)
          Map<String, Object> parameters) {
    accessGuard.requireWriteAccess("trigger_pipeline");

    String user = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous");
    Map<String, Object> pipelineConfig =
        PipelineConfigs.resolve(front50Service, application, pipelineNameOrId);

    Map<String, Object> trigger = new LinkedHashMap<>();
    trigger.put("user", user);
    trigger.put("notifications", List.of());
    if (parameters != null) {
      trigger.put("parameters", parameters);
    }
    pipelineConfig.put("trigger", trigger);

    return Retrofit2SyncCall.execute(
        orcaServiceSelector.select().startPipeline(pipelineConfig, user));
  }

  @McpTool(name = "get_pipeline_execution", description = "Retrieve a pipeline execution by id.")
  public Map<String, Object> getPipelineExecution(
      @McpToolParam(description = "Pipeline execution id", required = true) String executionId) {
    return Retrofit2SyncCall.execute(orcaServiceSelector.select().getPipeline(executionId));
  }

  @McpTool(name = "cancel_pipeline", description = "Cancel a running pipeline execution.")
  public void cancelPipeline(
      @McpToolParam(description = "Pipeline execution id", required = true) String executionId,
      @McpToolParam(description = "Optional reason for cancellation", required = false)
          String reason,
      @McpToolParam(
              description = "Force cancellation even if the execution can't be cleanly stopped",
              required = false)
          Boolean force) {
    accessGuard.requireWriteAccess("cancel_pipeline");
    Retrofit2SyncCall.execute(
        orcaServiceSelector
            .select()
            .cancelPipeline(executionId, reason, force != null && force, ""));
  }

  @McpTool(name = "pause_pipeline", description = "Pause a running pipeline execution.")
  public void pausePipeline(
      @McpToolParam(description = "Pipeline execution id", required = true) String executionId) {
    accessGuard.requireWriteAccess("pause_pipeline");
    Retrofit2SyncCall.execute(orcaServiceSelector.select().pausePipeline(executionId, ""));
  }

  @McpTool(name = "resume_pipeline", description = "Resume a paused pipeline execution.")
  public void resumePipeline(
      @McpToolParam(description = "Pipeline execution id", required = true) String executionId) {
    accessGuard.requireWriteAccess("resume_pipeline");
    Retrofit2SyncCall.execute(orcaServiceSelector.select().resumePipeline(executionId, ""));
  }
}
