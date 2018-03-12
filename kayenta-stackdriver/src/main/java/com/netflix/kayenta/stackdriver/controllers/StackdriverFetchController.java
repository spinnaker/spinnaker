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

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.providers.StackdriverCanaryMetricSetQueryConfig;
import com.netflix.kayenta.metrics.SynchronousQueryProcessor;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;
import com.netflix.kayenta.stackdriver.canary.StackdriverCanaryScope;
import com.netflix.kayenta.stackdriver.config.StackdriverConfigurationTestControllerDefaultProperties;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.netflix.kayenta.canary.util.FetchControllerUtils.determineDefaultProperty;

@RestController
@RequestMapping("/fetch/stackdriver")
@Slf4j
public class StackdriverFetchController {

  private final AccountCredentialsRepository accountCredentialsRepository;
  private final SynchronousQueryProcessor synchronousQueryProcessor;
  private final StackdriverConfigurationTestControllerDefaultProperties stackdriverConfigurationTestControllerDefaultProperties;

  @Autowired
  public StackdriverFetchController(AccountCredentialsRepository accountCredentialsRepository,
                                    SynchronousQueryProcessor synchronousQueryProcessor,
                                    StackdriverConfigurationTestControllerDefaultProperties stackdriverConfigurationTestControllerDefaultProperties) {
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.synchronousQueryProcessor = synchronousQueryProcessor;
    this.stackdriverConfigurationTestControllerDefaultProperties = stackdriverConfigurationTestControllerDefaultProperties;
  }

  @ApiOperation(value = "Exercise the Stackdriver Metrics Service directly, without any orchestration or judging")
  @RequestMapping(value = "/query", method = RequestMethod.POST)
  public Map queryMetrics(@RequestParam(required = false) final String metricsAccountName,
                          @RequestParam(required = false) final String storageAccountName,
                          @ApiParam(defaultValue = "cpu") @RequestParam String metricSetName,
                          @ApiParam(defaultValue = "compute.googleapis.com/instance/cpu/utilization") @RequestParam String metricType,
                          @RequestParam(required = false) List<String> groupByFields, // metric.label.instance_name
                          @RequestParam(required = false) String project,
                          @ApiParam(value = "Used to identify the type of the resource being queried, " +
                                            "e.g. aws_ec2_instance, gce_instance.")
                            @RequestParam(required = false) String resourceType,
                          @ApiParam(value = "The region to use when scoping the query. Valid choices depend on what cloud " +
                                            "platform the query relates to.")
                            @RequestParam(required = false) String region,
                          @ApiParam(value = "The name of the resource to use when scoping the query. " +
                                            "The most common use-case is to provide a server group name.")
                            @RequestParam(required = false) String scope,
                          @ApiParam(value = "An ISO format timestamp, e.g.: 2018-02-21T12:48:00Z")
                            @RequestParam(required = false) String startTimeIso,
                          @ApiParam(value = "An ISO format timestamp, e.g.: 2018-02-21T12:51:00Z")
                            @RequestParam(required = false) String endTimeIso,
                          @ApiParam(defaultValue = "60", value = "seconds") @RequestParam Long step,
                          @RequestParam(required = false) final String customFilter,
                          @ApiParam @RequestBody final Map<String, String> extendedScopeParams) throws IOException {
    // Apply defaults.
    project = determineDefaultProperty(project, "project", stackdriverConfigurationTestControllerDefaultProperties);
    resourceType = determineDefaultProperty(resourceType, "resourceType", stackdriverConfigurationTestControllerDefaultProperties);
    region = determineDefaultProperty(region, "region", stackdriverConfigurationTestControllerDefaultProperties);
    scope = determineDefaultProperty(scope, "scope", stackdriverConfigurationTestControllerDefaultProperties);
    startTimeIso = determineDefaultProperty(startTimeIso, "start", stackdriverConfigurationTestControllerDefaultProperties);
    endTimeIso = determineDefaultProperty(endTimeIso, "end", stackdriverConfigurationTestControllerDefaultProperties);

    if (StringUtils.isEmpty(startTimeIso)) {
      throw new IllegalArgumentException("Start time is required.");
    }

    if (StringUtils.isEmpty(endTimeIso)) {
      throw new IllegalArgumentException("End time is required.");
    }

    String resolvedMetricsAccountName = CredentialsHelper.resolveAccountByNameOrType(metricsAccountName,
                                                                                     AccountCredentials.Type.METRICS_STORE,
                                                                                     accountCredentialsRepository);
    String resolvedStorageAccountName = CredentialsHelper.resolveAccountByNameOrType(storageAccountName,
                                                                                     AccountCredentials.Type.OBJECT_STORE,
                                                                                     accountCredentialsRepository);

    StackdriverCanaryMetricSetQueryConfig.StackdriverCanaryMetricSetQueryConfigBuilder stackdriverCanaryMetricSetQueryConfigBuilder =
      StackdriverCanaryMetricSetQueryConfig
        .builder()
        .metricType(metricType);

    if (!CollectionUtils.isEmpty(groupByFields)) {
      stackdriverCanaryMetricSetQueryConfigBuilder.groupByFields(groupByFields);
    }

    if (!StringUtils.isEmpty(customFilter)) {
      stackdriverCanaryMetricSetQueryConfigBuilder.customFilter(customFilter);
    }

    CanaryMetricConfig canaryMetricConfig =
      CanaryMetricConfig
        .builder()
        .name(metricSetName)
        .query(stackdriverCanaryMetricSetQueryConfigBuilder.build())
        .build();

    StackdriverCanaryScope stackdriverCanaryScope = new StackdriverCanaryScope();
    stackdriverCanaryScope.setScope(scope);
    stackdriverCanaryScope.setRegion(region);
    stackdriverCanaryScope.setResourceType(resourceType);
    stackdriverCanaryScope.setStart(Instant.parse(startTimeIso));
    stackdriverCanaryScope.setEnd(Instant.parse(endTimeIso));
    stackdriverCanaryScope.setStep(step);
    stackdriverCanaryScope.setExtendedScopeParams(extendedScopeParams);

    if (!StringUtils.isEmpty(project)) {
      stackdriverCanaryScope.setProject(project);
    }

    String metricSetListId = synchronousQueryProcessor.processQuery(resolvedMetricsAccountName,
                                                                    resolvedStorageAccountName,
                                                                    CanaryConfig.builder().metric(canaryMetricConfig).build(),
                                                                    0,
                                                                    stackdriverCanaryScope);

    return Collections.singletonMap("metricSetListId", metricSetListId);
  }
}
