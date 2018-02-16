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
import com.netflix.kayenta.canary.orca.CanaryStageNames;
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
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@RestController
@RequestMapping("/canary")
@Slf4j
public class CanaryController {

  private final String AD_HOC = "ad-hoc";

  private final String currentInstanceId;
  private final ExecutionLauncher executionLauncher;
  private final ExecutionRepository executionRepository;
  private final AccountCredentialsRepository accountCredentialsRepository;
  private final StorageServiceRepository storageServiceRepository;
  private final List<CanaryScopeFactory> canaryScopeFactories;
  private final Registry registry;
  private final ObjectMapper kayentaObjectMapper;
  private final ExecutionMapper executionMapper;

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
                          ObjectMapper kayentaObjectMapper,
                          ExecutionMapper executionMapper) {
    this.currentInstanceId = currentInstanceId;
    this.executionLauncher = executionLauncher;
    this.executionRepository = executionRepository;
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.storageServiceRepository = storageServiceRepository;
    this.executionMapper = executionMapper;

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
  public CanaryExecutionResponse initiateCanary(@RequestParam(required = false) final String application,
                                                @RequestParam(required = false) final String parentPipelineExecutionId,
                                                @RequestParam(required = false) final String metricsAccountName,
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

    return buildExecution(application,
                          parentPipelineExecutionId,
                          canaryConfigId,
                          canaryConfig,
                          resolvedConfigurationAccountName,
                          resolvedMetricsAccountName,
                          resolvedStorageAccountName,
                          canaryExecutionRequest);
  }

  //
  // Initiate a new canary run, fully specifying the config and execution request
  //
  // TODO(duftler): Allow for user to be passed in.
  @ApiOperation(value = "Initiate a canary pipeline with CanaryConfig provided")
  @RequestMapping(consumes = "application/json", method = RequestMethod.POST)
  public CanaryExecutionResponse initiateCanaryWithConfig(@RequestParam(required = false) final String metricsAccountName,
                                                          @RequestParam(required = false) final String storageAccountName,
                                                          @ApiParam @RequestBody final CanaryAdhocExecutionRequest canaryAdhocExecutionRequest) throws JsonProcessingException {

    String resolvedMetricsAccountName = CredentialsHelper.resolveAccountByNameOrType(metricsAccountName,
                                                                                     AccountCredentials.Type.METRICS_STORE,
                                                                                     accountCredentialsRepository);
    String resolvedStorageAccountName = CredentialsHelper.resolveAccountByNameOrType(storageAccountName,
                                                                                     AccountCredentials.Type.OBJECT_STORE,
                                                                                     accountCredentialsRepository);

    if (canaryAdhocExecutionRequest.getCanaryConfig() == null) {
      throw new IllegalArgumentException("canaryConfig must be provided for ad-hoc requests");
    }
    if (canaryAdhocExecutionRequest.getExecutionRequest() == null) {
      throw new IllegalArgumentException("executionRequest must be provided for ad-hoc requests");
    }

    return buildExecution(AD_HOC,
                          AD_HOC,
                          AD_HOC,
                          canaryAdhocExecutionRequest.getCanaryConfig(),
                          null,
                          resolvedMetricsAccountName,
                          resolvedStorageAccountName,
                          canaryAdhocExecutionRequest.getExecutionRequest());
  }

  //
  // Get the results of a canary run by ID
  //
  @ApiOperation(value = "Retrieve status and results for a canary run")
  @RequestMapping(value = "/{canaryExecutionId:.+}", method = RequestMethod.GET)
  public CanaryExecutionStatusResponse getCanaryResults(@RequestParam(required = false) final String storageAccountName,
                                                        @PathVariable String canaryExecutionId) {
    String resolvedStorageAccountName = CredentialsHelper.resolveAccountByNameOrType(storageAccountName,
                                                                                     AccountCredentials.Type.OBJECT_STORE,
                                                                                     accountCredentialsRepository);

    Execution pipeline = executionRepository.retrieve(Execution.ExecutionType.PIPELINE, canaryExecutionId);

    return executionMapper.fromExecution(storageAccountName, pipeline);
  }

  private CanaryExecutionResponse buildExecution(String application,
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

    if (StringUtils.isEmpty(parentPipelineExecutionId)) {
      parentPipelineExecutionId = "no-parent-pipeline-execution";
    }

    HashMap<String, Object> setupCanaryContext =
      Maps.newHashMap(
        new ImmutableMap.Builder<String, Object>()
          .put("refId", CanaryStageNames.REFID_SET_CONTEXT)
          .put("user", "[anonymous]")
          .put("application", application)
          .put("parentPipelineExecutionId", parentPipelineExecutionId)
          .put("canaryConfigId", canaryConfigId)
          .put("storageAccountName", resolvedStorageAccountName)
          .build());
    if (resolvedConfigurationAccountName != null) {
      setupCanaryContext.put("configurationAccountName", resolvedConfigurationAccountName);
    }
    if (canaryConfigId.equalsIgnoreCase(AD_HOC)) {
      setupCanaryContext.put("canaryConfig", canaryConfig);
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
          .put("refId", CanaryStageNames.REFID_JUDGE)
          .put("requisiteStageRefIds", Collections.singletonList(CanaryStageNames.REFID_MIX_METRICS))
          .put("user", "[anonymous]")
          .put("storageAccountName", resolvedStorageAccountName)
          .put("metricSetPairListId", "${ #stage('Mix Control and Experiment Results')['context']['metricSetPairListId']}")
          .put("orchestratorScoreThresholds", orchestratorScoreThresholds)
          .put("application", application)
          .put("parentPipelineExecutionId", parentPipelineExecutionId)
          .put("canaryExecutionRequest", canaryExecutionRequestJSON)
          .build());

    String canaryPipelineConfigId = application + "-standard-canary-pipeline";
    PipelineBuilder pipelineBuilder =
      new PipelineBuilder(application)
        .withName("Standard Canary Pipeline")
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
          scopeJson = kayentaObjectMapper.writeValueAsString(scopeModel);
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

  @ApiOperation(value = "Retrieve a list of an application's canary results")
  @RequestMapping(value = "/executions", method = RequestMethod.GET)
  List<CanaryExecutionStatusResponse> getCanaryResultsByApplication(@RequestParam(required = false) String application,
                                                                    @RequestParam(value = "limit", defaultValue = "20") int limit,
                                                                    @RequestParam(value = "statuses", required = false) String statuses,
                                                                    @RequestParam(required = false) final String storageAccountName) {
    String resolvedStorageAccountName = CredentialsHelper.resolveAccountByNameOrType(storageAccountName,
                                                                                     AccountCredentials.Type.OBJECT_STORE,
                                                                                     accountCredentialsRepository);

    StorageService storageService =
      storageServiceRepository
        .getOne(resolvedStorageAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No storage service was configured; unable to retrieve results."));

    if (StringUtils.isEmpty(statuses)) {
      statuses = Stream.of(ExecutionStatus.values())
        .map(s -> s.toString())
        .collect(Collectors.joining(","));
    }

    List<String> statusesList = Stream.of(statuses.split(","))
      .map(s -> s.trim())
      .filter(s -> !StringUtils.isEmpty(s))
      .collect(Collectors.toList());
    ExecutionRepository.ExecutionCriteria executionCriteria = new ExecutionRepository.ExecutionCriteria()
      .setLimit(limit)
      .setStatuses(statusesList);

    // Users of the ad-hoc endpoint can either omit application or pass 'ad-hoc' explicitly.
    if (StringUtils.isEmpty(application)) {
      application = AD_HOC;
    }

    String canaryPipelineConfigId = application + "-standard-canary-pipeline";
    List<Execution> executions = executionRepository.retrievePipelinesForPipelineConfigId(canaryPipelineConfigId, executionCriteria).toList().toBlocking().single();

    return executions
      .stream()
      .map(execution -> executionMapper.fromExecution(resolvedStorageAccountName, execution))
      .collect(Collectors.toList());
  }
}
