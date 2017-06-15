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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.CanaryScopeFactory;
import com.netflix.kayenta.canary.CanaryServiceConfig;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.storage.StorageServiceRepository;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.PipelineLauncher;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Pipeline;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@RestController
@RequestMapping("/canary")
@Slf4j
public class CanaryController {

  private final String currentInstanceId;
  private final PipelineLauncher pipelineLauncher;
  private final ExecutionRepository executionRepository;
  private final AccountCredentialsRepository accountCredentialsRepository;
  private final StorageServiceRepository storageServiceRepository;
  private final List<CanaryScopeFactory> canaryScopeFactories;
  private final ObjectMapper objectMapper;

  @Autowired
  public CanaryController(String currentInstanceId,
                          PipelineLauncher pipelineLauncher,
                          ExecutionRepository executionRepository,
                          AccountCredentialsRepository accountCredentialsRepository,
                          StorageServiceRepository storageServiceRepository,
                          List<CanaryScopeFactory> canaryScopeFactories,
                          ObjectMapper objectMapper) {
    this.currentInstanceId = currentInstanceId;
    this.pipelineLauncher = pipelineLauncher;
    this.executionRepository = executionRepository;
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.storageServiceRepository = storageServiceRepository;
    this.canaryScopeFactories = canaryScopeFactories;
    this.objectMapper = objectMapper;
  }

  @ApiOperation(value = "Initiate a canary pipeline")
  @RequestMapping(consumes = "application/context+json", method = RequestMethod.POST)
  public Map<String, String> initiateCanary(@RequestParam(required = false) final String metricsAccountName,
                                            @RequestParam(required = false) final String storageAccountName,
                                            @ApiParam(defaultValue = "MySampleStackdriverCanaryConfig") @RequestParam String canaryConfigId,
                                            @ApiParam(defaultValue = "myapp-v010-") @RequestParam String baselineScope,
                                            @ApiParam(defaultValue = "myapp-v021-") @RequestParam String canaryScope,
                                            @ApiParam(defaultValue = "1496329980000") @RequestParam String startTimeMillis,
                                            @ApiParam(defaultValue = "1496417220000") @RequestParam String endTimeMillis,
                                            // TODO(duftler): Normalize this somehow. Stackdriver expects a number in seconds and Atlas expects a duration like PT10S.
                                            @ApiParam(value = "Stackdriver expects a number in seconds and Atlas expects a duration like PT10S.", defaultValue = "3600") @RequestParam String step,
                                            @ApiParam(value = "Atlas requires \"type\" to be set to application, cluster or node.") @RequestBody(required = false) Map<String, String> extendedScopeParams) {
    String resolvedMetricsAccountName = CredentialsHelper.resolveAccountByNameOrType(metricsAccountName,
                                                                                     AccountCredentials.Type.METRICS_STORE,
                                                                                     accountCredentialsRepository);
    String resolvedStorageAccountName = CredentialsHelper.resolveAccountByNameOrType(storageAccountName,
                                                                                     AccountCredentials.Type.OBJECT_STORE,
                                                                                     accountCredentialsRepository);
    StorageService storageService =
      storageServiceRepository
        .getOne(resolvedStorageAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No storage service was configured; unable to retrieve canary config."));

    canaryConfigId = canaryConfigId.toLowerCase();

    CanaryConfig canaryConfig = storageService.loadObject(resolvedStorageAccountName, ObjectType.CANARY_CONFIG, canaryConfigId);

    CanaryServiceConfig canaryConfigService =
      canaryConfig
        .getServices()
        .entrySet()
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No metrics store service was specified in canary config."))
        .getValue();
    String serviceType = canaryConfigService.getType();

    CanaryScopeFactory canaryScopeFactory =
      canaryScopeFactories
        .stream()
        .filter((f) -> f.handles(serviceType)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unable to resolve canary scope factory for '" + serviceType + "'."));

    CanaryScope baselineScopeModel =
      canaryScopeFactory.buildCanaryScope(baselineScope, startTimeMillis, endTimeMillis, step, extendedScopeParams);
    CanaryScope canaryScopeModel =
      canaryScopeFactory.buildCanaryScope(canaryScope, startTimeMillis, endTimeMillis, step, extendedScopeParams);

    Map<String, Object> fetchBaselineContext =
      Maps.newHashMap(
        new ImmutableMap.Builder<String, Object>()
          .put("refId", "1")
          .put("user", "[anonymous]")
          .put("metricsAccountName", resolvedMetricsAccountName)
          .put("storageAccountName", resolvedStorageAccountName)
          .put("canaryConfigId", canaryConfigId)
          .put(serviceType + "CanaryScope", baselineScopeModel)
          .build());

    Map<String, Object> fetchCanaryContext =
      Maps.newHashMap(
        new ImmutableMap.Builder<String, Object>()
          .put("refId", "2")
          .put("user", "[anonymous]")
          .put("metricsAccountName", resolvedMetricsAccountName)
          .put("storageAccountName", resolvedStorageAccountName)
          .put("canaryConfigId", canaryConfigId)
          .put(serviceType + "CanaryScope", canaryScopeModel)
          .build());

    Map<String, Object> mixMetricSetsContext =
      Maps.newHashMap(
        new ImmutableMap.Builder<String, Object>()
          .put("refId", "3")
          .put("requisiteStageRefIds", new ImmutableList.Builder().add("1").add("2").build())
          .put("user", "[anonymous]")
          .put("storageAccountName", resolvedStorageAccountName)
          .put("controlMetricSetListIds", "${ #stage('Fetch Baseline from " + serviceType + "')['context']['metricSetListIds']}")
          .put("experimentMetricSetListIds", "${ #stage('Fetch Canary from " + serviceType + "')['context']['metricSetListIds']}")
          .build());

    Map<String, Object> canaryJudgeContext =
      Maps.newHashMap(
        new ImmutableMap.Builder<String, Object>()
          .put("refId", "4")
          .put("requisiteStageRefIds", Collections.singletonList("3"))
          .put("user", "[anonymous]")
          .put("canaryConfigId", canaryConfigId)
          .put("metricSetPairListId", "${ #stage('Mix Baseline and Canary Results')['context']['metricSetPairListId']}")
          .build());

    Pipeline pipeline =
      Pipeline
        .builder()
        .withApplication("kayenta-" + currentInstanceId)
        .withName("Standard Canary Pipeline")
        .withPipelineConfigId(UUID.randomUUID() + "")
        .withStage(serviceType + "Fetch", "Fetch Baseline from " + serviceType, fetchBaselineContext)
        .withStage(serviceType + "Fetch", "Fetch Canary from " + serviceType, fetchCanaryContext)
        .withStage("metricSetMixer", "Mix Baseline and Canary Results", mixMetricSetsContext)
        .withStage("canaryJudge", "Perform Analysis", canaryJudgeContext)
        .withParallel(true)
        .withLimitConcurrent(false)
        .withExecutingInstance(currentInstanceId)
        .withExecutionEngine(Execution.ExecutionEngine.v3)
        .build();

    executionRepository.store(pipeline);

    try {
      pipelineLauncher.start(pipeline);
    } catch (Throwable t) {
      handleStartupFailure(pipeline, t);
    }

    return Collections.singletonMap("ref", "/pipelines/" + pipeline.getId());
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
