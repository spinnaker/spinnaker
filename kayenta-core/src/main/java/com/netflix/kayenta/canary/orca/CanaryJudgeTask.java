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

package com.netflix.kayenta.canary.orca;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.kayenta.canary.*;
import com.netflix.kayenta.canary.results.CanaryJudgeResult;
import com.netflix.kayenta.canary.results.CanaryResult;
import com.netflix.kayenta.metrics.MetricSetPair;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.storage.StorageServiceRepository;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class CanaryJudgeTask implements RetryableTask {

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository;

  @Autowired
  StorageServiceRepository storageServiceRepository;

  @Autowired
  List<CanaryJudge> canaryJudges;

  @Autowired
  ObjectMapper objectMapper;

  @Override
  public long getBackoffPeriod() {
    // TODO(duftler): Externalize this configuration.
    return Duration.ofSeconds(2).toMillis();
  }

  @Override
  public long getTimeout() {
    // TODO(duftler): Externalize this configuration.
    return Duration.ofMinutes(2).toMillis();
  }

  @Override
  public TaskResult execute(Stage stage) {
    Map<String, Object> context = stage.getContext();
    String storageAccountName = (String)context.get("storageAccountName");
    String resolvedStorageAccountName = CredentialsHelper.resolveAccountByNameOrType(storageAccountName,
                                                                                     AccountCredentials.Type.OBJECT_STORE,
                                                                                     accountCredentialsRepository);
    String metricSetPairListId = (String)context.get("metricSetPairListId");
    Map<String, String> orchestratorScoreThresholdsMap = (Map<String, String>)context.get("orchestratorScoreThresholds");
    CanaryClassifierThresholdsConfig orchestratorScoreThresholds = objectMapper.convertValue(orchestratorScoreThresholdsMap,
                                                                                             CanaryClassifierThresholdsConfig.class);
    StorageService storageService =
      storageServiceRepository
        .getOne(resolvedStorageAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No storage service was configured; unable to load metric set lists."));

    Map<String, Object> canaryConfigMap = (Map<String, Object>)context.get("canaryConfig");
    CanaryConfig canaryConfig = objectMapper.convertValue(canaryConfigMap, CanaryConfig.class);
    List<MetricSetPair> metricSetPairList = storageService.loadObject(resolvedStorageAccountName, ObjectType.METRIC_SET_PAIR_LIST, metricSetPairListId);
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

    String canaryExecutionRequestJSON = (String)context.get("canaryExecutionRequest");
    CanaryExecutionRequest canaryExecutionRequest = null;
    try {
      canaryExecutionRequest = objectMapper.readValue(canaryExecutionRequestJSON, CanaryExecutionRequest.class);
    } catch (IOException e) {
      log.error("Cannot deserialize canaryExecutionRequest", e);
      throw new IllegalArgumentException("Cannot deserialize canaryExecutionRequest", e);
    }

    CanaryJudgeResult result = canaryJudge.judge(canaryConfig, orchestratorScoreThresholds, metricSetPairList);
    String canaryJudgeResultId = UUID.randomUUID() + "";

    CanaryResult canaryResult = CanaryResult.builder()
      .judgeResult(result)
      .config(canaryConfig)
      .canaryExecutionRequest(canaryExecutionRequest)
      .build();

    storageService.storeObject(resolvedStorageAccountName, ObjectType.CANARY_RESULT, canaryJudgeResultId, canaryResult);

    Map<String, Object> outputs =
      ImmutableMap.<String, Object>builder()
        .put("canaryJudgeResultId", canaryJudgeResultId)
        .put("result", result)
        .build();

    return new TaskResult(ExecutionStatus.SUCCEEDED, outputs);
  }
}
