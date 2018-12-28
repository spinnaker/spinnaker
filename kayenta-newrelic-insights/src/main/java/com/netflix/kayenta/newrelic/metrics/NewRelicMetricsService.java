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
import com.netflix.kayenta.newrelic.security.NewRelicCredentials;
import com.netflix.kayenta.newrelic.security.NewRelicNamedAccountCredentials;
import com.netflix.kayenta.newrelic.service.NewRelicRemoteService;
import com.netflix.kayenta.newrelic.service.NewRelicTimeSeries;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.spectator.api.Registry;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

@Builder
@Slf4j
public class NewRelicMetricsService implements MetricsService {

  @NotNull
  @Singular
  @Getter
  private List<String> accountNames;

  @Autowired
  private final AccountCredentialsRepository accountCredentialsRepository;

  @Autowired
  private final Registry registry;

  @Override
  public String getType() {
    return "newrelic";
  }

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  @Override
  public String buildQuery(String metricsAccountName, CanaryConfig canaryConfig, CanaryMetricConfig canaryMetricConfig,
    CanaryScope canaryScope) throws IOException {

    NewRelicCanaryScope newRelicCanaryScope = (NewRelicCanaryScope) canaryScope;

    NewRelicCanaryMetricSetQueryConfig queryConfig =
      (NewRelicCanaryMetricSetQueryConfig) canaryMetricConfig.getQuery();

    // Example for a query produced by this class:
    // SELECT count(*) FROM Transaction TIMESERIES MAX SINCE 1540382125 UNTIL 1540392125
    // WHERE appName LIKE 'PROD - Service' AND httpResponseCode >= '500'

    // we expect the full select statement to be in the config
    StringBuilder query = new StringBuilder(queryConfig.getSelect());
    query.append(" TIMESERIES ");

    if (newRelicCanaryScope.getStep() == 0) {
      query.append("MAX");
    } else {
      query.append(newRelicCanaryScope.getStep());
      query.append(" seconds");
    }

    query.append(" SINCE ");
    query.append(newRelicCanaryScope.getStart().getEpochSecond());
    query.append(" UNTIL ");
    query.append(newRelicCanaryScope.getEnd().getEpochSecond());
    query.append(" WHERE ");
    if (!StringUtils.isEmpty(queryConfig.getQ())) {
      query.append(queryConfig.getQ());
      query.append(" AND ");
    }

    for (Map.Entry<String, String> extendedParam : newRelicCanaryScope.getExtendedScopeParams().entrySet()) {
      if (extendedParam.getKey().startsWith("_")) {
        continue;
      }
      query.append(extendedParam.getKey());
      query.append(" LIKE ");
      query.append('\'');
      query.append(extendedParam.getValue());
      query.append('\'');
      query.append(" AND ");
    }

    query.append(newRelicCanaryScope.getScopeKey());
    query.append(" LIKE '");
    query.append(newRelicCanaryScope.getScope());
    query.append('\'');
    return query.toString();
  }

  @Override
  public List<MetricSet> queryMetrics(String accountName,
    CanaryConfig canaryConfig,
    CanaryMetricConfig canaryMetricConfig, CanaryScope canaryScope)
    throws IOException {
    NewRelicNamedAccountCredentials accountCredentials =
      (NewRelicNamedAccountCredentials) accountCredentialsRepository
        .getOne(accountName)
        .orElseThrow(() -> new IllegalArgumentException(
          "Unable to resolve account " + accountName + "."));

    NewRelicCredentials credentials = accountCredentials.getCredentials();
    NewRelicRemoteService remoteService = accountCredentials.getNewRelicRemoteService();

    NewRelicTimeSeries timeSeries = remoteService.getTimeSeries(
      credentials.getApiKey(),
      credentials.getApplicationKey(),
      buildQuery(accountName, canaryConfig, canaryMetricConfig, canaryScope).toString()
    );

    Instant begin =
      Instant.ofEpochMilli(timeSeries.getMetadata().getBeginTimeMillis());
    Instant end =
      Instant.ofEpochMilli(timeSeries.getMetadata().getEndTimeMillis());

    Duration stepDuration = Duration.ofSeconds(canaryScope.getStep());
    if (stepDuration.isZero()) {
      stepDuration = calculateStepDuration(timeSeries);
    }

    return Arrays.asList(
      MetricSet.builder()
        .name(canaryMetricConfig.getName())
        .startTimeMillis(begin.toEpochMilli())
        .startTimeIso(begin.toString())
        .stepMillis(stepDuration.toMillis())
        .endTimeMillis(end.toEpochMilli())
        .endTimeIso(end.toString())
        .values(timeSeries.getDataPoints().collect(Collectors.toList()))
        .build()
    );
  }

  /**
   * identifies the stepDuration based on the timeseries. With 'TIMESERIES MAX' New Relic returns the maximum possible
   * resolution we need to determine the step size.
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
