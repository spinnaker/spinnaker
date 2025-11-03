/*
 * Copyright 2022 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipeline.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.base.Strings;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.util.List;
import java.util.Map;

/** Subset of pipeline and stage metadata for a failed stage. */
@JsonPropertyOrder({
  "parentPipelineExecutionId",
  "parentPipelineExecutionName",
  "parentPipelineApplication",
  "parentPipelineExecutionUrl",
  "pipelineExecutionId",
  "pipelineExecutionName",
  "pipelineApplication",
  "pipelineExecutionUrl",
  "stageId",
  "stageName",
  "stageType",
  "stageStatus",
  "stageException",
  "childPipelineExecutionId",
  "childPipelineExecutionName",
  "childPipelineApplication",
  "childPipelineExecutionUrl"
})
@JsonInclude(JsonInclude.Include.ALWAYS)
public class FailedStageExecution {

  // Parent pipeline metadata.
  final String parentPipelineExecutionId;
  final String parentPipelineExecutionName;
  final String parentPipelineApplication;
  // Non-final to allow setting after object creation.
  String parentPipelineExecutionUrl;

  // Pipeline metadata of the failing stage.
  final String pipelineExecutionId;
  final String pipelineExecutionName;
  final String pipelineApplication;
  final String pipelineExecutionUrl;

  // Failing stage metadata.
  final String stageId;
  final String stageName;
  final String stageType;
  final ExecutionStatus stageStatus;
  final String stageException;

  // Child pipeline metadata.
  final String childPipelineExecutionId;
  final String childPipelineExecutionName;
  final String childPipelineApplication;
  // Non-final to allow setting after object creation.
  String childPipelineExecutionUrl;

  @JsonCreator
  public FailedStageExecution(
      @JsonProperty("parentPipelineExecutionId") final String parentPipelineExecutionId,
      @JsonProperty("parentPipelineExecutionName") final String parentPipelineExecutionName,
      @JsonProperty("parentPipelineApplication") final String parentPipelineApplication,
      @JsonProperty("parentPipelineExecutionUrl") final String parentPipelineExecutionUrl,
      @JsonProperty("pipelineExecutionId") final String pipelineExecutionId,
      @JsonProperty("pipelineExecutionName") final String pipelineExecutionName,
      @JsonProperty("pipelineApplication") final String pipelineApplication,
      @JsonProperty("pipelineExecutionUrl") final String pipelineExecutionUrl,
      @JsonProperty("stageId") final String stageId,
      @JsonProperty("stageName") final String stageName,
      @JsonProperty("stageType") final String stageType,
      @JsonProperty("stageStatus") final ExecutionStatus stageStatus,
      @JsonProperty("stageException") final String stageException,
      @JsonProperty("childPipelineExecutionId") final String childPipelineExecutionId,
      @JsonProperty("childPipelineExecutionName") final String childPipelineExecutionName,
      @JsonProperty("childPipelineApplication") final String childPipelineApplication,
      @JsonProperty("childPipelineExecutionUrl") final String childPipelineExecutionUrl) {
    this.parentPipelineExecutionId = parentPipelineExecutionId;
    this.parentPipelineExecutionName = parentPipelineExecutionName;
    this.parentPipelineApplication = parentPipelineApplication;
    this.parentPipelineExecutionUrl = parentPipelineExecutionUrl;

    // Pipeline metadata of the failing stage.
    this.pipelineExecutionId = pipelineExecutionId;
    this.pipelineExecutionName = pipelineExecutionName;
    this.pipelineApplication = pipelineApplication;
    this.pipelineExecutionUrl = pipelineExecutionUrl;

    // Failing stage metadata.
    this.stageId = stageId;
    this.stageName = stageName;
    this.stageType = stageType;
    this.stageStatus = stageStatus;
    this.stageException = stageException;

    // Child pipeline metadata.
    this.childPipelineExecutionId = childPipelineExecutionId;
    this.childPipelineExecutionName = childPipelineExecutionName;
    this.childPipelineApplication = childPipelineApplication;
    this.childPipelineExecutionUrl = childPipelineExecutionUrl;
  }

  public static FailedStageExecution of(
      final PipelineExecution pipelineExecution,
      final StageExecution stageExecution,
      final int stageIndex,
      final String deckOrigin) {
    // Parent pipeline metadata.
    String parentPipelineExecutionId = null;
    String parentPipelineExecutionName = null;
    String parentPipelineApplication = null;
    String parentPipelineExecutionUrl = null;

    // NOTE: Only capture parent pipeline metadata if the pipeline trigger was another pipeline.
    if (pipelineExecution.getTrigger() instanceof PipelineTrigger) {
      final PipelineTrigger pipelineTrigger = (PipelineTrigger) pipelineExecution.getTrigger();

      parentPipelineExecutionId = pipelineTrigger.getParentExecution().getId();
      parentPipelineExecutionName = pipelineTrigger.getParentExecution().getName();
      parentPipelineApplication = pipelineTrigger.getParentExecution().getApplication();
    }

    // Best effort to get the stage error message.
    final String stageException = generateStageException(stageExecution);

    // Child pipeline metadata.
    String childPipelineExecutionId = null;
    String childPipelineExecutionName = null;
    String childPipelineApplication = null;
    String childPipelineExecutionUrl = null;

    // Only attempt next level traversal if the current failing stage is a pipeline.
    if (stageExecution.getType().equalsIgnoreCase("pipeline")) {
      // Be defensive as there are no direct field references, i.e. we are in the land of
      // Map<String, Object>.
      final Map<String, Object> stageContext = stageExecution.getContext();

      childPipelineExecutionId =
          Strings.emptyToNull(stageContext.getOrDefault("executionId", "").toString());
      childPipelineExecutionName =
          Strings.emptyToNull(stageContext.getOrDefault("executionName", "").toString());
      childPipelineApplication =
          Strings.emptyToNull(stageContext.getOrDefault("application", "").toString());
    }

    // Build and return the final object.
    final FailedStageExecution failedStageExecution =
        new FailedStageExecution(
            parentPipelineExecutionId,
            parentPipelineExecutionName,
            parentPipelineApplication,
            parentPipelineExecutionUrl,
            pipelineExecution.getId(),
            pipelineExecution.getName(),
            pipelineExecution.getApplication(),
            generatePipelineExecutionUrl(pipelineExecution, stageExecution, stageIndex, deckOrigin),
            stageExecution.getId(),
            stageExecution.getName(),
            stageExecution.getType(),
            stageExecution.getStatus(),
            stageException,
            childPipelineExecutionId,
            childPipelineExecutionName,
            childPipelineApplication,
            childPipelineExecutionUrl);

    return failedStageExecution;
  }

  private static String generatePipelineExecutionUrl(
      final PipelineExecution pipelineExecution,
      final StageExecution stageExecution,
      final int stageIndex,
      final String deckOrigin) {
    // Generate the Deck UI URL using the following template:
    // /#/applications/<application_name>/executions/details/<pipeline_execution_id>?stage=<stage_index>&step=0&details=<config_type>

    // For now assume these are the only valid config types mapping to the "red" error box in the
    // UI.
    final String configType =
        stageExecution.getType().equalsIgnoreCase("pipeline") ? "pipelineConfig" : "runJobConfig";

    final StringBuilder stringBuilder = new StringBuilder();

    if (!Strings.isNullOrEmpty(deckOrigin)) {
      stringBuilder.append(deckOrigin);
    }

    return stringBuilder
        .append("/#/applications/")
        .append(pipelineExecution.getApplication())
        .append("/executions/details/")
        .append(pipelineExecution.getId())
        .append("?stage=")
        .append(stageIndex)
        .append("&step=0&details=")
        .append(configType)
        .toString();
  }

  private static String generateStageException(final StageExecution stageExecution) {
    // Failing stage metadata.
    final Map<String, Object> stageContext = stageExecution.getContext();

    // Track stage type to help with Map<String, Object> look ups.
    final boolean stageIsPipeline = stageExecution.getType().equalsIgnoreCase("pipeline");
    final boolean stageIsRunJobManifest =
        stageExecution.getType().equalsIgnoreCase("runJobManifest");

    String stageException = "NOT_FOUND_CHECK_UI";

    if (stageIsPipeline) {
      try {
        final List<String> errorList =
            (List<String>)
                ((Map<String, Object>)
                        ((Map<String, Object>) stageContext.get("exception")).get("details"))
                    .get("errors");

        if (!errorList.isEmpty() && !Strings.isNullOrEmpty(errorList.get(0))) {
          stageException = errorList.get(0);
        }
      } catch (ClassCastException | NullPointerException e) {
        stageException = "NOT_FOUND_CHECK_UI";
      }
    } else if (stageIsRunJobManifest) {
      try {
        final List<Object> katoTasks = (List<Object>) stageContext.get("kato.tasks");

        if (!katoTasks.isEmpty()) {
          final String cause =
              ((Map<String, Object>) ((Map<String, Object>) katoTasks.get(0)).get("exception"))
                  .getOrDefault("cause", "")
                  .toString();

          if (!Strings.isNullOrEmpty(cause)) {
            stageException = cause;
          }
        }
      } catch (ClassCastException | NullPointerException e) {
        stageException = "NOT_FOUND_CHECK_UI";
      }
    }

    return stageException;
  }

  public static List<FailedStageExecution> updateUrlReferences(
      final List<FailedStageExecution> failedStageExecutions) {
    FailedStageExecution previousFailedStageExecution = null;

    for (final FailedStageExecution failedStageExecution : failedStageExecutions) {
      if (previousFailedStageExecution != null) {
        failedStageExecution.setParentPipelineExecutionUrl(
            previousFailedStageExecution.getPipelineExecutionUrl());
        previousFailedStageExecution.setChildPipelineExecutionUrl(
            failedStageExecution.getPipelineExecutionUrl());
      }

      previousFailedStageExecution = failedStageExecution;
    }

    return failedStageExecutions;
  }

  public String getParentPipelineExecutionId() {
    return parentPipelineExecutionId;
  }

  public String getParentPipelineExecutionName() {
    return parentPipelineExecutionName;
  }

  public String getParentPipelineApplication() {
    return parentPipelineApplication;
  }

  public String getParentPipelineExecutionUrl() {
    return parentPipelineExecutionUrl;
  }

  public void setParentPipelineExecutionUrl(final String parentPipelineExecutionUrl) {
    this.parentPipelineExecutionUrl = parentPipelineExecutionUrl;
  }

  public String getPipelineExecutionId() {
    return pipelineExecutionId;
  }

  public String getPipelineExecutionName() {
    return pipelineExecutionName;
  }

  public String getPipelineApplication() {
    return pipelineApplication;
  }

  public String getPipelineExecutionUrl() {
    return pipelineExecutionUrl;
  }

  public String getStageId() {
    return stageId;
  }

  public String getStageName() {
    return stageName;
  }

  public String getStageType() {
    return stageType;
  }

  public ExecutionStatus getStageStatus() {
    return stageStatus;
  }

  public String getStageException() {
    return stageException;
  }

  public String getChildPipelineExecutionId() {
    return childPipelineExecutionId;
  }

  public String getChildPipelineExecutionName() {
    return childPipelineExecutionName;
  }

  public String getChildPipelineApplication() {
    return childPipelineApplication;
  }

  public String getChildPipelineExecutionUrl() {
    return childPipelineExecutionUrl;
  }

  public void setChildPipelineExecutionUrl(final String childPipelineExecutionUrl) {
    this.childPipelineExecutionUrl = childPipelineExecutionUrl;
  }
}
