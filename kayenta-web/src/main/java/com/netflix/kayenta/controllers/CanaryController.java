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
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.PipelineBuilder;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RestController
@RequestMapping("/canary")
@Slf4j
public class CanaryController {

  private final String REFID_SET_CONTEXT = "setupContext";
  private final String REFID_FETCH_CONTROL_PREFIX = "fetchControl";
  private final String REFID_FETCH_EXPERIMENT_PREFIX = "fetchExperiment";
  private final String REFID_MIX_METRICS = "mixMetrics";
  private final String REFID_JUDGE = "judge";

  private final String currentInstanceId;
  private final ExecutionLauncher executionLauncher;
  private final ExecutionRepository executionRepository;
  private final AccountCredentialsRepository accountCredentialsRepository;
  private final StorageServiceRepository storageServiceRepository;
  private final List<CanaryScopeFactory> canaryScopeFactories;
  private final Registry registry;
  private final ObjectMapper kayentaObjectMapper;
  private final Id pipelineRunId;
  private final Id failureId;

  @Autowired
  public CanaryController(String currentInstanceId,
                          ExecutionLauncher executionLauncher,
                          ExecutionRepository executionRepository,
                          AccountCredentialsRepository accountCredentialsRepository,
                          StorageServiceRepository storageServiceRepository,
                          Optional<List<CanaryScopeFactory>> canaryScopeFactories,
                          Registry registry,
                          ObjectMapper kayentaObjectMapper) {
    this.currentInstanceId = currentInstanceId;
    this.executionLauncher = executionLauncher;
    this.executionRepository = executionRepository;
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.storageServiceRepository = storageServiceRepository;

    this.canaryScopeFactories = canaryScopeFactories.orElseGet(Collections::emptyList);

    this.registry = registry;
    this.kayentaObjectMapper = kayentaObjectMapper;
    this.pipelineRunId = registry.createId("canary.pipelines.initiated");
    this.failureId = registry.createId("canary.pipelines.startupFailed");
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
    CanaryConfig canaryConfig = configurationService.loadObject(resolvedConfigurationAccountName, ObjectType.CANARY_CONFIG, canaryConfigId);

    registry.counter(pipelineRunId.withTag("canaryConfigId", canaryConfigId).withTag("canaryConfigName", canaryConfig.getName())).increment();

    Set<String> requiredScopes = canaryConfig.getMetrics().stream()
      .map(CanaryMetricConfig::getScopeName)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());

    if (requiredScopes.size() > 0 && canaryExecutionRequest.getScopes() == null) {
      throw new IllegalArgumentException("Canary metrics require scopes, but no scopes were provided in the execution request.");
    }
    Set<String> providedScopes = canaryExecutionRequest.getScopes() == null ? Collections.emptySet() : canaryExecutionRequest.getScopes().keySet();
    requiredScopes.removeAll(providedScopes);
    if (requiredScopes.size() > 0) {
      throw new IllegalArgumentException("Canary metrics require scopes which were not provided in the execution request: " + requiredScopes);
    }

    Map<String, Object> setupCanaryContext =
      Maps.newHashMap(
        new ImmutableMap.Builder<String, Object>()
          .put("refId", REFID_SET_CONTEXT)
          .put("user", "[anonymous]")
          .put("canaryConfigId", canaryConfigId)
          .put("configurationAccountName", resolvedConfigurationAccountName)
          .build());

    List<Map<String, Object>> fetchExperimentContexts = generateFetchScopes(canaryConfig, canaryExecutionRequest, true,resolvedMetricsAccountName, resolvedStorageAccountName);
    List<Map<String, Object>> controlFetchContexts = generateFetchScopes(canaryConfig, canaryExecutionRequest, false,resolvedMetricsAccountName, resolvedStorageAccountName);

    int maxMetricIndex = canaryConfig.getMetrics().size() - 1; // 0 based naming, so we want the last index value, not the count
    String lastControlFetchRefid = REFID_FETCH_CONTROL_PREFIX + maxMetricIndex;
    String lastExperimentFetchRefid = REFID_FETCH_EXPERIMENT_PREFIX + maxMetricIndex;

    Map<String, Object> mixMetricSetsContext =
      Maps.newHashMap(
        new ImmutableMap.Builder<String, Object>()
          .put("refId", REFID_MIX_METRICS)
          .put("requisiteStageRefIds", new ImmutableList.Builder().add(lastControlFetchRefid).add(lastExperimentFetchRefid).build())
          .put("user", "[anonymous]")
          .put("storageAccountName", resolvedStorageAccountName)
          .put("controlRefidPrefix", REFID_FETCH_CONTROL_PREFIX)
          .put("experimentRefidPrefix", REFID_FETCH_EXPERIMENT_PREFIX)
          .build());

    CanaryClassifierThresholdsConfig orchestratorScoreThresholds = canaryExecutionRequest.getThresholds();

    if (orchestratorScoreThresholds == null) {
      if (canaryConfig.getClassifier() == null || canaryConfig.getClassifier().getScoreThresholds() == null) {
        throw new IllegalArgumentException("Classifier thresholds must be specified in either the canary config, or the execution request.");
      }
      // The score thresholds were not explicitly passed in from the orchestrator (i.e. Spinnaker), so just use the canary config values.
      orchestratorScoreThresholds = canaryConfig.getClassifier().getScoreThresholds();
    }

    String canaryExecutionRequestJSON = kayentaObjectMapper.writeValueAsString(canaryExecutionRequest);

    Map<String, Object> canaryJudgeContext =
      Maps.newHashMap(
        new ImmutableMap.Builder<String, Object>()
          .put("refId", REFID_JUDGE)
          .put("requisiteStageRefIds", Collections.singletonList(REFID_MIX_METRICS))
          .put("user", "[anonymous]")
          .put("storageAccountName", resolvedStorageAccountName)
          .put("metricSetPairListId", "${ #stage('Mix Control and Experiment Results')['context']['metricSetPairListId']}")
          .put("orchestratorScoreThresholds", orchestratorScoreThresholds)
          .put("canaryExecutionRequest", canaryExecutionRequestJSON)
          .build());

    PipelineBuilder pipelineBuilder =
      new PipelineBuilder("kayenta-" + currentInstanceId)
        .withName("Standard Canary Pipeline")
        .withPipelineConfigId(UUID.randomUUID() + "")
        .withStage("setupCanary", "Setup Canary", setupCanaryContext)
        .withStage("metricSetMixer", "Mix Control and Experiment Results", mixMetricSetsContext)
        .withStage("canaryJudge", "Perform Analysis", canaryJudgeContext);

    controlFetchContexts.forEach((context) ->
                                   pipelineBuilder.withStage((String)context.get("stageType"),
                                                             (String)context.get("refId"),
                                                             context));
    fetchExperimentContexts.forEach((context) ->
                                      pipelineBuilder.withStage((String)context.get("stageType"),
                                                                (String)context.get("refId"),
                                                                context));

    Execution pipeline = pipelineBuilder
      .withLimitConcurrent(false)
      .withExecutionEngine(Execution.ExecutionEngine.v3)
      .build();

    executionRepository.store(pipeline);

    try {
      executionLauncher.start(pipeline);
    } catch (Throwable t) {
      handleStartupFailure(pipeline, t);
    }

    return CanaryExecutionResponse.builder().canaryExecutionId(pipeline.getId()).build();
  }

  //
  // Get the results of a canary run by ID
  //
  @ApiOperation(value = "Retrieve status and results for a canary run")
  @RequestMapping(value = "/{canaryExecutionId:.+}", method = RequestMethod.GET)
  public CanaryExecutionStatusResponse getCanaryResults(@RequestParam(required = false) final String storageAccountName,
                                                        @PathVariable String canaryExecutionId) throws JsonProcessingException {
    String resolvedStorageAccountName = CredentialsHelper.resolveAccountByNameOrType(storageAccountName,
                                                                                     AccountCredentials.Type.OBJECT_STORE,
                                                                                     accountCredentialsRepository);

    StorageService storageService =
      storageServiceRepository
        .getOne(resolvedStorageAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No storage service was configured; unable to retrieve results."));

    Execution pipeline = executionRepository.retrieve(Execution.ExecutionType.PIPELINE, canaryExecutionId);
    Stage judgeStage = pipeline.getStages().stream()
      .filter(stage -> stage.getRefId().equals(REFID_JUDGE))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unable to find stage '" + REFID_JUDGE + "' in pipeline ID '" + canaryExecutionId + "'"));
    Map<String, Object> judgeContext = judgeStage.getContext();

    Stage contextStage = pipeline.getStages().stream()
      .filter(stage -> stage.getRefId().equals(REFID_SET_CONTEXT))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unable to find stage '" + REFID_SET_CONTEXT + "' in pipeline ID '" + canaryExecutionId + "'"));
    Map<String, Object> contextContext = contextStage.getContext();

    Stage mixerStage = pipeline.getStages().stream()
      .filter(stage -> stage.getRefId().equals(REFID_MIX_METRICS))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unable to find stage '" + REFID_MIX_METRICS + "' in pipeline ID '" + canaryExecutionId + "'"));
    Map<String, Object> mixerContext = mixerStage.getContext();

    CanaryExecutionStatusResponse.CanaryExecutionStatusResponseBuilder canaryExecutionStatusResponseBuilder = CanaryExecutionStatusResponse.builder();

    Map<String, String> stageStatus = pipeline.getStages()
      .stream()
      .collect(Collectors.toMap(Stage::getRefId, s -> s.getStatus().toString().toLowerCase()));

    Boolean isComplete = pipeline.getStatus().isComplete();
    String pipelineStatus = pipeline.getStatus().toString().toLowerCase();
    canaryExecutionStatusResponseBuilder.stageStatus(stageStatus);
    canaryExecutionStatusResponseBuilder.complete(isComplete);
    canaryExecutionStatusResponseBuilder.status(pipelineStatus);

    Long buildTime = pipeline.getBuildTime();
    if (buildTime != null) {
      canaryExecutionStatusResponseBuilder.buildTimeMillis(buildTime);
      canaryExecutionStatusResponseBuilder.buildTimeIso(Instant.ofEpochMilli(buildTime) + "");
    }

    Long startTime = pipeline.getStartTime();
    if (startTime != null) {
      canaryExecutionStatusResponseBuilder.startTimeMillis(startTime);
      canaryExecutionStatusResponseBuilder.startTimeIso(Instant.ofEpochMilli(startTime) + "");
    }

    Long endTime = pipeline.getEndTime();
    if (endTime != null) {
      canaryExecutionStatusResponseBuilder.endTimeMillis(endTime);
      canaryExecutionStatusResponseBuilder.endTimeIso(Instant.ofEpochMilli(endTime) + "");
    }

    if (isComplete && pipelineStatus.equals("succeeded")) {
      if (judgeContext.containsKey("canaryJudgeResultId")) {
        String canaryJudgeResultId = (String)judgeContext.get("canaryJudgeResultId");
        canaryExecutionStatusResponseBuilder.result(storageService.loadObject(resolvedStorageAccountName, ObjectType.CANARY_RESULT, canaryJudgeResultId));
      }
    }

    return canaryExecutionStatusResponseBuilder.build();
  }

  private Execution handleStartupFailure(Execution execution, Throwable failure) {
    final String canceledBy = "system";
    final String reason = "Failed on startup: " + failure.getMessage();
    final ExecutionStatus status = ExecutionStatus.TERMINAL;

    log.error("Failed to start {} {}", execution.getType(), execution.getId(), failure);
    executionRepository.updateStatus(execution.getId(), status);
    executionRepository.cancel(execution.getId(), canceledBy, reason);

    registry.counter(failureId).increment();

    return executionRepository.retrieve(execution.getType(), execution.getId());
  }

  private CanaryScopeFactory getScopeFactoryForServiceType(String serviceType) {
    return canaryScopeFactories
      .stream()
      .filter((f) -> f.handles(serviceType)).findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve canary scope factory for '" + serviceType + "'."));
  }

  private CanaryScope getScopeForNamedScope(CanaryExecutionRequest executionRequest, String scopeName, boolean isCanary) {
    if (scopeName == null) {
      return isCanary ? executionRequest.getExperimentScope() : executionRequest.getControlScope();
    }

    CanaryScopePair canaryScopePair = executionRequest.getScopes().get(scopeName);
    CanaryScope canaryScope = isCanary ? canaryScopePair.getExperimentScope() : canaryScopePair.getControlScope();
    if (canaryScope == null) {
      throw new IllegalArgumentException("Canary scope for named scope " + scopeName + " is missing experimentScope or controlScope keys");
    }
    return canaryScope;
  }

  private List<Map<String, Object>> generateFetchScopes(CanaryConfig canaryConfig,
                                                        CanaryExecutionRequest executionRequest,
                                                        boolean isCanary,
                                                        String resolvedMetricsAccountName,
                                                        String resolvedStorageAccountName) {
    return IntStream.range(0, canaryConfig.getMetrics().size())
      .mapToObj(index -> {
        CanaryMetricConfig metric = canaryConfig.getMetrics().get(index);
        String serviceType = metric.getQuery().getServiceType();
        CanaryScopeFactory canaryScopeFactory = getScopeFactoryForServiceType(serviceType);
        CanaryScope inspecificScope = getScopeForNamedScope(executionRequest, metric.getScopeName(), isCanary);
        CanaryScope scopeModel = canaryScopeFactory.buildCanaryScope(inspecificScope);
        String stagePrefix = (isCanary ? REFID_FETCH_EXPERIMENT_PREFIX : REFID_FETCH_CONTROL_PREFIX);
        String scopeJson;
        try {
          scopeJson = kayentaObjectMapper.writeValueAsString(scopeModel);
        } catch (JsonProcessingException e) {
          throw new IllegalArgumentException("Cannot render scope to json"); // TODO: this seems like cheating
        }

        String currentStageId = stagePrefix + index;
        String previousStageId = (index == 0) ? REFID_SET_CONTEXT : stagePrefix + (index - 1);

        return Maps.newHashMap(
          new ImmutableMap.Builder<String, Object>()
            .put("refId", currentStageId)
            .put("metricIndex", index)
            .put("requisiteStageRefIds", Collections.singletonList(previousStageId))
            .put("user", "[anonymous]")
            .put("metricsAccountName", resolvedMetricsAccountName) // TODO: How can this work?  We'd need to look this up per type
            .put("storageAccountName", resolvedStorageAccountName)
            .put("stageType", serviceType + "Fetch")
            .put("canaryScope", scopeJson)
            .build());
      }).collect(Collectors.toList());
  }
}
