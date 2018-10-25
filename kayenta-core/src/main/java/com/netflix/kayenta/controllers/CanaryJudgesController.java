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
import com.google.common.collect.ImmutableMap;
import com.netflix.kayenta.canary.CanaryClassifierThresholdsConfig;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryExecutionResponse;
import com.netflix.kayenta.canary.CanaryJudge;
import com.netflix.kayenta.canary.CanaryJudgeConfig;
import com.netflix.kayenta.canary.ExecutionMapper;
import com.netflix.kayenta.canary.results.CanaryJudgeResult;
import com.netflix.kayenta.metrics.MetricSetPair;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.storage.StorageServiceRepository;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/judges")
public class CanaryJudgesController {

  private final AccountCredentialsRepository accountCredentialsRepository;
  private final StorageServiceRepository storageServiceRepository;
  private final ExecutionRepository executionRepository;
  private final ExecutionMapper executionMapper;
  private final List<CanaryJudge> canaryJudges;

  @Autowired
  public CanaryJudgesController(AccountCredentialsRepository accountCredentialsRepository,
                                StorageServiceRepository storageServiceRepository,
                                ExecutionRepository executionRepository,
                                ExecutionMapper executionMapper,
                                List<CanaryJudge> canaryJudges) {
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.storageServiceRepository = storageServiceRepository;
    this.executionRepository = executionRepository;
    this.executionMapper = executionMapper;
    this.canaryJudges = canaryJudges;
  }

  @ApiOperation(value = "Retrieve a list of all configured canary judges")
  @RequestMapping(method = RequestMethod.GET)
  List<CanaryJudge> list() {
    return canaryJudges;
  }

  @ApiOperation(value = "Exercise a judge directly, without any orchestration or querying of metrics services")
  @RequestMapping(value = "/judge", method = RequestMethod.POST)
  public CanaryJudgeResult judge(@RequestParam(required = false) final String configurationAccountName,
                                 @RequestParam(required = false) final String storageAccountName,
                                 @RequestParam final String canaryConfigId,
                                 @RequestParam final String metricSetPairListId,
                                 @RequestParam final Double passThreshold,
                                 @RequestParam final Double marginalThreshold) {
    String resolvedConfigurationAccountName = CredentialsHelper.resolveAccountByNameOrType(configurationAccountName,
                                                                                           AccountCredentials.Type.CONFIGURATION_STORE,
                                                                                           accountCredentialsRepository);
    String resolvedStorageAccountName = CredentialsHelper.resolveAccountByNameOrType(storageAccountName,
                                                                                     AccountCredentials.Type.OBJECT_STORE,
                                                                                     accountCredentialsRepository);
    StorageService configurationService =
      storageServiceRepository
        .getOne(resolvedConfigurationAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No configuration service was configured; unable to read canary config from bucket."));
    StorageService storageService =
      storageServiceRepository
        .getOne(resolvedStorageAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No storage service was configured; unable to read metric set pair list from bucket."));

    CanaryConfig canaryConfig = configurationService.loadObject(resolvedConfigurationAccountName, ObjectType.CANARY_CONFIG, canaryConfigId);
    CanaryJudgeConfig canaryJudgeConfig = canaryConfig.getJudge();
    CanaryJudge canaryJudge = null;

    if (canaryJudgeConfig != null) {
      String judgeName = canaryJudgeConfig.getName();

      if (!StringUtils.isEmpty(judgeName)) {
        canaryJudge =
          canaryJudges
            .stream()
            .filter(c -> c.getName().equals(judgeName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unable to resolve canary judge '" + judgeName + "'."));
      }
    }

    if (canaryJudge == null) {
      canaryJudge = canaryJudges.get(0);
    }

    List<MetricSetPair> metricSetPairList = storageService.loadObject(resolvedStorageAccountName, ObjectType.METRIC_SET_PAIR_LIST, metricSetPairListId);
    CanaryClassifierThresholdsConfig canaryClassifierThresholdsConfig = CanaryClassifierThresholdsConfig.builder().pass(passThreshold).marginal(marginalThreshold).build();

    return canaryJudge.judge(canaryConfig, canaryClassifierThresholdsConfig, metricSetPairList);
  }

  @ApiOperation(value = "Apply a pair of judges to a canned set of data")
  @RequestMapping(value = "/comparison", method = RequestMethod.POST)
  public CanaryExecutionResponse initiateJudgeComparison(@RequestParam(required = false) final String configurationAccountName,
                                                         @RequestParam(required = false) final String storageAccountName,
                                                         @RequestParam final String canaryConfigId,
                                                         @ApiParam(value = "The name of the first judge to use, e.g. NetflixACAJudge-v1.0, dredd-v1.0.")
                                                           @RequestParam(required = false) final String overrideCanaryJudge1,
                                                         @ApiParam(value = "The name of the second judge to use, e.g. NetflixACAJudge-v1.0, dredd-v1.0.")
                                                           @RequestParam(required = false) final String overrideCanaryJudge2,
                                                         @RequestParam final String metricSetPairListId,
                                                         @RequestParam final Double passThreshold,
                                                         @RequestParam final Double marginalThreshold) throws JsonProcessingException {
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

    return executionMapper.buildJudgeComparisonExecution("judge-comparison",
                                                         "judge-comparison",
                                                         canaryConfigId,
                                                         canaryConfig,
                                                         overrideCanaryJudge1,
                                                         overrideCanaryJudge2,
                                                         metricSetPairListId,
                                                         passThreshold,
                                                         marginalThreshold,
                                                         resolvedConfigurationAccountName,
                                                         resolvedStorageAccountName);
  }

  @ApiOperation(value = "Retrieve the results of a judge comparison")
  @RequestMapping(value = "/comparison/{executionId:.+}", method = RequestMethod.GET)
  public Map getJudgeComparisonResults(@PathVariable String executionId) {
    Execution pipeline = executionRepository.retrieve(Execution.ExecutionType.PIPELINE, executionId);
    String canaryExecutionId = pipeline.getId();
    Stage compareJudgeResultsStage = pipeline.getStages().stream()
      .filter(stage -> stage.getRefId().equals("compareJudgeResults"))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unable to find stage 'compareJudgeResults' in pipeline ID '" + canaryExecutionId + "'"));
    Map<String, Object> compareJudgeResultsOutputs = compareJudgeResultsStage.getOutputs();
    Boolean isComplete = pipeline.getStatus().isComplete();
    String pipelineStatus = pipeline.getStatus().toString().toLowerCase();
    ImmutableMap.Builder<String, Object> comparisonResult = ImmutableMap.builder();

    if (isComplete && pipelineStatus.equals("succeeded")) {
      comparisonResult.put("comparisonResult", compareJudgeResultsOutputs.get("comparisonResult"));
    }

    // Propagate all the pipeline exceptions we can locate.
    List<Stage> stagesWithException = pipeline.getStages().stream()
      .filter(stage -> stage.getContext().containsKey("exception"))
      .collect(Collectors.toList());

    if (!CollectionUtils.isEmpty(stagesWithException)) {
      comparisonResult.put(
        "exceptions",
        stagesWithException
          .stream()
          .collect(
            Collectors.toMap(s -> s.getName(), s -> s.getContext().get("exception"))
          ));
    }

    return comparisonResult.build();
  }
}
