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
import com.netflix.kayenta.canary.CanaryClassifierThresholdsConfig;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryJudge;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CombinedCanaryResultStrategy;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    String resolvedAccountName = CredentialsHelper.resolveAccountByNameOrType(storageAccountName,
                                                                              AccountCredentials.Type.OBJECT_STORE,
                                                                              accountCredentialsRepository);
    String canaryConfigId = (String)context.get("canaryConfigId");
    String metricSetPairListId = (String)context.get("metricSetPairListId");
    Map<String, String> orchestratorScoreThresholdsMap = (Map<String, String>)context.get("orchestratorScoreThresholds");
    CombinedCanaryResultStrategy combinedCanaryResultStrategy = CombinedCanaryResultStrategy.valueOf((String)context.get("combinedCanaryResultStrategy"));
    CanaryClassifierThresholdsConfig orchestratorScoreThresholds = objectMapper.convertValue(orchestratorScoreThresholdsMap,
                                                                                             CanaryClassifierThresholdsConfig.class);
    StorageService storageService =
      storageServiceRepository
        .getOne(resolvedAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No storage service was configured; unable to load metric set lists."));

    CanaryConfig canaryConfig = storageService.loadObject(resolvedAccountName, ObjectType.CANARY_CONFIG, canaryConfigId.toLowerCase());
    List<MetricSetPair> metricSetPairList = storageService.loadObject(resolvedAccountName, ObjectType.METRIC_SET_PAIR_LIST, metricSetPairListId);
    List<CanaryMetricConfig> metricConfigList = canaryConfig.getMetrics();
    CanaryJudge canaryJudge = null;

    if (metricConfigList != null && metricConfigList.size() > 0) {
      // TODO(duftler): We're just considering the judge reference in the first metric analysis configuration here. Should we be considering each one?
      Map<String, Map> analysisConfigurations = metricConfigList.get(0).getAnalysisConfigurations();

      if (analysisConfigurations != null && analysisConfigurations.containsKey("canary")) {
        Map canaryAnalysisConfiguration = analysisConfigurations.get("canary");

        if (canaryAnalysisConfiguration != null && canaryAnalysisConfiguration.containsKey("judge")) {
          String judgeName = (String)canaryAnalysisConfiguration.get("judge");

          canaryJudge =
            canaryJudges
              .stream()
              .filter(c -> c.getName().equals(judgeName))
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("Unable to resolve canary judge '" + judgeName + "'."));
        }
      }
    }

    if (canaryJudge == null) {
      canaryJudge = canaryJudges.get(0);
    }

    CanaryJudgeResult result = canaryJudge.judge(canaryConfig,
                                                 combinedCanaryResultStrategy,
                                                 orchestratorScoreThresholds,
                                                 metricSetPairList);
    Map<String, CanaryJudgeResult> outputs = Collections.singletonMap("result", result);

    return new TaskResult(ExecutionStatus.SUCCEEDED, outputs);
  }
}
