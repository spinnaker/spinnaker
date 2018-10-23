/*
 * Copyright (c) 2018 Nike, inc.
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
 *
 */

package com.netflix.kayenta.signalfx.orca;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.metrics.SynchronousQueryProcessor;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;
import com.netflix.kayenta.signalfx.canary.SignalFxCanaryScope;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@Component
@Slf4j
public class SignalFxFetchTask implements RetryableTask {

  private final ObjectMapper kayentaObjectMapper;
  private final AccountCredentialsRepository accountCredentialsRepository;
  private final SynchronousQueryProcessor synchronousQueryProcessor;

  @Autowired
  public SignalFxFetchTask(ObjectMapper kayentaObjectMapper,
                           AccountCredentialsRepository accountCredentialsRepository,
                           SynchronousQueryProcessor synchronousQueryProcessor) {

    this.kayentaObjectMapper = kayentaObjectMapper;
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.synchronousQueryProcessor = synchronousQueryProcessor;
  }

  @Override
  public long getBackoffPeriod() {
    return Duration.ofSeconds(2).toMillis();
  }

  @Override
  public long getTimeout() {
    return Duration.ofMinutes(2).toMillis();
  }

  @SuppressWarnings("Duplicates")
  // Seems to be the pattern in the code base to have this code duplicated, I assume to prevent independent implementations from being tightly coupled,
  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    Map<String, Object> context = stage.getContext();
    SignalFxCanaryScope canaryScope;
    try {
      canaryScope = kayentaObjectMapper.readValue((String) stage.getContext().get("canaryScope"), SignalFxCanaryScope.class);
    } catch (IOException e) {
      log.warn("Unable to parse JSON scope", e);
      throw new RuntimeException(e);
    }

    String resolvedMetricsAccountName = CredentialsHelper.resolveAccountByNameOrType(
        (String) context.get("metricsAccountName"),
        AccountCredentials.Type.METRICS_STORE,
        accountCredentialsRepository
    );

    String resolvedStorageAccountName = CredentialsHelper.resolveAccountByNameOrType(
        (String) context.get("storageAccountName"),
        AccountCredentials.Type.OBJECT_STORE,
        accountCredentialsRepository
    );

    return synchronousQueryProcessor.executeQueryAndProduceTaskResult(
        resolvedMetricsAccountName,
        resolvedStorageAccountName,
        kayentaObjectMapper.convertValue(context.get("canaryConfig"), CanaryConfig.class),
        (Integer) stage.getContext().get("metricIndex"),
        canaryScope
    );
  }
}
