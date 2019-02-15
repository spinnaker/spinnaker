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

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CanaryJudgeTask implements RetryableTask {

  private final AccountCredentialsRepository accountCredentialsRepository;
  private final StorageServiceRepository storageServiceRepository;
  private final List<CanaryJudge> canaryJudges;
  private final ObjectMapper objectMapper;
  private final ExecutionMapper executionMapper;

  @Autowired
  public CanaryJudgeTask(AccountCredentialsRepository accountCredentialsRepository,
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
  public TaskResult execute(@Nonnull Stage stage) {
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

    CanaryConfig canaryConfig = executionMapper.getCanaryConfig(stage.getExecution());
    List<MetricSetPair> metricSetPairList = storageService.loadObject(resolvedStorageAccountName, ObjectType.METRIC_SET_PAIR_LIST, metricSetPairListId);
    CanaryJudgeConfig canaryJudgeConfig = canaryConfig.getJudge();
    CanaryJudge canaryJudge = null;

    if (canaryJudgeConfig != null) {
      String overrideJudgeName = (String)context.get("overrideJudgeName");
      String judgeName = StringUtils.isNotEmpty(overrideJudgeName) ? overrideJudgeName : canaryJudgeConfig.getName();

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

    CanaryJudgeResult result = canaryJudge.judge(canaryConfig, orchestratorScoreThresholds, metricSetPairList);

    Map<String, Object> outputs =
      ImmutableMap.<String, Object>builder()
        .put("result", result)
        .build();

    return new TaskResult(ExecutionStatus.SUCCEEDED, Collections.emptyMap(), outputs);
  }
}
