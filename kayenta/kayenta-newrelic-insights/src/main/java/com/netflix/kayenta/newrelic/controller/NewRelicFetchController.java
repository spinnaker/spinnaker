/*
 * Copyright 2018 Adobe
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

package com.netflix.kayenta.newrelic.controller;

import static com.netflix.kayenta.canary.util.FetchControllerUtils.determineDefaultProperty;
import static com.netflix.kayenta.newrelic.canary.NewRelicCanaryScopeFactory.LOCATION_KEY_KEY;
import static com.netflix.kayenta.newrelic.canary.NewRelicCanaryScopeFactory.SCOPE_KEY_KEY;

import com.netflix.kayenta.metrics.SynchronousQueryProcessor;
import com.netflix.kayenta.newrelic.canary.NewRelicCanaryScope;
import com.netflix.kayenta.newrelic.config.NewRelicConfigurationTestControllerDefaultProperties;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/fetch/newrelic")
@Slf4j
public class NewRelicFetchController {

  private final AccountCredentialsRepository accountCredentialsRepository;
  private final SynchronousQueryProcessor synchronousQueryProcessor;
  private final NewRelicConfigurationTestControllerDefaultProperties
      newrelicConfigurationTestControllerDefaultProperties;

  @Autowired
  public NewRelicFetchController(
      AccountCredentialsRepository accountCredentialsRepository,
      SynchronousQueryProcessor synchronousQueryProcessor,
      NewRelicConfigurationTestControllerDefaultProperties
          newrelicConfigurationTestControllerDefaultProperties) {
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.synchronousQueryProcessor = synchronousQueryProcessor;
    this.newrelicConfigurationTestControllerDefaultProperties =
        newrelicConfigurationTestControllerDefaultProperties;
  }

  @RequestMapping(value = "/query", method = RequestMethod.POST)
  public Map queryMetrics(
      @RequestParam(required = false) final String metricsAccountName,
      @RequestParam(required = false) final String storageAccountName,
      @Parameter(required = true) @Valid @RequestBody NewRelicFetchRequest newRelicFetchRequest,
      @Parameter(
              description =
                  "The scope of the NewRelic query. e.g. autoscaling_group:myapp-prod-v002")
          @RequestParam(required = false)
          String scope,
      @Parameter(description = "The location of the NewRelic query. e.g. us-west-2")
          @RequestParam(required = false)
          String location,
      @Parameter(description = "An ISO format timestamp, e.g.: 2018-03-15T01:23:45Z") @RequestParam
          String start,
      @Parameter(description = "An ISO format timestamp, e.g.: 2018-03-15T01:23:45Z") @RequestParam
          String end,
      @Parameter(example = "60", description = "seconds") @RequestParam Long step,
      @Parameter(schema = @Schema(defaultValue = "0"), description = "canary config metrics index")
          @RequestParam(required = false)
          Integer metricIndex,
      @Parameter(schema = @Schema(defaultValue = "false")) @RequestParam(required = false)
          final boolean dryRun)
      throws IOException {

    // Apply defaults.
    scope =
        determineDefaultProperty(
            scope, "scope", newrelicConfigurationTestControllerDefaultProperties);
    start =
        determineDefaultProperty(
            start, "start", newrelicConfigurationTestControllerDefaultProperties);
    end =
        determineDefaultProperty(end, "end", newrelicConfigurationTestControllerDefaultProperties);

    if (StringUtils.isEmpty(start)) {
      throw new IllegalArgumentException("Start time is required.");
    }

    if (StringUtils.isEmpty(end)) {
      throw new IllegalArgumentException("End time is required.");
    }

    String resolvedMetricsAccountName =
        accountCredentialsRepository
            .getRequiredOneBy(metricsAccountName, AccountCredentials.Type.METRICS_STORE)
            .getName();
    String resolvedStorageAccountName =
        accountCredentialsRepository
            .getRequiredOneBy(storageAccountName, AccountCredentials.Type.OBJECT_STORE)
            .getName();

    NewRelicCanaryScope canaryScope = new NewRelicCanaryScope();
    canaryScope.setScope(scope);

    Optional.ofNullable(newRelicFetchRequest.extendedScopeParams)
        .ifPresent(
            esp -> {
              canaryScope.setLocationKey(esp.getOrDefault(LOCATION_KEY_KEY, null));
              canaryScope.setScopeKey(esp.getOrDefault(SCOPE_KEY_KEY, null));
            });

    canaryScope.setLocation(location);
    canaryScope.setStart(Instant.parse(start));
    canaryScope.setEnd(Instant.parse(end));
    canaryScope.setStep(step);
    canaryScope.setExtendedScopeParams(newRelicFetchRequest.extendedScopeParams);

    return synchronousQueryProcessor.processQueryAndReturnMap(
        resolvedMetricsAccountName,
        resolvedStorageAccountName,
        newRelicFetchRequest.getCanaryConfig(),
        newRelicFetchRequest.getCanaryMetricConfig(),
        Optional.ofNullable(metricIndex).orElse(0),
        canaryScope,
        dryRun);
  }
}
