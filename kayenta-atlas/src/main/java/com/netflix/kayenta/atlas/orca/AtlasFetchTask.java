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

package com.netflix.kayenta.atlas.orca;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.atlas.canary.AtlasCanaryScope;
import com.netflix.kayenta.atlas.config.AtlasConfigurationProperties;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.metrics.SynchronousQueryProcessor;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;
import com.netflix.kayenta.storage.StorageServiceRepository;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class AtlasFetchTask implements RetryableTask {

  @Autowired
  ObjectMapper kayentaObjectMapper;

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository;

  @Autowired
  StorageServiceRepository storageServiceRepository;

  @Autowired
  SynchronousQueryProcessor synchronousQueryProcessor;

  @Autowired
  AtlasConfigurationProperties atlasConfigurationProperties;

  @Override
  public long getBackoffPeriod() {
    return Duration.ofSeconds(2).toMillis();
  }

  @Override
  public long getTimeout() {
    return Duration.ofMinutes(atlasConfigurationProperties.getStageTimeoutMinutes()).toMillis();
  }

  @Override
  public long getDynamicBackoffPeriod(Duration taskDuration) {
    int numZeros = Long.numberOfLeadingZeros(taskDuration.getSeconds());
    int floorLog = 63 - numZeros;
    // If the first iteration fails quickly, we still want a one second backoff period.
    int exponent = Math.max(floorLog, 0);
    int backoffPeriodSeconds = Math.min(atlasConfigurationProperties.getMaxBackoffPeriodSeconds(), (int)Math.pow(2, exponent));

    return Duration.ofSeconds(backoffPeriodSeconds).toMillis();
  }

  @Override
  public TaskResult execute(Stage stage) {
    Map<String, Object> context = stage.getContext();
    String metricsAccountName = (String)context.get("metricsAccountName");
    String storageAccountName = (String)context.get("storageAccountName");
    Map<String, Object> canaryConfigMap = (Map<String, Object>)context.get("canaryConfig");
    CanaryConfig canaryConfig = kayentaObjectMapper.convertValue(canaryConfigMap, CanaryConfig.class);
    String scopeJson = (String)stage.getContext().get("atlasCanaryScope");
    AtlasCanaryScope atlasCanaryScope;
    try {
      atlasCanaryScope = kayentaObjectMapper.readValue(scopeJson, AtlasCanaryScope.class);
    } catch (IOException e) {
      log.error("Unable to parse JSON scope: " + scopeJson, e);
      throw new RuntimeException(e);
    }
    String resolvedMetricsAccountName = CredentialsHelper.resolveAccountByNameOrType(metricsAccountName,
                                                                                     AccountCredentials.Type.METRICS_STORE,
                                                                                     accountCredentialsRepository);
    String resolvedStorageAccountName = CredentialsHelper.resolveAccountByNameOrType(storageAccountName,
                                                                                     AccountCredentials.Type.OBJECT_STORE,
                                                                                     accountCredentialsRepository);
    try {
      List<String> metricSetListIds = synchronousQueryProcessor.processQuery(resolvedMetricsAccountName,
                                                                             resolvedStorageAccountName,
                                                                             canaryConfig.getMetrics(),
                                                                             atlasCanaryScope);

      Map outputs = Collections.singletonMap("metricSetListIds", metricSetListIds);

      return new TaskResult(ExecutionStatus.SUCCEEDED, outputs);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
