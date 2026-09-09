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

package com.netflix.spinnaker.gate.mcp.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.gate.mcp.support.ManualJudgments;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.util.List;
import java.util.Map;
import org.springaicommunity.mcp.annotation.McpArg;
import org.springaicommunity.mcp.annotation.McpResource;

/**
 * Read-only MCP resources exposing application, execution, and manual-judgment state for
 * continuous-delivery context gathering (as opposed to tools, which are for taking action).
 *
 * <p>{@code @McpResource} methods may only return {@code String}, {@code ResourceContents}, a
 * {@code List} of those, or {@code ReadResourceResult} - not arbitrary POJOs/Maps - so results are
 * serialized to a JSON string here.
 */
public class SpinnakerResources {

  private final Front50Service front50Service;
  private final OrcaServiceSelector orcaServiceSelector;
  private final ObjectMapper objectMapper;

  public SpinnakerResources(
      Front50Service front50Service,
      OrcaServiceSelector orcaServiceSelector,
      ObjectMapper objectMapper) {
    this.front50Service = front50Service;
    this.orcaServiceSelector = orcaServiceSelector;
    this.objectMapper = objectMapper;
  }

  @McpResource(
      uri = "spinnaker://applications/{application}",
      name = "Application detail",
      description = "Front50 metadata for a single Spinnaker application.",
      mimeType = "application/json")
  public String applicationDetail(
      @McpArg(name = "application", description = "Application name", required = true)
          String application) {
    return writeValueAsString(
        Retrofit2SyncCall.execute(front50Service.getApplication(application)));
  }

  @McpResource(
      uri = "spinnaker://applications/{application}/pipelines",
      name = "Application recent executions",
      description = "The most recent pipeline executions for an application, newest first.",
      mimeType = "application/json")
  public String applicationPipelines(
      @McpArg(name = "application", description = "Application name", required = true)
          String application) {
    return writeValueAsString(
        Retrofit2SyncCall.execute(
            orcaServiceSelector.select().getPipelines(application, 20, null, false, null, null)));
  }

  @McpResource(
      uri = "spinnaker://executions/{executionId}",
      name = "Execution detail",
      description = "Full detail for a single pipeline execution, including all stages.",
      mimeType = "application/json")
  public String executionDetail(
      @McpArg(name = "executionId", description = "Pipeline execution id", required = true)
          String executionId) {
    return writeValueAsString(
        Retrofit2SyncCall.execute(orcaServiceSelector.select().getPipeline(executionId)));
  }

  @McpResource(
      uri = "spinnaker://applications/{application}/manual-judgments",
      name = "Pending manual judgments",
      description =
          "Manual judgment stages currently blocking one of this application's running pipeline executions.",
      mimeType = "application/json")
  public String applicationManualJudgments(
      @McpArg(name = "application", description = "Application name", required = true)
          String application) {
    List<Map<String, Object>> runningExecutions =
        Retrofit2SyncCall.execute(
            orcaServiceSelector
                .select()
                .getPipelines(application, 100, "RUNNING", true, null, null));
    return writeValueAsString(ManualJudgments.findPendingAcross(runningExecutions));
  }

  private String writeValueAsString(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize MCP resource result", e);
    }
  }
}
