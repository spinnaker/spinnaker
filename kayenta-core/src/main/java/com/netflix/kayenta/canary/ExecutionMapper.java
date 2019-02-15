/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.kayenta.canary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.netflix.kayenta.canary.orca.CanaryStageNames;
import com.netflix.kayenta.canary.providers.metrics.QueryConfigUtils;
import com.netflix.kayenta.canary.results.CanaryJudgeResult;
import com.netflix.kayenta.canary.results.CanaryResult;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;
import com.netflix.kayenta.storage.StorageServiceRepository;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.PipelineBuilder;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@Slf4j
public class ExecutionMapper {

  public static final String PIPELINE_NAME = "Standard Canary Pipeline";

  private final StorageServiceRepository storageServiceRepository;
  private final AccountCredentialsRepository accountCredentialsRepository;
  private final ObjectMapper objectMapper;
  private final Registry registry;
  private final String currentInstanceId;
  private final List<CanaryScopeFactory> canaryScopeFactories;
  private final ExecutionLauncher executionLauncher;
  private final ExecutionRepository executionRepository;

  private final Id pipelineRunId;
  private final Id failureId;

  @Autowired
  public ExecutionMapper(StorageServiceRepository storageServiceRepository,
                         AccountCredentialsRepository accountCredentialsRepository,
                         ObjectMapper objectMapper,
                         Registry registry,
                         String currentInstanceId,
                         Optional<List<CanaryScopeFactory>> canaryScopeFactories,
                         ExecutionLauncher executionLauncher,
                         ExecutionRepository executionRepository) {
    this.storageServiceRepository = storageServiceRepository;
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.objectMapper = objectMapper;
    this.registry = registry;
    this.currentInstanceId = currentInstanceId;
    this.canaryScopeFactories = canaryScopeFactories.orElseGet(Collections::emptyList);
    this.executionLauncher = executionLauncher;
    this.executionRepository = executionRepository;

    this.pipelineRunId = registry.createId("canary.pipelines.initiated");
    this.failureId = registry.createId("canary.pipelines.startupFailed");
  }

  public CanaryExecutionStatusResponse fromExecution(Execution pipeline) {
    String canaryExecutionId = pipeline.getId();

    Stage contextStage = pipeline.getStages().stream()
      .filter(stage -> stage.getRefId().equals(CanaryStageNames.REFID_SET_CONTEXT))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unable to find stage '" + CanaryStageNames.REFID_SET_CONTEXT + "' in pipeline ID '" + canaryExecutionId + "'"));
    Map<String, Object> contextContext = contextStage.getContext();

    String storageAccountName = (String)contextContext.get("storageAccountName");
    return fromExecution(storageAccountName, pipeline);
  }

  public CanaryExecutionStatusResponse fromExecution(String unresolvedStorageAccountName, Execution pipeline) {
    String storageAccountName = CredentialsHelper.resolveAccountByNameOrType(unresolvedStorageAccountName,
                                                                             AccountCredentials.Type.OBJECT_STORE,
                                                                             accountCredentialsRepository);

    String canaryExecutionId = pipeline.getId();

    Stage judgeStage = pipeline.getStages().stream()
      .filter(stage -> stage.getRefId().equals(CanaryStageNames.REFID_JUDGE))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unable to find stage '" + CanaryStageNames.REFID_JUDGE + "' in pipeline ID '" + canaryExecutionId + "'"));
    Map<String, Object> judgeOutputs = judgeStage.getOutputs();

    Stage contextStage = pipeline.getStages().stream()
      .filter(stage -> stage.getRefId().equals(CanaryStageNames.REFID_SET_CONTEXT))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unable to find stage '" + CanaryStageNames.REFID_SET_CONTEXT + "' in pipeline ID '" + canaryExecutionId + "'"));
    Map<String, Object> contextContext = contextStage.getContext();

    Stage mixerStage = pipeline.getStages().stream()
      .filter(stage -> stage.getRefId().equals(CanaryStageNames.REFID_MIX_METRICS))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unable to find stage '" + CanaryStageNames.REFID_MIX_METRICS + "' in pipeline ID '" + canaryExecutionId + "'"));
    Map<String, Object> mixerContext = mixerStage.getContext();
    Map<String, Object> mixerOutputs = mixerStage.getOutputs();

    CanaryExecutionStatusResponse.CanaryExecutionStatusResponseBuilder canaryExecutionStatusResponseBuilder =
      CanaryExecutionStatusResponse.builder()
        .application((String)contextContext.get("application"))
        .parentPipelineExecutionId((String)contextContext.get("parentPipelineExecutionId"))
        .pipelineId(pipeline.getId())
        .storageAccountName(storageAccountName);
    if (contextContext.containsKey("canaryConfigId")) {
      String canaryConfigId = (String)contextContext.get("canaryConfigId");
      canaryExecutionStatusResponseBuilder.canaryConfigId(canaryConfigId);
    }
    if (contextContext.containsKey("configurationAccountName")) {
      String configurationAccountName = (String)contextContext.get("configurationAccountName");
      canaryExecutionStatusResponseBuilder.configurationAccountName(configurationAccountName);
    }
    canaryExecutionStatusResponseBuilder.config(getCanaryConfig(pipeline));
    CanaryExecutionRequest canaryExecutionRequest = getCanaryExecutionRequest(pipeline);
    canaryExecutionStatusResponseBuilder.canaryExecutionRequest(canaryExecutionRequest);

    if (mixerOutputs.containsKey("metricSetPairListId")) {
      canaryExecutionStatusResponseBuilder.metricSetPairListId((String)mixerOutputs.get("metricSetPairListId"));
    }

    Map<String, String> stageStatus = pipeline.getStages()
      .stream()
      .collect(Collectors.toMap(Stage::getRefId, s -> s.getStatus().toString().toLowerCase()));

    Boolean isComplete = pipeline.getStatus().isComplete();
    String pipelineStatus = pipeline.getStatus().toString().toLowerCase();

    canaryExecutionStatusResponseBuilder
      .stageStatus(stageStatus)
      .complete(isComplete)
      .status(pipelineStatus);

    Long buildTime = pipeline.getBuildTime();
    if (buildTime != null) {
      canaryExecutionStatusResponseBuilder
        .buildTimeMillis(buildTime)
        .buildTimeIso(Instant.ofEpochMilli(buildTime) + "");
    }

    Long startTime = pipeline.getStartTime();
    if (startTime != null) {
      canaryExecutionStatusResponseBuilder
        .startTimeMillis(startTime)
        .startTimeIso(Instant.ofEpochMilli(startTime) + "");
    }

    Long endTime = pipeline.getEndTime();
    if (endTime != null) {
      canaryExecutionStatusResponseBuilder
        .endTimeMillis(endTime)
        .endTimeIso(Instant.ofEpochMilli(endTime) + "");
    }

    if (isComplete && pipelineStatus.equals("succeeded")) {
      if (judgeOutputs.containsKey("result")) {
        Map<String, Object> resultMap = (Map<String, Object>)judgeOutputs.get("result");
        CanaryJudgeResult canaryJudgeResult = objectMapper.convertValue(resultMap, CanaryJudgeResult.class);
        Duration canaryDuration = canaryExecutionRequest != null ? canaryExecutionRequest.calculateDuration() : null;
        CanaryResult result = CanaryResult.builder().judgeResult(canaryJudgeResult).canaryDuration(canaryDuration).build();
        canaryExecutionStatusResponseBuilder.result(result);
      }
    }

    // Propagate the first canary pipeline exception we can locate.
    Stage stageWithException = pipeline.getStages().stream()
      .filter(stage -> stage.getContext().containsKey("exception"))
      .findFirst()
      .orElse(null);

    if (stageWithException != null) {
      canaryExecutionStatusResponseBuilder.exception(stageWithException.getContext().get("exception"));
    }

    return canaryExecutionStatusResponseBuilder.build();
  }

  // Some older (stored) results have the execution request only in the judge context.
  public String getCanaryExecutionRequestFromJudgeContext(Execution pipeline) {
    Stage contextStage = pipeline.getStages().stream()
      .filter(stage -> stage.getRefId().equals(CanaryStageNames.REFID_JUDGE))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unable to find stage '" + CanaryStageNames.REFID_JUDGE + "' in pipeline ID '" + pipeline.getId() + "'"));
    Map<String, Object> context = contextStage.getContext();

    return (String) context.get("canaryExecutionRequest");
  }


  public CanaryExecutionRequest getCanaryExecutionRequest(Execution pipeline) {
    Stage contextStage = pipeline.getStages().stream()
      .filter(stage -> stage.getRefId().equals(CanaryStageNames.REFID_SET_CONTEXT))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unable to find stage '" + CanaryStageNames.REFID_SET_CONTEXT + "' in pipeline ID '" + pipeline.getId() + "'"));
    Map<String, Object> context = contextStage.getContext();

    String canaryExecutionRequestJSON = (String)context.get("canaryExecutionRequest");
    if (canaryExecutionRequestJSON == null) {
      canaryExecutionRequestJSON = getCanaryExecutionRequestFromJudgeContext(pipeline);
    }
    if (canaryExecutionRequestJSON == null) {
      return null;
    }
    CanaryExecutionRequest canaryExecutionRequest = null;
    try {
      canaryExecutionRequest = objectMapper.readValue(canaryExecutionRequestJSON, CanaryExecutionRequest.class);
    } catch (IOException e) {
      log.error("Cannot deserialize canaryExecutionRequest", e);
      throw new IllegalArgumentException("Cannot deserialize canaryExecutionRequest", e);
    }
    return canaryExecutionRequest;
  }

  public CanaryConfig getCanaryConfig(Execution pipeline) {
    Stage contextStage = pipeline.getStages().stream()
      .filter(stage -> stage.getRefId().equals(CanaryStageNames.REFID_SET_CONTEXT))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unable to find stage '" + CanaryStageNames.REFID_SET_CONTEXT + "' in pipeline ID '" + pipeline.getId() + "'"));
    Map<String, Object> context = contextStage.getContext();

    Map<String, Object> canaryConfigMap = (Map<String, Object>)context.get("canaryConfig");
    return objectMapper.convertValue(canaryConfigMap, CanaryConfig.class);
  }


  private CanaryScopeFactory getScopeFactoryForServiceType(String serviceType) {
    return canaryScopeFactories
      .stream()
      .filter((f) -> f.handles(serviceType)).findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve canary scope factory for '" + serviceType + "'."));
  }

  private CanaryScope getScopeForNamedScope(CanaryExecutionRequest executionRequest, String scopeName, boolean isCanary) {
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
        if (metric.getScopeName() == null) {
          throw new IllegalArgumentException("Canary scope for metric named '" + metric.getName() + "' is null.");
        }
        CanaryScope inspecificScope = getScopeForNamedScope(executionRequest, metric.getScopeName(), isCanary);
        CanaryScope scopeModel = canaryScopeFactory.buildCanaryScope(inspecificScope);
        String stagePrefix = (isCanary ? CanaryStageNames.REFID_FETCH_EXPERIMENT_PREFIX : CanaryStageNames.REFID_FETCH_CONTROL_PREFIX);
        String scopeJson;
        try {
          scopeJson = objectMapper.writeValueAsString(scopeModel);
        } catch (JsonProcessingException e) {
          throw new IllegalArgumentException("Cannot render scope to json"); // TODO: this seems like cheating
        }

        String currentStageId = stagePrefix + index;
        String previousStageId = (index == 0) ? CanaryStageNames.REFID_SET_CONTEXT : stagePrefix + (index - 1);

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

  public CanaryExecutionResponse buildExecution(String application,
                                                String parentPipelineExecutionId,
                                                @NotNull String canaryConfigId,
                                                @NotNull CanaryConfig canaryConfig,
                                                String resolvedConfigurationAccountName,
                                                @NotNull String resolvedMetricsAccountName,
                                                @NotNull String resolvedStorageAccountName,
                                                @NotNull CanaryExecutionRequest canaryExecutionRequest) throws JsonProcessingException {
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

    // TODO: Will non-spinnaker users need to know what application to pass (probably should pass something, or else how will they group their runs)?
    if (StringUtils.isEmpty(application)) {
      application = "kayenta-" + currentInstanceId;
    }

    canaryConfig = QueryConfigUtils.escapeTemplates(canaryConfig);

    ImmutableMap.Builder<String, Object> mapBuilder = new ImmutableMap.Builder<String, Object>()
      .put("refId", CanaryStageNames.REFID_SET_CONTEXT)
      .put("user", "[anonymous]")
      .put("application", application)
      .put("storageAccountName", resolvedStorageAccountName)
      .put("canaryConfig", canaryConfig);
    if (parentPipelineExecutionId != null) {
      mapBuilder.put("parentPipelineExecutionId", parentPipelineExecutionId);
    }

    HashMap<String, Object> setupCanaryContext = Maps.newHashMap(mapBuilder.build());
    if (resolvedConfigurationAccountName != null) {
      setupCanaryContext.put("configurationAccountName", resolvedConfigurationAccountName);
    }
    if (canaryConfigId != null) {
      setupCanaryContext.put("canaryConfigId", canaryConfigId);
    }

    List<Map<String, Object>> fetchExperimentContexts = generateFetchScopes(canaryConfig,
                                                                            canaryExecutionRequest,
                                                                            true,
                                                                            resolvedMetricsAccountName,
                                                                            resolvedStorageAccountName);
    List<Map<String, Object>> controlFetchContexts = generateFetchScopes(canaryConfig,
                                                                         canaryExecutionRequest,
                                                                         false,
                                                                         resolvedMetricsAccountName,
                                                                         resolvedStorageAccountName);

    int maxMetricIndex = canaryConfig.getMetrics().size() - 1; // 0 based naming, so we want the last index value, not the count
    String lastControlFetchRefid = CanaryStageNames.REFID_FETCH_CONTROL_PREFIX + maxMetricIndex;
    String lastExperimentFetchRefid = CanaryStageNames.REFID_FETCH_EXPERIMENT_PREFIX + maxMetricIndex;

    Map<String, Object> mixMetricSetsContext =
      Maps.newHashMap(
        new ImmutableMap.Builder<String, Object>()
          .put("refId", CanaryStageNames.REFID_MIX_METRICS)
          .put("requisiteStageRefIds", new ImmutableList.Builder().add(lastControlFetchRefid).add(lastExperimentFetchRefid).build())
          .put("user", "[anonymous]")
          .put("storageAccountName", resolvedStorageAccountName)
          .put("controlRefidPrefix", CanaryStageNames.REFID_FETCH_CONTROL_PREFIX)
          .put("experimentRefidPrefix", CanaryStageNames.REFID_FETCH_EXPERIMENT_PREFIX)
          .build());

    final CanaryClassifierThresholdsConfig orchestratorScoreThresholds = canaryExecutionRequest.getThresholds();
    if (orchestratorScoreThresholds == null) {
      throw new IllegalArgumentException("Execution request must contain thresholds");
    }

    String canaryExecutionRequestJSON = objectMapper.writeValueAsString(canaryExecutionRequest);
    setupCanaryContext.put("canaryExecutionRequest", canaryExecutionRequestJSON);

    Map<String, Object> canaryJudgeContext =
      Maps.newHashMap(
        new ImmutableMap.Builder<String, Object>()
          .put("refId", CanaryStageNames.REFID_JUDGE)
          .put("requisiteStageRefIds", Collections.singletonList(CanaryStageNames.REFID_MIX_METRICS))
          .put("user", "[anonymous]")
          .put("storageAccountName", resolvedStorageAccountName)
          .put("metricSetPairListId", "${ #stage('Mix Control and Experiment Results')['context']['metricSetPairListId']}")
          .put("orchestratorScoreThresholds", orchestratorScoreThresholds)
          .build());

    String canaryPipelineConfigId = application + "-standard-canary-pipeline";
    PipelineBuilder pipelineBuilder =
      new PipelineBuilder(application)
        .withName(PIPELINE_NAME)
        .withPipelineConfigId(canaryPipelineConfigId)
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
      .build();

    executionRepository.store(pipeline);

    try {
      executionLauncher.start(pipeline);
    } catch (Throwable t) {
      handleStartupFailure(pipeline, t);
    }

    return CanaryExecutionResponse.builder().canaryExecutionId(pipeline.getId()).build();
  }

  public CanaryExecutionResponse buildJudgeComparisonExecution(String application,
                                                               String parentPipelineExecutionId,
                                                               @NotNull String canaryConfigId,
                                                               @NotNull CanaryConfig canaryConfig,
                                                               String overrideCanaryJudge1,
                                                               String overrideCanaryJudge2,
                                                               String metricSetPairListId,
                                                               Double passThreshold,
                                                               Double marginalThreshold,
                                                               String resolvedConfigurationAccountName,
                                                               @NotNull String resolvedStorageAccountName) throws JsonProcessingException {
    if (StringUtils.isEmpty(application)) {
      application = "kayenta-" + currentInstanceId;
    }

    canaryConfig = QueryConfigUtils.escapeTemplates(canaryConfig);

    ImmutableMap.Builder<String, Object> mapBuilder = new ImmutableMap.Builder<String, Object>()
      .put("refId", CanaryStageNames.REFID_SET_CONTEXT)
      .put("user", "[anonymous]")
      .put("application", application)
      .put("storageAccountName", resolvedStorageAccountName)
      .put("canaryConfig", canaryConfig);
    if (parentPipelineExecutionId != null) {
      mapBuilder.put("parentPipelineExecutionId", parentPipelineExecutionId);
    }

    HashMap<String, Object> setupCanaryContext = Maps.newHashMap(mapBuilder.build());

    if (resolvedConfigurationAccountName != null) {
      setupCanaryContext.put("configurationAccountName", resolvedConfigurationAccountName);
    }
    if (canaryConfigId != null) {
      setupCanaryContext.put("canaryConfigId", canaryConfigId);
    }

    Map<String, Object> canaryJudgeContext1 =
      Maps.newHashMap(
        new ImmutableMap.Builder<String, Object>()
          .put("refId", CanaryStageNames.REFID_JUDGE)
          .put("requisiteStageRefIds", Collections.singletonList(CanaryStageNames.REFID_SET_CONTEXT))
          .put("user", "[anonymous]")
          .put("storageAccountName", resolvedStorageAccountName)
          .put("metricSetPairListId", metricSetPairListId)
          .put("orchestratorScoreThresholds", CanaryClassifierThresholdsConfig.builder().pass(passThreshold).marginal(marginalThreshold).build())
          .build());
    if (StringUtils.isNotEmpty(overrideCanaryJudge1)) {
      canaryJudgeContext1.put("overrideJudgeName", overrideCanaryJudge1);
    }

    Map<String, Object> canaryJudgeContext2 =
      Maps.newHashMap(
        new ImmutableMap.Builder<String, Object>()
          .put("refId", CanaryStageNames.REFID_JUDGE + "-2")
          .put("requisiteStageRefIds", Collections.singletonList(CanaryStageNames.REFID_SET_CONTEXT))
          .put("user", "[anonymous]")
          .put("storageAccountName", resolvedStorageAccountName)
          .put("metricSetPairListId", metricSetPairListId)
          .put("orchestratorScoreThresholds", CanaryClassifierThresholdsConfig.builder().pass(passThreshold).marginal(marginalThreshold).build())
          .build());
    if (StringUtils.isNotEmpty(overrideCanaryJudge2)) {
      canaryJudgeContext2.put("overrideJudgeName", overrideCanaryJudge2);
    }

    Map<String, Object> compareJudgeResultsContext =
      Maps.newHashMap(
        new ImmutableMap.Builder<String, Object>()
          .put("refId", "compareJudgeResults")
          .put("requisiteStageRefIds", Arrays.asList(new String[]{CanaryStageNames.REFID_JUDGE,
                                                                  CanaryStageNames.REFID_JUDGE + "-2"}))
          .put("user", "[anonymous]")
          .put("storageAccountName", resolvedStorageAccountName)
          .put("judge1Result", "${ #stage('Perform Analysis with Judge 1')['context']['result']}")
          .put("judge2Result", "${ #stage('Perform Analysis with Judge 2')['context']['result']}")
          .build());

    String canaryPipelineConfigId = application + "-standard-canary-pipeline";
    PipelineBuilder pipelineBuilder =
      new PipelineBuilder(application)
        .withName("Standard Canary Pipeline")
        .withPipelineConfigId(canaryPipelineConfigId)
        .withStage("setupCanary", "Setup Canary", setupCanaryContext)
        .withStage("canaryJudge", "Perform Analysis with Judge 1", canaryJudgeContext1)
        .withStage("canaryJudge", "Perform Analysis with Judge 2", canaryJudgeContext2)
        .withStage("compareJudgeResults", "Compare Judge Results", compareJudgeResultsContext);

    Execution pipeline = pipelineBuilder
      .withLimitConcurrent(false)
      .build();

    executionRepository.store(pipeline);

    try {
      executionLauncher.start(pipeline);
    } catch (Throwable t) {
      handleStartupFailure(pipeline, t);
    }

    return CanaryExecutionResponse.builder().canaryExecutionId(pipeline.getId()).build();
  }

  private void handleStartupFailure(Execution execution, Throwable failure) {
    final String canceledBy = "system";
    final String reason = "Failed on startup: " + failure.getMessage();
    final ExecutionStatus status = ExecutionStatus.TERMINAL;

    log.error("Failed to start {} {}", execution.getType(), execution.getId(), failure);
    executionRepository.updateStatus(execution.getType(), execution.getId(), status);
    executionRepository.cancel(execution.getType(), execution.getId(), canceledBy, reason);

    registry.counter(failureId).increment();
  }

}
