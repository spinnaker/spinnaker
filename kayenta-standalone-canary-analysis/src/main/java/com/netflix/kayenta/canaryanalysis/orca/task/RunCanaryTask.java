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
 */

package com.netflix.kayenta.canaryanalysis.orca.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.kayenta.canary.CanaryAdhocExecutionRequest;
import com.netflix.kayenta.canary.CanaryExecutionRequest;
import com.netflix.kayenta.canary.CanaryExecutionResponse;
import com.netflix.kayenta.canary.CanaryScopePair;
import com.netflix.kayenta.canary.ExecutionMapper;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.kayenta.canaryanalysis.domain.RunCanaryContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

import java.util.Optional;

import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED;

/**
 * Orca Task that tells Kayenta to execute a canary analysis / judgement
 */
@Component
@Slf4j
public class RunCanaryTask implements Task {

  private final String AD_HOC = "ad-hoc";

  private final AccountCredentialsRepository accountCredentialsRepository;
  private final ExecutionMapper executionMapper;
  private final ObjectMapper kayentaObjectMapper;

  @Autowired
  public RunCanaryTask(AccountCredentialsRepository accountCredentialsRepository,
                       ExecutionMapper executionMapper,
                       ObjectMapper kayentaObjectMapper) {

    this.accountCredentialsRepository = accountCredentialsRepository;
    this.executionMapper = executionMapper;
    this.kayentaObjectMapper = kayentaObjectMapper;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    RunCanaryContext context = kayentaObjectMapper
        .convertValue(stage.getContext(), RunCanaryContext.class);

    String metricsAccount = context.getMetricsAccountName();
    String storageAccount = context.getStorageAccountName();

    CanaryAdhocExecutionRequest request = new CanaryAdhocExecutionRequest();
    request.setCanaryConfig(context.getCanaryConfig());

    CanaryExecutionRequest executionRequest = CanaryExecutionRequest.builder()
        .scopes(context.getScopes())
        .thresholds(context.getScoreThresholds())
        .siteLocal(context.getSiteLocal())
        .build();

    request.setExecutionRequest(executionRequest);

    CanaryExecutionResponse canaryExecutionResponse;
    try {
      String resolvedMetricsAccountName = CredentialsHelper.resolveAccountByNameOrType(metricsAccount,
          AccountCredentials.Type.METRICS_STORE,
          accountCredentialsRepository);
      String resolvedStorageAccountName = CredentialsHelper.resolveAccountByNameOrType(storageAccount,
          AccountCredentials.Type.OBJECT_STORE,
          accountCredentialsRepository);

      if (request.getCanaryConfig() == null) {
        throw new IllegalArgumentException("canaryConfig must be provided for ad-hoc requests");
      }
      if (request.getExecutionRequest() == null) {
        throw new IllegalArgumentException("executionRequest must be provided for ad-hoc requests");
      }

      canaryExecutionResponse = executionMapper.buildExecution(
          Optional.ofNullable(context.getApplication()).orElse(AD_HOC),
          stage.getExecution().getId(),
          Optional.ofNullable(context.getCanaryConfigId()).orElse(AD_HOC),
          request.getCanaryConfig(),
          null,
          resolvedMetricsAccountName,
          resolvedStorageAccountName,
          request.getExecutionRequest());
    } catch (Exception e) {
      throw new RuntimeException("Failed to initiate canary analysis", e);
    }

    String canaryPipelineExecutionId = canaryExecutionResponse.getCanaryExecutionId();

    // Grab the first scope pair so we can store the start and end times that were used for the judgement.
    CanaryScopePair firstScopePair = context.getScopes().entrySet().stream().findFirst()
        .orElseThrow(() -> new IllegalStateException("There should be at least 1 scope")).getValue();

    return new TaskResult(SUCCEEDED, ImmutableMap.of(
        "canaryPipelineExecutionId", canaryPipelineExecutionId,
        "judgementStartTimeIso", firstScopePair.getControlScope().getStart().toString(),
        "judgementStartTimeMillis", firstScopePair.getControlScope().getStart().toEpochMilli(),
        "judgementEndTimeIso", firstScopePair.getControlScope().getEnd().toString(),
        "judgementEndTimeMillis", firstScopePair.getControlScope().getEnd().toEpochMilli()
    ));
  }
}
