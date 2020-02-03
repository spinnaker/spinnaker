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

package com.netflix.kayenta.newrelic.metrics;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.providers.metrics.NewRelicCanaryMetricSetQueryConfig;
import com.netflix.kayenta.metrics.MetricSet;
import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.newrelic.canary.NewRelicCanaryScope;
import com.netflix.kayenta.newrelic.config.NewRelicScopeConfiguration;
import com.netflix.kayenta.newrelic.security.NewRelicCredentials;
import com.netflix.kayenta.newrelic.security.NewRelicNamedAccountCredentials;
import com.netflix.kayenta.newrelic.service.NewRelicRemoteService;
import com.netflix.kayenta.newrelic.service.NewRelicTimeSeries;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.spectator.api.Registry;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Builder
@Slf4j
public class NewRelicMetricsService implements MetricsService {

  @NotNull @Singular @Getter private List<String> accountNames;

  @Autowired private final AccountCredentialsRepository accountCredentialsRepository;

  @Autowired private final Map<String, NewRelicScopeConfiguration> newrelicScopeConfigurationMap;

  @Autowired private final Registry registry;

  @Autowired private final NewRelicQueryBuilderService queryBuilder;

  @Override
  public String getType() {
    return "newrelic";
  }

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  @Override
  public String buildQuery(
      String metricsAccountName,
      CanaryConfig canaryConfig,
      CanaryMetricConfig canaryMetricConfig,
      CanaryScope canaryScope)
      throws IOException {

    NewRelicScopeConfiguration scopeConfiguration =
        newrelicScopeConfigurationMap.get(metricsAccountName);

    NewRelicCanaryScope newRelicCanaryScope = (NewRelicCanaryScope) canaryScope;

    NewRelicCanaryMetricSetQueryConfig queryConfig =
        (NewRelicCanaryMetricSetQueryConfig) canaryMetricConfig.getQuery();

    return queryBuilder.buildQuery(
        canaryConfig, newRelicCanaryScope, queryConfig, scopeConfiguration);
  }

  @Override
  public List<MetricSet> queryMetrics(
      String accountName,
      CanaryConfig canaryConfig,
      CanaryMetricConfig canaryMetricConfig,
      CanaryScope canaryScope)
      throws IOException {
    NewRelicNamedAccountCredentials accountCredentials =
        accountCredentialsRepository.getRequiredOne(accountName);

    NewRelicCredentials credentials = accountCredentials.getCredentials();
    NewRelicRemoteService remoteService = accountCredentials.getNewRelicRemoteService();

    String query = buildQuery(accountName, canaryConfig, canaryMetricConfig, canaryScope);

    NewRelicTimeSeries timeSeries =
        remoteService.getTimeSeries(
            credentials.getApiKey(), credentials.getApplicationKey(), query);

    Instant begin = Instant.ofEpochMilli(timeSeries.getMetadata().getBeginTimeMillis());
    Instant end = Instant.ofEpochMilli(timeSeries.getMetadata().getEndTimeMillis());

    Duration stepDuration = Duration.ofSeconds(canaryScope.getStep());
    if (stepDuration.isZero()) {
      stepDuration = calculateStepDuration(timeSeries);
    }

    return Collections.singletonList(
        MetricSet.builder()
            .name(canaryMetricConfig.getName())
            .startTimeMillis(begin.toEpochMilli())
            .startTimeIso(begin.toString())
            .stepMillis(stepDuration.toMillis())
            .endTimeMillis(end.toEpochMilli())
            .endTimeIso(end.toString())
            .values(timeSeries.getDataPoints().collect(Collectors.toList()))
            .attribute("query", query)
            .build());
  }

  /**
   * identifies the stepDuration based on the timeseries. With 'TIMESERIES MAX' New Relic returns
   * the maximum possible resolution we need to determine the step size.
   *
   * @param timeSeries to identify stepsize for
   * @return step size
   */
  private Duration calculateStepDuration(NewRelicTimeSeries timeSeries) {
    Long firstTimestamp = null;
    for (NewRelicTimeSeries.NewRelicSeriesEntry entry : timeSeries.getTimeSeries()) {
      if (firstTimestamp == null) {
        // get first
        firstTimestamp = entry.getBeginTimeSeconds();
      } else {
        // get next which differs from first
        if (!firstTimestamp.equals(entry.getBeginTimeSeconds())) {
          return Duration.ofSeconds(entry.getBeginTimeSeconds() - firstTimestamp);
        }
      }
    }
    return Duration.ZERO;
  }
}
