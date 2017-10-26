/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.netflix.kayenta.canary.*;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.storage.StorageServiceRepository;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.PipelineLauncher;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Pipeline;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/canary")
@Slf4j
public class CanaryController {

  private final String REFID_SET_CONTEXT = "setupContext";
  private final String REFID_FETCH_CONTROL = "fetchControl";
  private final String REFID_FETCH_EXPERIMENT = "fetchExperiment";
  private final String REFID_MIX_METRICS = "mixMetrics";
  private final String REFID_JUDGE = "judge";

  private final String currentInstanceId;
  private final PipelineLauncher pipelineLauncher;
  private final ExecutionRepository executionRepository;
  private final AccountCredentialsRepository accountCredentialsRepository;
  private final StorageServiceRepository storageServiceRepository;
  private final List<CanaryScopeFactory> canaryScopeFactories;
  private final Registry registry;
  private final ObjectMapper kayentaObjectMapper;

  @Autowired
  public CanaryController(String currentInstanceId,
                          PipelineLauncher pipelineLauncher,
                          ExecutionRepository executionRepository,
                          AccountCredentialsRepository accountCredentialsRepository,
                          StorageServiceRepository storageServiceRepository,
                          Optional<List<CanaryScopeFactory>> canaryScopeFactories,
                          Registry registry,
                          ObjectMapper kayentaObjectMapper) {
    this.currentInstanceId = currentInstanceId;
    this.pipelineLauncher = pipelineLauncher;
    this.executionRepository = executionRepository;
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.storageServiceRepository = storageServiceRepository;

    if (canaryScopeFactories.isPresent()) {
      this.canaryScopeFactories = canaryScopeFactories.get();
    } else {
      this.canaryScopeFactories = Collections.emptyList();
    }

    this.registry = registry;
    this.kayentaObjectMapper = kayentaObjectMapper;
  }

  //
  // Initiate a new canary run.
  //
  // TODO(duftler): Allow for user to be passed in.
  @ApiOperation(value = "Initiate a canary pipeline")
  @RequestMapping(value = "/{canaryConfigId:.+}", consumes = "application/json", method = RequestMethod.POST)
  public CanaryExecutionResponse initiateCanary(@RequestParam(required = false) final String metricsAccountName,
                                                @RequestParam(required = false) final String configurationAccountName,
                                                @RequestParam(required = false) final String storageAccountName,
                                                @ApiParam @RequestBody final CanaryExecutionRequest canaryExecutionRequest,
                                                @PathVariable String canaryConfigId) throws JsonProcessingException {
    String resolvedMetricsAccountName = CredentialsHelper.resolveAccountByNameOrType(metricsAccountName,
                                                                                     AccountCredentials.Type.METRICS_STORE,
                                                                                     accountCredentialsRepository);
    String resolvedStorageAccountName = CredentialsHelper.resolveAccountByNameOrType(storageAccountName,
                                                                                     AccountCredentials.Type.OBJECT_STORE,
                                                                                     accountCredentialsRepository);
    String resolvedConfigurationAccountName = CredentialsHelper.resolveAccountByNameOrType(configurationAccountName,
                                                                                           AccountCredentials.Type.CONFIGURATION_STORE,
                                                                                           accountCredentialsRepository);

    StorageService configurationService =
      storageServiceRepository
        .getOne(resolvedConfigurationAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No configuration service was configured."));

    canaryConfigId = canaryConfigId.toLowerCase();

    CanaryConfig canaryConfig = configurationService.loadObject(resolvedConfigurationAccountName, ObjectType.CANARY_CONFIG, canaryConfigId);

    CanaryServiceConfig canaryConfigService =
      canaryConfig
        .getServices()
        .entrySet()
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No metrics store service was specified in canary config."))
        .getValue();
    // TODO(duftler/dpeach): serviceType could change between here and the setupCanary stage
    // (which resolves the canary config for the remaining stages in this pipeline).
    // Should synthetically add the metric fetch stages inside setupCanary.
    String serviceType = canaryConfigService.getType();

    CanaryScopeFactory canaryScopeFactory =
      canaryScopeFactories
        .stream()
        .filter((f) -> f.handles(serviceType)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unable to resolve canary scope factory for '" + serviceType + "'."));

    registry.counter(registry.createId("canary.pipelines.initiated")).increment();

    CanaryScope controlScopeModel = canaryScopeFactory.buildCanaryScope(canaryExecutionRequest.getControlScope());
    CanaryScope experimentScopeModel = canaryScopeFactory.buildCanaryScope(canaryExecutionRequest.getExperimentScope());

    String controlScopeJson = kayentaObjectMapper.writeValueAsString(controlScopeModel);
    String experimentScopeJson = kayentaObjectMapper.writeValueAsString(experimentScopeModel);

    Map<String, Object> setupCanaryContext =
      Maps.newHashMap(
        new ImmutableMap.Builder<String, Object>()
          .put("refId", REFID_SET_CONTEXT)
          .put("user", "[anonymous]")
          .put("canaryConfigId", canaryConfigId)
          .put("configurationAccountName", resolvedConfigurationAccountName)
          .build());

    Map<String, Object> fetchControlContext =
      Maps.newHashMap(
        new ImmutableMap.Builder<String, Object>()
          .put("refId", REFID_FETCH_CONTROL)
          .put("requisiteStageRefIds", Collections.singletonList(REFID_SET_CONTEXT))
          .put("user", "[anonymous]")
          .put("metricsAccountName", resolvedMetricsAccountName)
          .put("storageAccountName", resolvedStorageAccountName)
          .put(serviceType + "CanaryScope", controlScopeJson)
          .build());

    Map<String, Object> fetchExperimentContext =
      Maps.newHashMap(
        new ImmutableMap.Builder<String, Object>()
          .put("refId", REFID_FETCH_EXPERIMENT)
          .put("requisiteStageRefIds", Collections.singletonList(REFID_SET_CONTEXT))
          .put("user", "[anonymous]")
          .put("metricsAccountName", resolvedMetricsAccountName)
          .put("storageAccountName", resolvedStorageAccountName)
          .put(serviceType + "CanaryScope", experimentScopeJson)
          .build());

    Map<String, Object> mixMetricSetsContext =
      Maps.newHashMap(
        new ImmutableMap.Builder<String, Object>()
          .put("refId", REFID_MIX_METRICS)
          .put("requisiteStageRefIds", new ImmutableList.Builder().add(REFID_FETCH_CONTROL).add(REFID_FETCH_EXPERIMENT).build())
          .put("user", "[anonymous]")
          .put("storageAccountName", resolvedStorageAccountName)
          .put("controlMetricSetListIds", "${ #stage('Fetch Control from " + serviceType + "')['context']['metricSetListIds']}")
          .put("experimentMetricSetListIds", "${ #stage('Fetch Experiment from " + serviceType + "')['context']['metricSetListIds']}")
          .build());

    CanaryClassifierThresholdsConfig orchestratorScoreThresholds = canaryExecutionRequest.getThresholds();

    if (orchestratorScoreThresholds == null) {
      // The score thresholds were not explicitly passed in from the orchestrator (i.e. Spinnaker), so just use the canary config values.
      orchestratorScoreThresholds = canaryConfig.getClassifier().getScoreThresholds();
    }

    Map<String, Object> canaryJudgeContext =
      Maps.newHashMap(
        new ImmutableMap.Builder<String, Object>()
          .put("refId", REFID_JUDGE)
          .put("requisiteStageRefIds", Collections.singletonList(REFID_MIX_METRICS))
          .put("user", "[anonymous]")
          .put("storageAccountName", resolvedStorageAccountName)
          .put("metricSetPairListId", "${ #stage('Mix Control and Experiment Results')['context']['metricSetPairListId']}")
          .put("orchestratorScoreThresholds", orchestratorScoreThresholds)
          .build());

    Pipeline pipeline =
      Pipeline
        .builder("kayenta-" + currentInstanceId)
        .withName("Standard Canary Pipeline")
        .withPipelineConfigId(UUID.randomUUID() + "")
        .withStage("setupCanary", "Setup Canary", setupCanaryContext)
        .withStage(serviceType + "Fetch", "Fetch Control from " + serviceType, fetchControlContext)
        .withStage(serviceType + "Fetch", "Fetch Experiment from " + serviceType, fetchExperimentContext)
        .withStage("metricSetMixer", "Mix Control and Experiment Results", mixMetricSetsContext)
        .withStage("canaryJudge", "Perform Analysis", canaryJudgeContext)
        .withLimitConcurrent(false)
        .withExecutionEngine(Execution.ExecutionEngine.v3)
        .build();

    executionRepository.store(pipeline);

    try {
      pipelineLauncher.start(pipeline);
    } catch (Throwable t) {
      handleStartupFailure(pipeline, t);
    }

    return CanaryExecutionResponse.builder().canaryExecutionId(pipeline.getId()).build();
  }

  //
  // Get the results of a canary run by ID
  //
  @ApiOperation(value = "Retrieve status and results for a canary run")
  @RequestMapping(value = "/{canaryConfigId:.+}/{canaryExecutionId:.+}", method = RequestMethod.GET)
  public CanaryExecutionStatusResponse getCanaryResults(@RequestParam(required = false) final String storageAccountName,
                                                        @PathVariable String canaryConfigId,
                                                        @PathVariable String canaryExecutionId) throws JsonProcessingException {
    String resolvedStorageAccountName = CredentialsHelper.resolveAccountByNameOrType(storageAccountName,
                                                                                     AccountCredentials.Type.OBJECT_STORE,
                                                                                     accountCredentialsRepository);

    StorageService storageService =
      storageServiceRepository
        .getOne(resolvedStorageAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No storage service was configured; unable to retrieve results."));

    Pipeline pipeline = executionRepository.retrievePipeline(canaryExecutionId);
    Stage judgeStage = pipeline.getStages().stream()
      .filter(stage -> stage.getRefId().equals(REFID_JUDGE))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unable to find stage '" + REFID_JUDGE + "' in pipeline ID '" + canaryExecutionId + "'"));
    Map<String, Object> judgeContext = judgeStage.getContext();

    Stage contextStage = pipeline.getStages().stream()
      .filter(stage -> stage.getRefId().equals(REFID_SET_CONTEXT))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unable to find stage '" + REFID_JUDGE + "' in pipeline ID '" + canaryExecutionId + "'"));
    Map<String, Object> contextContext = contextStage.getContext();

    if (!contextContext.containsKey("canaryConfigId")) {
      throw new IllegalArgumentException("The judge stage does not contain a canaryConfigId reference");
    }
    String contextCanaryConfigId = (String)contextContext.get("canaryConfigId");
    if (!contextCanaryConfigId.equalsIgnoreCase(canaryConfigId)) {
      throw new IllegalArgumentException("Execution ID does not belong to this configuration ('" + contextCanaryConfigId + "' vs '" + canaryConfigId + "')");
    }

    CanaryExecutionStatusResponse.CanaryExecutionStatusResponseBuilder canaryExecutionStatusResponseBuilder = CanaryExecutionStatusResponse.builder();

    Map<String, String> stageStatus = pipeline.getStages()
      .stream()
      .collect(Collectors.toMap(Stage::getRefId, s -> s.getStatus().toString().toLowerCase()));

    Boolean isComplete = pipeline.getStatus().isComplete();
    String pipelineStatus = pipeline.getStatus().toString().toLowerCase();
    canaryExecutionStatusResponseBuilder.stageStatus(stageStatus);
    canaryExecutionStatusResponseBuilder.complete(isComplete);
    canaryExecutionStatusResponseBuilder.status(pipelineStatus);

    if (isComplete && pipelineStatus.equals("succeeded")) {
      if (judgeContext.containsKey("canaryJudgeResultId")) {
        String canaryJudgeResultId = (String)judgeContext.get("canaryJudgeResultId");
        canaryExecutionStatusResponseBuilder.result(storageService.loadObject(resolvedStorageAccountName, ObjectType.CANARY_JUDGE_RESULT, canaryJudgeResultId));
      }
    }

    return canaryExecutionStatusResponseBuilder.build();
  }

  private Pipeline handleStartupFailure(Pipeline pipeline, Throwable failure) {
    final String canceledBy = "system";
    final String reason = "Failed on startup: " + failure.getMessage();
    final ExecutionStatus status = ExecutionStatus.TERMINAL;
    final Function<Pipeline, Pipeline> reloader;
    final String executionType = "pipeline";

    reloader = (p) -> executionRepository.retrievePipeline(p.getId());

    log.error("Failed to start " + executionType + " " + pipeline.getId(), failure);
    executionRepository.updateStatus(pipeline.getId(), status);
    executionRepository.cancel(pipeline.getId(), canceledBy, reason);

    return reloader.apply(pipeline);
  }
}
