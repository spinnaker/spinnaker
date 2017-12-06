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

package com.netflix.kayenta.prometheus.metrics;

import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.providers.PrometheusCanaryMetricSetQueryConfig;
import com.netflix.kayenta.metrics.MetricSet;
import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.prometheus.model.PrometheusResults;
import com.netflix.kayenta.prometheus.security.PrometheusNamedAccountCredentials;
import com.netflix.kayenta.prometheus.service.PrometheusRemoteService;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Builder
@Slf4j
public class PrometheusMetricsService implements MetricsService {

  @NotNull
  private String scopeLabel;

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
    return "prometheus";
  }

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  private StringBuilder addScopeFilter(StringBuilder queryBuilder, String scope, PrometheusCanaryMetricSetQueryConfig queryConfig) {
    List<String> filters = queryConfig.getLabelBindings();
    if (filters == null) {
      filters = new ArrayList<String>();
    }

    if (scope != null && (!scope.isEmpty() || !scope.equals(".*"))) {
      filters.add(scopeLabel + "=~\"" + scope + "\"");
    }

    if (!filters.isEmpty()) {
      String sep = "";
      queryBuilder.append('{');
      for (String binding : filters) {
        queryBuilder.append(sep);
        queryBuilder.append(binding);
        sep = ",";
      }
      queryBuilder.append('}');
    }

    return queryBuilder;
  }

  private StringBuilder addRateQuery(StringBuilder queryBuilder, PrometheusCanaryMetricSetQueryConfig queryConfig) {
    String period = queryConfig.getAggregationPeriod();
    if (period != null && !period.trim().isEmpty()) {
      queryBuilder.insert(0, "rate(");
      queryBuilder.append("[" + period + "])");
    }
    return queryBuilder;
  }

  private StringBuilder addSumQuery(StringBuilder queryBuilder, PrometheusCanaryMetricSetQueryConfig queryConfig) {
    List<String> sumByFields = queryConfig.getSumByFields();
    if (sumByFields != null && !sumByFields.isEmpty()) {
        queryBuilder.insert(0, "sum (");
        queryBuilder.append(") by (");
        String sep = "";
        for (String elem : sumByFields) {
          queryBuilder.append(sep);
          queryBuilder.append(elem);
          sep = ",";
        }
        queryBuilder.append(")");
    }
    return queryBuilder;
  }

  @Override
  public List<MetricSet> queryMetrics(String accountName,
                                      CanaryMetricConfig canaryMetricConfig,
                                      CanaryScope canaryScope) throws IOException {
    PrometheusNamedAccountCredentials credentials = (PrometheusNamedAccountCredentials)accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));
    PrometheusRemoteService prometheusRemoteService = credentials.getPrometheusRemoteService();
    PrometheusCanaryMetricSetQueryConfig queryConfig = (PrometheusCanaryMetricSetQueryConfig)canaryMetricConfig.getQuery();

    StringBuilder queryBuilder = new StringBuilder(queryConfig.getMetricName());
    queryBuilder = addScopeFilter(queryBuilder, canaryScope.getScope(), queryConfig);
    queryBuilder = addRateQuery(queryBuilder, queryConfig);
    queryBuilder = addSumQuery(queryBuilder, queryConfig);

    long startTime = registry.clock().monotonicTime();
    List<PrometheusResults> prometheusResultsList;

    try {
      prometheusResultsList = prometheusRemoteService.fetch(queryBuilder.toString(),
                                                            canaryScope.getStart().toString(),
                                                            canaryScope.getEnd().toString(),
                                                            canaryScope.getStep());
    } finally {
      long endTime = registry.clock().monotonicTime();
      // TODO(ewiseblatt/duftler): Add appropriate tags.
      Id prometheusFetchTimerId = registry.createId("prometheus.fetchTime");

      registry.timer(prometheusFetchTimerId).record(endTime - startTime, TimeUnit.NANOSECONDS);
    }

    List<MetricSet> metricSetList = new ArrayList<>();

    for (PrometheusResults prometheusResults : prometheusResultsList) {
        Instant responseStartTimeInstant = Instant.ofEpochSecond(prometheusResults.getStartSecs());
      MetricSet.MetricSetBuilder metricSetBuilder =
        MetricSet.builder()
          .name(canaryMetricConfig.getName())
          .startTimeMillis(TimeUnit.NANOSECONDS.toMillis(prometheusResults.getStartSecs()))
          .startTimeIso(responseStartTimeInstant.toString())
          .stepMillis(TimeUnit.SECONDS.toMillis(prometheusResults.getStepSecs()))
          .values(prometheusResults.getValues());

      Map<String, String> tags = prometheusResults.getTags();

      if (tags != null) {
        metricSetBuilder.tags(tags);
      }

      metricSetList.add(metricSetBuilder.build());
    }

    return metricSetList;
  }
}
