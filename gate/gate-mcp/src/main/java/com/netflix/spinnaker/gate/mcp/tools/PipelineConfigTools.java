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

import com.netflix.spinnaker.gate.mcp.support.OrchestrationJobs;
import com.netflix.spinnaker.gate.mcp.support.PipelineConfigs;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;

/**
 * MCP tools for CRUD on pipeline *definitions* (front50 config), as distinct from {@link
 * PipelineTools} which operates on running/started *executions*.
 *
 * <p>Mutating operations mirror {@code PipelineController} in gate-web exactly: {@code
 * save_pipeline_config}/{@code delete_pipeline_config} submit the same {@code savePipeline}/{@code
 * deletePipeline} Orca job types Deck submits (see {@link OrchestrationJobs}). Reimplemented
 * against {@link Front50Service} directly (a gate-core bean) since gate-mcp, a dependency of
 * gate-web, cannot depend on gate-web's controllers/services.
 */
public class PipelineConfigTools {

  private final Front50Service front50Service;
  private final OrchestrationJobs orchestrationJobs;

  public PipelineConfigTools(Front50Service front50Service, OrchestrationJobs orchestrationJobs) {
    this.front50Service = front50Service;
    this.orchestrationJobs = orchestrationJobs;
  }

  @McpTool(
      name = "list_pipeline_configs",
      description = "List an application's configured pipelines (definitions, not executions).")
  public List<Map<String, Object>> listPipelineConfigs(
      @McpToolParam(description = "Application name", required = true) String application,
      @McpToolParam(
              description = "Filter pipelines whose name contains this string",
              required = false)
          String pipelineNameFilter) {
    return Retrofit2SyncCall.execute(
        front50Service.getPipelineConfigsForApplication(
            application, pipelineNameFilter, null, true));
  }

  @McpTool(
      name = "get_pipeline_config",
      description = "Retrieve a single pipeline definition by name or config id.")
  public Map<String, Object> getPipelineConfig(
      @McpToolParam(description = "Application name", required = true) String application,
      @McpToolParam(description = "Pipeline name or config id", required = true)
          String pipelineNameOrId) {
    return PipelineConfigs.resolve(front50Service, application, pipelineNameOrId);
  }

  @McpTool(
      name = "save_pipeline_config",
      description =
          "Create or update a pipeline definition. Creates a new pipeline if the supplied definition has no 'id' "
              + "field (or none matching an existing pipeline); otherwise updates the existing pipeline with that id "
              + "(to rename a pipeline, fetch it with get_pipeline_config, change its 'name' field, and save it back "
              + "with its existing 'id'). The definition must include at minimum 'name' and 'application'.")
  public Map<String, Object> savePipelineConfig(
      @McpToolParam(
              description =
                  "Full pipeline definition map (name, application, stages, triggers, parameterConfig, ...)",
              required = true)
          Map<String, Object> pipeline,
      @McpToolParam(
              description =
                  "If true, reject the save if the pipeline was modified since it was last read (optimistic locking)",
              required = false)
          Boolean staleCheck) {
    orchestrationJobs.requireWriteAccess("save_pipeline_config");

    Object name = pipeline.get("name");
    Object application = pipeline.get("application");

    Map<String, Object> job = new LinkedHashMap<>();
    job.put("type", "savePipeline");
    job.put("pipeline", pipeline);
    job.put("user", AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"));
    job.put("staleCheck", staleCheck != null && staleCheck);

    return orchestrationJobs.submit(
        String.valueOf(application), "Save pipeline '" + name + "'", List.of(job));
  }

  @McpTool(name = "delete_pipeline_config", description = "Delete a pipeline definition by name.")
  public void deletePipelineConfig(
      @McpToolParam(description = "Application name", required = true) String application,
      @McpToolParam(description = "Pipeline name", required = true) String pipelineName) {
    orchestrationJobs.requireWriteAccess("delete_pipeline_config");

    List<Map<String, Object>> pipelineConfigs =
        Retrofit2SyncCall.execute(
            front50Service.getPipelineConfigsForApplication(application, null, null, true));
    Map<String, Object> pipeline =
        pipelineConfigs.stream()
            .filter(p -> pipelineName.equalsIgnoreCase(String.valueOf(p.get("name"))))
            .findFirst()
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "Pipeline configuration not found (name: "
                            + pipelineName
                            + " in application "
                            + application
                            + ")"));

    Map<String, Object> job = new LinkedHashMap<>();
    job.put("type", "deletePipeline");
    job.put("pipeline", pipeline);
    job.put("user", AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"));

    orchestrationJobs.submit(
        application, "Delete pipeline '" + pipeline.get("name") + "'", List.of(job));
  }
}
