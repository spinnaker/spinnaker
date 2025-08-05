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
import com.netflix.kayenta.canary.*;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.storage.StorageServiceRepository;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/canary")
@Slf4j
public class CanaryController {

  private final String AD_HOC = "ad-hoc";

  private final ExecutionRepository executionRepository;
  private final AccountCredentialsRepository accountCredentialsRepository;
  private final StorageServiceRepository storageServiceRepository;
  private final ExecutionMapper executionMapper;

  @Autowired
  public CanaryController(
      ExecutionRepository executionRepository,
      AccountCredentialsRepository accountCredentialsRepository,
      StorageServiceRepository storageServiceRepository,
      ExecutionMapper executionMapper) {
    this.executionRepository = executionRepository;
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.storageServiceRepository = storageServiceRepository;
    this.executionMapper = executionMapper;
  }

  //
  // Initiate a new canary run.
  //
  // TODO(duftler): Allow for user to be passed in.
  @Operation(summary = "Initiate a canary pipeline")
  @RequestMapping(
      value = "/{canaryConfigId:.+}",
      consumes = "application/json",
      method = RequestMethod.POST)
  public CanaryExecutionResponse initiateCanary(
      @RequestParam(required = false) final String application,
      @RequestParam(required = false) final String parentPipelineExecutionId,
      @RequestParam(required = false) final String metricsAccountName,
      @RequestParam(required = false) final String configurationAccountName,
      @RequestParam(required = false) final String storageAccountName,
      @Parameter @RequestBody final CanaryExecutionRequest canaryExecutionRequest,
      @PathVariable String canaryConfigId)
      throws JsonProcessingException {
    String resolvedMetricsAccountName =
        accountCredentialsRepository
            .getRequiredOneBy(metricsAccountName, AccountCredentials.Type.METRICS_STORE)
            .getName();
    String resolvedStorageAccountName =
        accountCredentialsRepository
            .getRequiredOneBy(storageAccountName, AccountCredentials.Type.OBJECT_STORE)
            .getName();
    String resolvedConfigurationAccountName =
        accountCredentialsRepository
            .getRequiredOneBy(configurationAccountName, AccountCredentials.Type.CONFIGURATION_STORE)
            .getName();

    StorageService configurationService =
        storageServiceRepository.getRequiredOne(resolvedConfigurationAccountName);
    CanaryConfig canaryConfig =
        configurationService.loadObject(
            resolvedConfigurationAccountName, ObjectType.CANARY_CONFIG, canaryConfigId);

    return executionMapper.buildExecution(
        application,
        parentPipelineExecutionId,
        canaryConfigId,
        canaryConfig,
        resolvedConfigurationAccountName,
        resolvedMetricsAccountName,
        resolvedStorageAccountName,
        canaryExecutionRequest);
  }

  //
  // Initiate a new canary run, fully specifying the config and execution request
  //
  // TODO(duftler): Allow for user to be passed in.
  @Operation(summary = "Initiate a canary pipeline with CanaryConfig provided")
  @RequestMapping(consumes = "application/json", method = RequestMethod.POST)
  public CanaryExecutionResponse initiateCanaryWithConfig(
      @RequestParam(required = false) final String application,
      @RequestParam(required = false) final String parentPipelineExecutionId,
      @RequestParam(required = false) final String metricsAccountName,
      @RequestParam(required = false) final String storageAccountName,
      @Parameter @RequestBody final CanaryAdhocExecutionRequest canaryAdhocExecutionRequest)
      throws JsonProcessingException {

    String resolvedMetricsAccountName =
        accountCredentialsRepository
            .getRequiredOneBy(metricsAccountName, AccountCredentials.Type.METRICS_STORE)
            .getName();
    String resolvedStorageAccountName =
        accountCredentialsRepository
            .getRequiredOneBy(storageAccountName, AccountCredentials.Type.OBJECT_STORE)
            .getName();

    if (canaryAdhocExecutionRequest.getCanaryConfig() == null) {
      throw new IllegalArgumentException("canaryConfig must be provided for ad-hoc requests");
    }
    if (canaryAdhocExecutionRequest.getExecutionRequest() == null) {
      throw new IllegalArgumentException("executionRequest must be provided for ad-hoc requests");
    }

    return executionMapper.buildExecution(
        Optional.ofNullable(application).orElse(AD_HOC),
        parentPipelineExecutionId,
        AD_HOC,
        canaryAdhocExecutionRequest.getCanaryConfig(),
        null,
        resolvedMetricsAccountName,
        resolvedStorageAccountName,
        canaryAdhocExecutionRequest.getExecutionRequest());
  }

  //
  // Get the results of a canary run by ID
  //
  @Operation(summary = "Retrieve status and results for a canary run")
  @RequestMapping(value = "/{canaryExecutionId:.+}", method = RequestMethod.GET)
  public CanaryExecutionStatusResponse getCanaryResults(
      @RequestParam(required = false) final String storageAccountName,
      @PathVariable String canaryExecutionId) {
    String resolvedStorageAccountName =
        accountCredentialsRepository
            .getRequiredOneBy(storageAccountName, AccountCredentials.Type.OBJECT_STORE)
            .getName();

    // First look in the online cache.  If nothing is found there, look in our storage for the ID.
    try {
      PipelineExecution pipeline =
          executionRepository.retrieve(ExecutionType.PIPELINE, canaryExecutionId);
      return executionMapper.fromExecution(pipeline);
    } catch (ExecutionNotFoundException e) {
      StorageService storageService =
          storageServiceRepository.getRequiredOne(resolvedStorageAccountName);

      return storageService.loadObject(
          resolvedStorageAccountName, ObjectType.CANARY_RESULT_ARCHIVE, canaryExecutionId);
    }
  }

  @Operation(summary = "Retrieve a list of an application's canary results")
  @RequestMapping(value = "/executions", method = RequestMethod.GET)
  List<CanaryExecutionStatusResponse> getCanaryResultsByApplication(
      @RequestParam(required = false) String application,
      @RequestParam(value = "limit", defaultValue = "20") int limit,
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "statuses", required = false) String statuses,
      @RequestParam(required = false) final String storageAccountName) {
    String resolvedStorageAccountName =
        accountCredentialsRepository
            .getRequiredOneBy(storageAccountName, AccountCredentials.Type.OBJECT_STORE)
            .getName();

    StorageService storageService =
        storageServiceRepository.getRequiredOne(resolvedStorageAccountName);

    if (StringUtils.isEmpty(statuses)) {
      statuses =
          Stream.of(ExecutionStatus.values())
              .map(s -> s.toString())
              .collect(Collectors.joining(","));
    }

    List<String> statusesList =
        Stream.of(statuses.split(","))
            .map(s -> s.trim())
            .filter(s -> !StringUtils.isEmpty(s))
            .collect(Collectors.toList());
    ExecutionRepository.ExecutionCriteria executionCriteria =
        new ExecutionRepository.ExecutionCriteria()
            .setPageSize(limit)
            .setStatuses(statusesList)
            .setPage(page);

    // Users of the ad-hoc endpoint can either omit application or pass 'ad-hoc' explicitly.
    if (StringUtils.isEmpty(application)) {
      application = AD_HOC;
    }

    String canaryPipelineConfigId = application + "-standard-canary-pipeline";
    List<PipelineExecution> executions =
        executionRepository
            .retrievePipelinesForPipelineConfigId(canaryPipelineConfigId, executionCriteria)
            .toList()
            .blockingGet();

    return executions.stream().map(executionMapper::fromExecution).collect(Collectors.toList());
  }
}
