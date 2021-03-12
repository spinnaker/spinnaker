/*
 * Copyright (c) 2018 Nike, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.kayenta.standalonecanaryanalysis.service;

import static com.netflix.kayenta.standalonecanaryanalysis.orca.task.GenerateCanaryAnalysisResultTask.CANARY_ANALYSIS_EXECUTION_RESULT;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.standalonecanaryanalysis.CanaryAnalysisConfig;
import com.netflix.kayenta.standalonecanaryanalysis.domain.CanaryAnalysisExecutionResponse;
import com.netflix.kayenta.standalonecanaryanalysis.domain.CanaryAnalysisExecutionResult;
import com.netflix.kayenta.standalonecanaryanalysis.domain.CanaryAnalysisExecutionStatusResponse;
import com.netflix.kayenta.standalonecanaryanalysis.domain.StageMetadata;
import com.netflix.kayenta.standalonecanaryanalysis.orca.MonitorKayentaCanaryContext;
import com.netflix.kayenta.standalonecanaryanalysis.orca.stage.GenerateCanaryAnalysisResultStage;
import com.netflix.kayenta.standalonecanaryanalysis.orca.stage.SetupAndExecuteCanariesStage;
import com.netflix.kayenta.standalonecanaryanalysis.storage.StandaloneCanaryAnalysisObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.storage.StorageServiceRepository;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher;
import com.netflix.spinnaker.orca.pipeline.model.PipelineBuilder;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Service that handles starting and mapping Canary Analysis StageExecution pipelines. */
@Slf4j
@Component
public class CanaryAnalysisService {

  public static final String CANARY_ANALYSIS_CONFIG_CONTEXT_KEY = "canaryAnalysisExecutionRequest";
  public static final String CANARY_ANALYSIS_PIPELINE_NAME = "Standalone Canary Analysis Pipeline";

  private final ExecutionLauncher executionLauncher;
  private final ExecutionRepository executionRepository;
  private final StorageServiceRepository storageServiceRepository;
  private final ObjectMapper kayentaObjectMapper;
  private final AccountCredentialsRepository accountCredentialsRepository;
  private final boolean includeAuthentication;

  @Autowired
  public CanaryAnalysisService(
      ExecutionLauncher executionLauncher,
      ExecutionRepository executionRepository,
      StorageServiceRepository storageServiceRepository,
      ObjectMapper kayentaObjectMapper,
      AccountCredentialsRepository accountCredentialsRepository,
      @Value("${kayenta.include-spring-security-authentication-in-pipeline-context:false}")
          boolean includeAuthentication) {

    this.executionLauncher = executionLauncher;
    this.executionRepository = executionRepository;
    this.storageServiceRepository = storageServiceRepository;
    this.kayentaObjectMapper = kayentaObjectMapper;
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.includeAuthentication = includeAuthentication;
  }

  /**
   * Initiates the canary analysis execution Orca pipeline.
   *
   * @param canaryAnalysisConfig The configuration for the canary analysis execution.
   * @return Wrapper object around the execution id.
   */
  public CanaryAnalysisExecutionResponse initiateCanaryAnalysisExecution(
      CanaryAnalysisConfig canaryAnalysisConfig) {

    String application = canaryAnalysisConfig.getApplication();

    var mapBuilder =
        new ImmutableMap.Builder<String, Object>()
            .put(CANARY_ANALYSIS_CONFIG_CONTEXT_KEY, canaryAnalysisConfig);

    if (includeAuthentication) {
      ofNullable(SecurityContextHolder.getContext().getAuthentication())
          .ifPresent(
              authentication -> mapBuilder.put("springSecurityAuthentication", authentication));
    }

    PipelineBuilder pipelineBuilder =
        new PipelineBuilder(application)
            .withName(CANARY_ANALYSIS_PIPELINE_NAME)
            .withPipelineConfigId(application + "-canary-analysis-referee-pipeline")
            .withStage(
                SetupAndExecuteCanariesStage.STAGE_TYPE,
                SetupAndExecuteCanariesStage.STAGE_DESCRIPTION,
                Maps.newHashMap(mapBuilder.build()));

    PipelineExecution pipeline = pipelineBuilder.withLimitConcurrent(false).build();
    executionRepository.store(pipeline);

    try {
      executionLauncher.start(pipeline);
    } catch (Throwable t) {
      log.error("Failed to start pipeline", t);
      handleStartupFailure(pipeline, t);
      throw new RuntimeException("Failed to start the canary analysis pipeline execution");
    }
    return CanaryAnalysisExecutionResponse.builder()
        .canaryAnalysisExecutionId(pipeline.getId())
        .build();
  }

  public CanaryAnalysisExecutionStatusResponse getCanaryAnalysisExecution(
      String canaryAnalysisExecutionId, String nullableStorageAccountName) {

    try {
      PipelineExecution execution =
          executionRepository.retrieve(ExecutionType.PIPELINE, canaryAnalysisExecutionId);
      return fromExecution(execution);
    } catch (ExecutionNotFoundException e) {
      return Optional.ofNullable(nullableStorageAccountName)
          .map(
              storageAccountName -> {
                String resolvedStorageAccountName =
                    accountCredentialsRepository
                        .getRequiredOneBy(storageAccountName, AccountCredentials.Type.OBJECT_STORE)
                        .getName();

                StorageService storageService =
                    storageServiceRepository.getRequiredOne(resolvedStorageAccountName);

                return (CanaryAnalysisExecutionStatusResponse)
                    storageService.loadObject(
                        resolvedStorageAccountName,
                        StandaloneCanaryAnalysisObjectType.STANDALONE_CANARY_RESULT_ARCHIVE,
                        canaryAnalysisExecutionId);
              })
          .orElseThrow(() -> e);
    }
  }

  private void handleStartupFailure(PipelineExecution execution, Throwable failure) {
    final String canceledBy = "system";
    final String reason = "Failed on startup: " + failure.getMessage();
    final ExecutionStatus status = ExecutionStatus.TERMINAL;

    log.error("Failed to start {} {}", execution.getType(), execution.getId(), failure);
    executionRepository.updateStatus(execution.getType(), execution.getId(), status);
    executionRepository.cancel(execution.getType(), execution.getId(), canceledBy, reason);
  }

  /**
   * Maps the pipeline execution to that canary analysis execution status response.
   *
   * @param pipeline The execution
   * @return The status response
   */
  protected CanaryAnalysisExecutionStatusResponse fromExecution(PipelineExecution pipeline) {

    CanaryAnalysisExecutionStatusResponse.CanaryAnalysisExecutionStatusResponseBuilder
        responseBuilder =
            CanaryAnalysisExecutionStatusResponse.builder()
                .application(pipeline.getApplication())
                .pipelineId(pipeline.getId())
                .stageStatus(
                    pipeline.getStages().stream()
                        .map(
                            stage ->
                                new StageMetadata(
                                    stage.getType(),
                                    stage.getName(),
                                    stage.getStatus(),
                                    stage
                                        .mapTo(MonitorKayentaCanaryContext.class)
                                        .getCanaryPipelineExecutionId()))
                        .collect(Collectors.toList()))
                .complete(pipeline.getStatus().isComplete())
                .executionStatus(pipeline.getStatus());

    // Add the request and config info if possible
    pipeline.getStages().stream()
        .filter(stage -> stage.getType().equals(SetupAndExecuteCanariesStage.STAGE_TYPE))
        .findFirst()
        .map(stage -> stage.getContext().get(CANARY_ANALYSIS_CONFIG_CONTEXT_KEY))
        .ifPresent(
            data -> {
              CanaryAnalysisConfig canaryAnalysisConfig =
                  kayentaObjectMapper.convertValue(data, CanaryAnalysisConfig.class);

              responseBuilder.parentPipelineExecutionId(
                  canaryAnalysisConfig.getParentPipelineExecutionId());
              responseBuilder.user(canaryAnalysisConfig.getUser());
              responseBuilder.application(canaryAnalysisConfig.getApplication());
              responseBuilder.canaryConfigId(canaryAnalysisConfig.getCanaryConfigId());
              responseBuilder.canaryAnalysisExecutionRequest(
                  canaryAnalysisConfig.getExecutionRequest());
              responseBuilder.canaryConfig(canaryAnalysisConfig.getCanaryConfig());
              responseBuilder.storageAccountName(canaryAnalysisConfig.getStorageAccountName());
              responseBuilder.metricsAccountName(canaryAnalysisConfig.getMetricsAccountName());
            });

    // Add the canary analysis execution result if present
    pipeline.getStages().stream()
        .filter(stage -> stage.getType().equals(GenerateCanaryAnalysisResultStage.STAGE_TYPE))
        .findFirst()
        .map(
            generateCanaryAnalysisResultStage ->
                generateCanaryAnalysisResultStage
                    .getOutputs()
                    .get(CANARY_ANALYSIS_EXECUTION_RESULT))
        .ifPresent(
            data ->
                responseBuilder.canaryAnalysisExecutionResult(
                    kayentaObjectMapper.convertValue(data, CanaryAnalysisExecutionResult.class)));

    // Propagate first exception.
    pipeline.getStages().stream()
        .filter(stage -> stage.getContext().containsKey("exception"))
        .findFirst()
        .ifPresent(stage -> responseBuilder.exception(stage.getContext().get("exception")));

    Long buildTime = pipeline.getBuildTime();
    if (buildTime != null) {
      responseBuilder.buildTimeMillis(buildTime).buildTimeIso(Instant.ofEpochMilli(buildTime) + "");
    }

    Long startTime = pipeline.getStartTime();
    if (startTime != null) {
      responseBuilder.startTimeMillis(startTime).startTimeIso(Instant.ofEpochMilli(startTime) + "");
    }

    Long endTime = pipeline.getEndTime();
    if (endTime != null) {
      responseBuilder.endTimeMillis(endTime).endTimeIso(Instant.ofEpochMilli(endTime) + "");
    }

    return responseBuilder.build();
  }
}
