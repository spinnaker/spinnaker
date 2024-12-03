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

package com.netflix.kayenta.stackdriver.controllers;

import static com.netflix.kayenta.canary.util.FetchControllerUtils.determineDefaultProperty;

import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.providers.metrics.StackdriverCanaryMetricSetQueryConfig;
import com.netflix.kayenta.metrics.SynchronousQueryProcessor;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.stackdriver.canary.StackdriverCanaryScopeFactory;
import com.netflix.kayenta.stackdriver.config.StackdriverConfigurationTestControllerDefaultProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fetch/stackdriver")
@Slf4j
public class StackdriverFetchController {

  private final AccountCredentialsRepository accountCredentialsRepository;
  private final SynchronousQueryProcessor synchronousQueryProcessor;
  private final StackdriverConfigurationTestControllerDefaultProperties
      stackdriverConfigurationTestControllerDefaultProperties;

  @Autowired
  public StackdriverFetchController(
      AccountCredentialsRepository accountCredentialsRepository,
      SynchronousQueryProcessor synchronousQueryProcessor,
      StackdriverConfigurationTestControllerDefaultProperties
          stackdriverConfigurationTestControllerDefaultProperties) {
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.synchronousQueryProcessor = synchronousQueryProcessor;
    this.stackdriverConfigurationTestControllerDefaultProperties =
        stackdriverConfigurationTestControllerDefaultProperties;
  }

  @Operation(
      summary =
          "Exercise the Stackdriver Metrics Service directly, without any orchestration or judging")
  @RequestMapping(value = "/query", method = RequestMethod.POST)
  public Map queryMetrics(
      @RequestParam(required = false) final String metricsAccountName,
      @RequestParam(required = false) final String storageAccountName,
      @Parameter(example = "cpu") @RequestParam String metricSetName,
      @Parameter(example = "compute.googleapis.com/instance/cpu/utilization") @RequestParam
          String metricType,
      @RequestParam(required = false) List<String> groupByFields, // metric.label.instance_name
      @RequestParam(required = false) String project,
      @Parameter(
              description =
                  "Used to identify the type of the resource being queried, "
                      + "e.g. aws_ec2_instance, gce_instance.")
          @RequestParam(required = false)
          String resourceType,
      @Parameter(
              description =
                  "The location to use when scoping the query. Valid choices depend on what cloud "
                      + "platform the query relates to (could be a region, a namespace, or something else).")
          @RequestParam(required = false)
          String location,
      @Parameter(
              description =
                  "The name of the resource to use when scoping the query. "
                      + "The most common use-case is to provide a server group name.")
          @RequestParam(required = false)
          String scope,
      @Parameter(description = "An ISO format timestamp, e.g.: 2018-02-21T12:48:00Z")
          @RequestParam(required = false)
          String startTimeIso,
      @Parameter(description = "An ISO format timestamp, e.g.: 2018-02-21T12:51:00Z")
          @RequestParam(required = false)
          String endTimeIso,
      @Parameter(example = "60", description = "seconds") @RequestParam Long step,
      @RequestParam(required = false) final String customFilter,
      @Parameter @RequestBody final Map<String, String> extendedScopeParams,
      @Parameter(schema = @Schema(defaultValue = "false")) @RequestParam(required = false)
          final boolean dryRun)
      throws IOException {
    // Apply defaults.
    project =
        determineDefaultProperty(
            project, "project", stackdriverConfigurationTestControllerDefaultProperties);
    resourceType =
        determineDefaultProperty(
            resourceType, "resourceType", stackdriverConfigurationTestControllerDefaultProperties);
    location =
        determineDefaultProperty(
            location, "location", stackdriverConfigurationTestControllerDefaultProperties);
    scope =
        determineDefaultProperty(
            scope, "scope", stackdriverConfigurationTestControllerDefaultProperties);
    startTimeIso =
        determineDefaultProperty(
            startTimeIso, "start", stackdriverConfigurationTestControllerDefaultProperties);
    endTimeIso =
        determineDefaultProperty(
            endTimeIso, "end", stackdriverConfigurationTestControllerDefaultProperties);

    String resolvedMetricsAccountName =
        accountCredentialsRepository
            .getRequiredOneBy(metricsAccountName, AccountCredentials.Type.METRICS_STORE)
            .getName();
    String resolvedStorageAccountName =
        accountCredentialsRepository
            .getRequiredOneBy(storageAccountName, AccountCredentials.Type.OBJECT_STORE)
            .getName();

    StackdriverCanaryMetricSetQueryConfig.StackdriverCanaryMetricSetQueryConfigBuilder
        stackdriverCanaryMetricSetQueryConfigBuilder =
            StackdriverCanaryMetricSetQueryConfig.builder().metricType(metricType);

    if (!StringUtils.isEmpty(resourceType)) {
      stackdriverCanaryMetricSetQueryConfigBuilder.resourceType(resourceType);
    }

    if (!CollectionUtils.isEmpty(groupByFields)) {
      stackdriverCanaryMetricSetQueryConfigBuilder.groupByFields(groupByFields);
    }

    if (!StringUtils.isEmpty(customFilter)) {
      stackdriverCanaryMetricSetQueryConfigBuilder.customInlineTemplate(customFilter);
    }

    CanaryMetricConfig canaryMetricConfig =
        CanaryMetricConfig.builder()
            .name(metricSetName)
            .query(stackdriverCanaryMetricSetQueryConfigBuilder.build())
            .build();

    CanaryScope canaryScope = new CanaryScope();
    canaryScope.setScope(scope);
    canaryScope.setLocation(location);
    canaryScope.setStart(startTimeIso != null ? Instant.parse(startTimeIso) : null);
    canaryScope.setEnd(endTimeIso != null ? Instant.parse(endTimeIso) : null);
    canaryScope.setStep(step);

    if (!StringUtils.isEmpty(project)) {
      extendedScopeParams.put("project", project);
    }

    canaryScope.setExtendedScopeParams(extendedScopeParams);

    CanaryScope stackdriverCanaryScope =
        new StackdriverCanaryScopeFactory().buildCanaryScope(canaryScope);

    return synchronousQueryProcessor.processQueryAndReturnMap(
        resolvedMetricsAccountName,
        resolvedStorageAccountName,
        null,
        canaryMetricConfig,
        0,
        stackdriverCanaryScope,
        dryRun);
  }
}
