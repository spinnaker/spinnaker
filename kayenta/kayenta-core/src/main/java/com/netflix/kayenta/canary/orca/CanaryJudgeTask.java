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
import com.netflix.kayenta.canary.*;
import com.netflix.kayenta.canary.results.CanaryJudgeResult;
import com.netflix.kayenta.metrics.MetricSetPair;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.storage.StorageServiceRepository;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CanaryJudgeTask implements RetryableTask {

  private final AccountCredentialsRepository accountCredentialsRepository;
  private final StorageServiceRepository storageServiceRepository;
  private final List<CanaryJudge> canaryJudges;
  private final ObjectMapper objectMapper;
  private final ExecutionMapper executionMapper;

  @Lazy
  @Autowired
  public CanaryJudgeTask(
      AccountCredentialsRepository accountCredentialsRepository,
      StorageServiceRepository storageServiceRepository,
      List<CanaryJudge> canaryJudges,
      ObjectMapper kayentaObjectMapper,
      ExecutionMapper executionMapper) {
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.storageServiceRepository = storageServiceRepository;
    this.canaryJudges = canaryJudges;
    this.objectMapper = kayentaObjectMapper;
    this.executionMapper = executionMapper;
  }

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

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    Map<String, Object> context = stage.getContext();
    String storageAccountName = (String) context.get("storageAccountName");
    String resolvedStorageAccountName =
        accountCredentialsRepository
            .getRequiredOneBy(storageAccountName, AccountCredentials.Type.OBJECT_STORE)
            .getName();
    String metricSetPairListId = (String) context.get("metricSetPairListId");
    Map<String, String> orchestratorScoreThresholdsMap =
        (Map<String, String>) context.get("orchestratorScoreThresholds");
    CanaryClassifierThresholdsConfig orchestratorScoreThresholds =
        objectMapper.convertValue(
            orchestratorScoreThresholdsMap, CanaryClassifierThresholdsConfig.class);
    StorageService storageService =
        storageServiceRepository.getRequiredOne(resolvedStorageAccountName);

    CanaryConfig canaryConfig = executionMapper.getCanaryConfig(stage.getExecution());
    List<MetricSetPair> metricSetPairList =
        storageService.loadObject(
            resolvedStorageAccountName, ObjectType.METRIC_SET_PAIR_LIST, metricSetPairListId);
    CanaryJudgeConfig canaryJudgeConfig = canaryConfig.getJudge();
    CanaryJudge canaryJudge = null;

    if (canaryJudgeConfig != null) {
      String overrideJudgeName = (String) context.get("overrideJudgeName");
      String judgeName =
          StringUtils.isNotEmpty(overrideJudgeName)
              ? overrideJudgeName
              : canaryJudgeConfig.getName();

      if (!StringUtils.isEmpty(judgeName)) {
        canaryJudge =
            canaryJudges.stream()
                .filter(c -> c.getName().equals(judgeName))
                .findFirst()
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "Unable to resolve canary judge '" + judgeName + "'."));
      }
    }

    if (canaryJudge == null) {
      canaryJudge = canaryJudges.get(0);
    }

    CanaryJudgeResult result =
        canaryJudge.judge(canaryConfig, orchestratorScoreThresholds, metricSetPairList);

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).output("result", result).build();
  }
}
