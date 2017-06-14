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

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryJudge;
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
import java.util.Optional;

@Component
public class CanaryJudgeTask implements RetryableTask {

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository;

  @Autowired
  StorageServiceRepository storageServiceRepository;

  @Autowired
  CanaryJudge canaryJudge;

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
    StorageService storageService =
      storageServiceRepository
        .getOne(resolvedAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No storage service was configured; unable to load metric set lists."));

    CanaryConfig canaryConfig = storageService.loadObject(resolvedAccountName, ObjectType.CANARY_CONFIG, canaryConfigId.toLowerCase());
    List<MetricSetPair> metricSetPairList = storageService.loadObject(resolvedAccountName, ObjectType.METRIC_SET_PAIR_LIST, metricSetPairListId);
    float canaryScore = canaryJudge.judge(canaryConfig, metricSetPairList);
    Map outputs = Collections.singletonMap("canaryScore", canaryScore);

    return new TaskResult(ExecutionStatus.SUCCEEDED, outputs);
  }
}
