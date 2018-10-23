/*
 * Copyright 2018 Armory, Inc.
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

package com.netflix.kayenta.datadog.metrics;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.providers.metrics.DatadogCanaryMetricSetQueryConfig;
import com.netflix.kayenta.datadog.security.DatadogCredentials;
import com.netflix.kayenta.datadog.security.DatadogNamedAccountCredentials;
import com.netflix.kayenta.datadog.service.DatadogRemoteService;
import com.netflix.kayenta.datadog.service.DatadogTimeSeries;
import com.netflix.kayenta.metrics.MetricSet;
import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.model.DatadogMetricDescriptor;
import com.netflix.kayenta.model.DatadogMetricDescriptorsResponse;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;
import com.netflix.spectator.api.Registry;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Builder
@Slf4j
public class DatadogMetricsService implements MetricsService {
  @NotNull
  @Singular
  @Getter
  private List<String> accountNames;

  @Autowired
  private final AccountCredentialsRepository accountCredentialsRepository;

  @Autowired
  private final Registry registry;

  @Builder.Default
  private List<DatadogMetricDescriptor> metricDescriptorsCache = Collections.emptyList();

  @Override
  public String getType() {
    return "datadog";
  }

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  @Override
  public String buildQuery(String metricsAccountName,
                           CanaryConfig canaryConfig,
                           CanaryMetricConfig canaryMetricConfig,
                           CanaryScope canaryScope) {
    DatadogCanaryMetricSetQueryConfig queryConfig = (DatadogCanaryMetricSetQueryConfig)canaryMetricConfig.getQuery();

    return queryConfig.getMetricName() + "{" + canaryScope.getScope() + "}";
  }

  @Override
  public List<MetricSet> queryMetrics(String accountName, CanaryConfig canaryConfig, CanaryMetricConfig canaryMetricConfig, CanaryScope canaryScope) {
    DatadogNamedAccountCredentials accountCredentials = (DatadogNamedAccountCredentials)accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));

    DatadogCredentials credentials = accountCredentials.getCredentials();
    DatadogRemoteService remoteService = accountCredentials.getDatadogRemoteService();

    if (StringUtils.isEmpty(canaryScope.getStart())) {
      throw new IllegalArgumentException("Start time is required.");
    }

    if (StringUtils.isEmpty(canaryScope.getEnd())) {
      throw new IllegalArgumentException("End time is required.");
    }

    String query = buildQuery(accountName,
                              canaryConfig,
                              canaryMetricConfig,
                              canaryScope);
    DatadogTimeSeries timeSeries = remoteService.getTimeSeries(
      credentials.getApiKey(),
      credentials.getApplicationKey(),
      (int)canaryScope.getStart().getEpochSecond(),
      (int)canaryScope.getEnd().getEpochSecond(),
      query
    );

    List<MetricSet> ret = new ArrayList<MetricSet>();

    for (DatadogTimeSeries.DatadogSeriesEntry series : timeSeries.getSeries()) {
      ret.add(
        MetricSet.builder()
          .name(canaryMetricConfig.getName())
          .startTimeMillis(series.getStart())
          .startTimeIso(Instant.ofEpochMilli(series.getStart()).toString())
          .endTimeMillis(series.getEnd())
          .endTimeIso(Instant.ofEpochMilli(series.getEnd()).toString())
          .stepMillis(series.getInterval() * 1000)
          .values(series.getDataPoints().collect(Collectors.toList()))
          .attribute("query", query)
          .build()
      );
    }

    return ret;
  }

  @Override
  public List<Map> getMetadata(String metricsAccountName, String filter) {
    if (!StringUtils.isEmpty(filter)) {
      String lowerCaseFilter = filter.toLowerCase();

      return metricDescriptorsCache
        .stream()
        .filter(metricDescriptor -> metricDescriptor.getName().toLowerCase().contains(lowerCaseFilter))
        .map(metricDescriptor -> metricDescriptor.getMap())
        .collect(Collectors.toList());
    } else {
      return metricDescriptorsCache
        .stream()
        .map(metricDescriptor -> metricDescriptor.getMap())
        .collect(Collectors.toList());
    }
  }

  @Scheduled(fixedDelayString = "#{@datadogConfigurationProperties.metadataCachingIntervalMS}")
  public void updateMetricDescriptorsCache() {
    Set<AccountCredentials> accountCredentialsSet =
      CredentialsHelper.getAllAccountsOfType(AccountCredentials.Type.METRICS_STORE, accountCredentialsRepository);

    for (AccountCredentials credentials : accountCredentialsSet) {
      if (credentials instanceof DatadogNamedAccountCredentials) {
        DatadogNamedAccountCredentials datadogCredentials = (DatadogNamedAccountCredentials)credentials;
        DatadogRemoteService datadogRemoteService = datadogCredentials.getDatadogRemoteService();
        DatadogCredentials ddCredentials = datadogCredentials.getCredentials();
        // Retrieve all metrics actively reporting in the last hour.
        long from = Instant.now().getEpochSecond() - 60 * 60;
        DatadogMetricDescriptorsResponse datadogMetricDescriptorsResponse =
          datadogRemoteService.getMetrics(ddCredentials.getApiKey(),
                                          ddCredentials.getApplicationKey(),
                                          from);

        if (datadogMetricDescriptorsResponse != null) {
          List<String> metrics = datadogMetricDescriptorsResponse.getMetrics();

          if (!CollectionUtils.isEmpty(metrics)) {
            // TODO(duftler): Should we instead be building the union across all accounts? This doesn't seem quite right yet.
            metricDescriptorsCache =
              metrics
                .stream()
                .map(metricName -> new DatadogMetricDescriptor(metricName))
                .collect(Collectors.toList());

            log.debug("Updated cache with {} metric descriptors via account {}.", metricDescriptorsCache.size(), datadogCredentials.getName());
          } else {
            log.debug("While updating cache, found no metric descriptors via account {}.", datadogCredentials.getName());
          }
        } else {
          log.debug("While updating cache, found no metric descriptors via account {}.", datadogCredentials.getName());
        }
      }
    }
  }
}
