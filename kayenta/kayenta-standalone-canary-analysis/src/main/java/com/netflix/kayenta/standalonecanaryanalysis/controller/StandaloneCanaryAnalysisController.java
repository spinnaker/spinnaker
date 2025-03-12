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

package com.netflix.kayenta.standalonecanaryanalysis.controller;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.providers.metrics.QueryConfigUtils;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.standalonecanaryanalysis.CanaryAnalysisConfig;
import com.netflix.kayenta.standalonecanaryanalysis.domain.CanaryAnalysisAdhocExecutionRequest;
import com.netflix.kayenta.standalonecanaryanalysis.domain.CanaryAnalysisExecutionRequest;
import com.netflix.kayenta.standalonecanaryanalysis.domain.CanaryAnalysisExecutionResponse;
import com.netflix.kayenta.standalonecanaryanalysis.domain.CanaryAnalysisExecutionStatusResponse;
import com.netflix.kayenta.standalonecanaryanalysis.service.CanaryAnalysisService;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.storage.StorageServiceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for triggering logic that is representative of what happens in the Spinnaker Canary
 * Analysis StageExecution of a pipeline.
 */
@RestController(
    value =
        "Standalone Canary Analysis Controller - endpoints for performing multiple canary judgements over a period of time, past or present")
@RequestMapping("/standalone_canary_analysis")
@Slf4j
public class StandaloneCanaryAnalysisController {

  private final AccountCredentialsRepository accountCredentialsRepository;
  private final CanaryAnalysisService canaryAnalysisService;
  private final StorageServiceRepository storageServiceRepository;

  private final String AD_HOC = "ad-hoc";

  @Autowired
  public StandaloneCanaryAnalysisController(
      AccountCredentialsRepository accountCredentialsRepository,
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
   * @param parentPipelineExecutionId The parent pipeline execution id, if this is being executed as
   *     a StageExecution in another pipeline
   * @param metricsAccountName The account that has the metrics for the application under test
   * @param configurationAccountName The account that has the supplied canary config id
   * @param storageAccountName The account that will be used to store results
   * @param canaryAnalysisExecutionRequest The canary analysis execution request that configures how
   *     the analysis will be performed
   * @param canaryConfigId The id for the canary configuration to use for the analysis execution
   * @return Object with the execution id
   */
  @Operation(
      summary =
          "Initiate a canary analysis execution with multiple canary judgements using a stored canary config")
  @RequestMapping(value = "/{canaryConfigId:.+}", consumes = "application/json", method = POST)
  public CanaryAnalysisExecutionResponse initiateCanaryAnalysis(
      @Parameter(
              description = "The initiating user",
              schema = @Schema(defaultValue = "anonymous"),
              example = "justin.field@example.com")
          @RequestParam(required = false)
          final String user,
      @Parameter(description = "The application under test", example = "examplecanarymicroservice")
          @RequestParam(required = false)
          final String application,
      @Parameter(
              description =
                  "The parent pipeline execution id, if this is being executed as a StageExecution in another pipeline",
              example = "01CYZCD53RBX2KR2Q9GY0218UI")
          @RequestParam(required = false)
          final String parentPipelineExecutionId,
      @Parameter(
              description = "The account that has the metrics for the application under test",
              example = "some-metrics-account")
          @RequestParam(required = false)
          final String metricsAccountName,
      @Parameter(
              description = "The account that has the supplied canary config id",
              example = "some-config-account")
          @RequestParam(required = false)
          final String configurationAccountName,
      @Parameter(
              description = "The account that will be used to store results",
              example = "some-storage-account")
          @RequestParam(required = false)
          final String storageAccountName,
      @Parameter(required = true) @RequestBody
          final CanaryAnalysisExecutionRequest canaryAnalysisExecutionRequest,
      @Parameter(
              description = "The id for the canary configuration to use for the analysis execution")
          @PathVariable
          String canaryConfigId) {

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

    return canaryAnalysisService.initiateCanaryAnalysisExecution(
        CanaryAnalysisConfig.builder()
            .user(Optional.ofNullable(user).orElse("anonymous"))
            .application(Optional.ofNullable(application).orElse(AD_HOC))
            .parentPipelineExecutionId(parentPipelineExecutionId)
            .canaryConfigId(canaryConfigId)
            .executionRequest(canaryAnalysisExecutionRequest)
            .metricsAccountName(resolvedMetricsAccountName)
            .storageAccountName(resolvedStorageAccountName)
            .configurationAccountName(configurationAccountName)
            .canaryConfig(QueryConfigUtils.escapeTemplates(canaryConfig))
            .build());
  }

  /**
   * Initiates a Canary Analysis Execution with the provided canary configuration.
   *
   * @param user The initiating user
   * @param application The application under test
   * @param parentPipelineExecutionId The parent pipeline execution id, if this is being executed as
   *     a StageExecution in another pipeline
   * @param metricsAccountName The account that has the metrics for the application under test
   * @param storageAccountName The account that will be used to store results
   * @param canaryAnalysisAdhocExecutionRequest Wrapper around the canary analysis execution request
   *     that configures how the analysis will be performed and the canary config
   * @return Object with the execution id
   */
  @Operation(
      summary =
          "Initiate an canary analysis execution with multiple canary judgements with the CanaryConfig provided in the request body")
  @RequestMapping(consumes = "application/json", method = POST)
  public CanaryAnalysisExecutionResponse initiateCanaryAnalysisExecutionWithConfig(
      @Parameter(
              description = "The initiating user",
              schema = @Schema(defaultValue = "anonymous"),
              example = "justin.field@example.com")
          @RequestParam(required = false)
          final String user,
      @Parameter(description = "The application under test", example = "examplecanarymicroservice")
          @RequestParam(required = false)
          final String application,
      @Parameter(
              description =
                  "The parent pipeline execution id, if this is being executed as a StageExecution in another pipeline",
              example = "01CYZCD53RBX2KR2Q9GY0218UI")
          @RequestParam(required = false)
          final String parentPipelineExecutionId,
      @Parameter(
              description = "The account that has the metrics for the application under test",
              example = "some-metrics-account")
          @RequestParam(required = false)
          final String metricsAccountName,
      @Parameter(
              description = "The account that will be used to store results",
              example = "some-storage-account")
          @RequestParam(required = false)
          final String storageAccountName,
      @Parameter(required = true) @RequestBody
          final CanaryAnalysisAdhocExecutionRequest canaryAnalysisAdhocExecutionRequest) {

    String resolvedMetricsAccountName =
        accountCredentialsRepository
            .getRequiredOneBy(metricsAccountName, AccountCredentials.Type.METRICS_STORE)
            .getName();
    String resolvedStorageAccountName =
        accountCredentialsRepository
            .getRequiredOneBy(storageAccountName, AccountCredentials.Type.OBJECT_STORE)
            .getName();

    if (canaryAnalysisAdhocExecutionRequest.getCanaryConfig() == null) {
      throw new IllegalArgumentException("canaryConfig must be provided for ad-hoc requests");
    }
    if (canaryAnalysisAdhocExecutionRequest.getExecutionRequest() == null) {
      throw new IllegalArgumentException("executionRequest must be provided for ad-hoc requests");
    }

    return canaryAnalysisService.initiateCanaryAnalysisExecution(
        CanaryAnalysisConfig.builder()
            .user(Optional.ofNullable(user).orElse("anonymous"))
            .application(Optional.ofNullable(application).orElse(AD_HOC))
            .parentPipelineExecutionId(parentPipelineExecutionId)
            .executionRequest(canaryAnalysisAdhocExecutionRequest.getExecutionRequest())
            .metricsAccountName(resolvedMetricsAccountName)
            .storageAccountName(resolvedStorageAccountName)
            .canaryConfig(
                QueryConfigUtils.escapeTemplates(
                    canaryAnalysisAdhocExecutionRequest.getCanaryConfig()))
            .build());
  }

  /**
   * Fetches a Canary Analysis Execution from a supplied id.
   *
   * @param canaryAnalysisExecutionId The id for the Canary Analysis Execution
   * @return The canary analysis execution object that will have the results and status.
   */
  @Operation(summary = "Retrieve status and results for a canary analysis execution")
  @RequestMapping(value = "/{canaryAnalysisExecutionId:.+}", method = GET)
  public CanaryAnalysisExecutionStatusResponse getCanaryAnalysisExecution(
      @Parameter(description = "The id for the Canary Analysis Execution") @PathVariable
          final String canaryAnalysisExecutionId,
      @Parameter(
              description =
                  "The account to use to try and find the execution if not found in the execution repo",
              example = "some-storage-account")
          @RequestParam(required = false)
          final String storageAccountName) {

    return canaryAnalysisService.getCanaryAnalysisExecution(
        canaryAnalysisExecutionId, storageAccountName);
  }
}
