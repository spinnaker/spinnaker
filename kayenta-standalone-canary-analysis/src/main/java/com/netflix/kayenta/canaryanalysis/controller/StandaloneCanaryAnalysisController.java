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

package com.netflix.kayenta.canaryanalysis.controller;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canaryanalysis.domain.CanaryAnalysisAdhocExecutionRequest;
import com.netflix.kayenta.canaryanalysis.domain.CanaryAnalysisConfig;
import com.netflix.kayenta.canaryanalysis.domain.CanaryAnalysisExecutionRequest;
import com.netflix.kayenta.canaryanalysis.domain.CanaryAnalysisExecutionResponse;
import com.netflix.kayenta.canaryanalysis.domain.CanaryAnalysisExecutionStatusResponse;
import com.netflix.kayenta.canaryanalysis.service.CanaryAnalysisService;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.storage.StorageServiceRepository;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

/**
 * Controller for triggering logic that is representative of what happens in the Spinnaker Canary Analysis Stage of a pipeline.
 */
@RestController(value = "Standalone Canary Analysis Controller - endpoints for performing multiple canary judgements over a period of time, past or present")
@RequestMapping("/standalone_canary_analysis")
@Slf4j
public class StandaloneCanaryAnalysisController {

  private final AccountCredentialsRepository accountCredentialsRepository;
  private final CanaryAnalysisService canaryAnalysisService;
  private final StorageServiceRepository storageServiceRepository;

  private final String AD_HOC = "ad-hoc";

  @Autowired
  public StandaloneCanaryAnalysisController(AccountCredentialsRepository accountCredentialsRepository,
                                            CanaryAnalysisService canaryAnalysisService,
                                            StorageServiceRepository storageServiceRepository) {

    this.accountCredentialsRepository = accountCredentialsRepository;
    this.canaryAnalysisService = canaryAnalysisService;
    this.storageServiceRepository = storageServiceRepository;
  }

  /**
   * Initiate a Canary Analysis Execution using a stored canary configuration.
   *
   * @param user The initiating user
   * @param application The application under test
   * @param parentPipelineExecutionId The parent pipeline execution id, if this is being executed as a stage in another pipeline
   * @param metricsAccountName The account that has the metrics for the application under test
   * @param configurationAccountName The account that has the supplied canary config id
   * @param storageAccountName The account that will be used to store results
   * @param canaryAnalysisExecutionRequest The canary analysis execution request that configures how the analysis will be performed
   * @param canaryConfigId The id for the canary configuration to use for the analysis execution
   * @return Object with the execution id
   */
  @ApiOperation(value = "Initiate a canary analysis execution with multiple canary judgements using a stored canary config")
  @RequestMapping(value = "/{canaryConfigId:.+}", consumes = "application/json", method = POST)
  public CanaryAnalysisExecutionResponse initiateCanaryAnalysis(
      @ApiParam(value = "The initiating user", defaultValue = "anonymous", example = "justin.field@example.com")
      @RequestParam(required = false) final String user,

      @ApiParam(value = "The application under test", example = "examplecanarymicroservice")
      @RequestParam(required = false) final String application,

      @ApiParam(
          value = "The parent pipeline execution id, if this is being executed as a stage in another pipeline",
          example = "01CYZCD53RBX2KR2Q9GY0218UI")
      @RequestParam(required = false) final String parentPipelineExecutionId,

      @ApiParam(value = "The account that has the metrics for the application under test", example = "some-metrics-account")
      @RequestParam(required = false) final String metricsAccountName,

      @ApiParam(value = "The account that has the supplied canary config id", example = "some-config-account")
      @RequestParam(required = false) final String configurationAccountName,

      @ApiParam(value = "The account that will be used to store results", example = "some-storage-account")
      @RequestParam(required = false) final String storageAccountName,

      @ApiParam(required = true)  @RequestBody final CanaryAnalysisExecutionRequest canaryAnalysisExecutionRequest,

      @ApiParam(value = "The id for the canary configuration to use for the analysis execution")
      @PathVariable String canaryConfigId) {

    String resolvedMetricsAccountName = CredentialsHelper.resolveAccountByNameOrType(metricsAccountName,
        AccountCredentials.Type.METRICS_STORE,
        accountCredentialsRepository);
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

    return canaryAnalysisService.initiateCanaryAnalysisExecution(
        CanaryAnalysisConfig.builder()
            .user(Optional.ofNullable(application).orElse("anonymous"))
            .application(Optional.ofNullable(application).orElse(AD_HOC))
            .parentPipelineExecutionId(parentPipelineExecutionId)
            .canaryConfigId(canaryConfigId)
            .executionRequest(canaryAnalysisExecutionRequest)
            .metricsAccountName(resolvedMetricsAccountName)
            .storageAccountName(resolvedStorageAccountName)
            .configurationAccountName(configurationAccountName)
            .canaryConfig(canaryConfig)
            .build());
  }

  /**
   * Initiates a Canary Analysis Execution with the provided canary configuration.
   *
   * @param user The initiating user
   * @param application The application under test
   * @param parentPipelineExecutionId The parent pipeline execution id, if this is being executed as a stage in another pipeline
   * @param metricsAccountName The account that has the metrics for the application under test
   * @param storageAccountName The account that will be used to store results
   * @param canaryAnalysisAdhocExecutionRequest Wrapper around the canary analysis execution request that configures
   *                                            how the analysis will be performed and the canary config
   * @return Object with the execution id
   */
  @ApiOperation(value = "Initiate an canary analysis execution with multiple canary judgements with the CanaryConfig provided in the request body")
  @RequestMapping(consumes = "application/json", method = POST)
  public CanaryAnalysisExecutionResponse initiateCanaryAnalysisExecutionWithConfig(
      @ApiParam(value = "The initiating user", defaultValue = "anonymous", example = "justin.field@example.com")
      @RequestParam(required = false) final String user,

      @ApiParam(value = "The application under test", example = "examplecanarymicroservice")
      @RequestParam(required = false) final String application,

      @ApiParam(
          value = "The parent pipeline execution id, if this is being executed as a stage in another pipeline",
          example = "01CYZCD53RBX2KR2Q9GY0218UI")
      @RequestParam(required = false) final String parentPipelineExecutionId,

      @ApiParam(value = "The account that has the metrics for the application under test", example = "some-metrics-account")
      @RequestParam(required = false) final String metricsAccountName,

      @ApiParam(value = "The account that will be used to store results", example = "some-storage-account")
      @RequestParam(required = false) final String storageAccountName,

      @ApiParam(required = true) @RequestBody final CanaryAnalysisAdhocExecutionRequest canaryAnalysisAdhocExecutionRequest) {

    String resolvedMetricsAccountName = CredentialsHelper.resolveAccountByNameOrType(metricsAccountName,
        AccountCredentials.Type.METRICS_STORE,
        accountCredentialsRepository);
    String resolvedStorageAccountName = CredentialsHelper.resolveAccountByNameOrType(storageAccountName,
        AccountCredentials.Type.OBJECT_STORE,
        accountCredentialsRepository);

    if (canaryAnalysisAdhocExecutionRequest.getCanaryConfig() == null) {
      throw new IllegalArgumentException("canaryConfig must be provided for ad-hoc requests");
    }
    if (canaryAnalysisAdhocExecutionRequest.getExecutionRequest() == null) {
      throw new IllegalArgumentException("executionRequest must be provided for ad-hoc requests");
    }

    return canaryAnalysisService.initiateCanaryAnalysisExecution(
        CanaryAnalysisConfig.builder()
            .user(Optional.ofNullable(application).orElse("anonymous"))
            .application(Optional.ofNullable(application).orElse(AD_HOC))
            .parentPipelineExecutionId(parentPipelineExecutionId)
            .executionRequest(canaryAnalysisAdhocExecutionRequest.getExecutionRequest())
            .metricsAccountName(resolvedMetricsAccountName)
            .storageAccountName(resolvedStorageAccountName)
            .canaryConfig(canaryAnalysisAdhocExecutionRequest.getCanaryConfig())
            .build());
  }

  /**
   * Fetches a Canary Analysis Execution from a supplied id.
   *
   * @param canaryAnalysisExecutionId The id for the Canary Analysis Execution
   *
   * @return The canary analysis execution object that will have the results and status.
   */
  @ApiOperation(value = "Retrieve status and results for a canary analysis execution")
  @RequestMapping(value = "/{canaryAnalysisExecutionId:.+}", method = GET)
  public CanaryAnalysisExecutionStatusResponse getCanaryAnalysisExecution(
      @ApiParam(value = "The id for the Canary Analysis Execution")
      @PathVariable String canaryAnalysisExecutionId) {

    return canaryAnalysisService.getCanaryAnalysisExecution(canaryAnalysisExecutionId);
  }
}
