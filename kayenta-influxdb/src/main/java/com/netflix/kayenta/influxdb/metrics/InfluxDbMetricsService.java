/*
 * Copyright 2018 Joseph Motha
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

package com.netflix.kayenta.influxdb.metrics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.providers.metrics.InfluxdbCanaryMetricSetQueryConfig;
import com.netflix.kayenta.influxdb.model.InfluxDbResult;
import com.netflix.kayenta.influxdb.security.InfluxDbNamedAccountCredentials;
import com.netflix.kayenta.influxdb.service.InfluxDbRemoteService;
import com.netflix.kayenta.metrics.MetricSet;
import com.netflix.kayenta.metrics.MetricSet.MetricSetBuilder;
import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Builder
public class InfluxDbMetricsService implements MetricsService {
  @NotNull
  @Singular
  @Getter
  private List<String> accountNames;

  @Autowired
  private final AccountCredentialsRepository accountCredentialsRepository;

  @Autowired
  private final Registry registry;
  
  @Autowired
  private final InfluxDbQueryBuilder queryBuilder;

  @Override
  public String getType() {
    return InfluxdbCanaryMetricSetQueryConfig.SERVICE_TYPE;
  }

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  @Override
  public List<MetricSet> queryMetrics(String accountName, CanaryConfig canaryConfig, CanaryMetricConfig canaryMetricConfig, CanaryScope canaryScope) {
	  
    InfluxDbNamedAccountCredentials accountCredentials = (InfluxDbNamedAccountCredentials)accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));

    InfluxDbRemoteService remoteService = accountCredentials.getInfluxDbRemoteService();
    InfluxdbCanaryMetricSetQueryConfig queryConfig = (InfluxdbCanaryMetricSetQueryConfig)canaryMetricConfig.getQuery();

    String query = queryBuilder.build(queryConfig, canaryScope);
    log.debug("query={}", query);
    
    String metricSetName = canaryMetricConfig.getName();
    List<InfluxDbResult> influxDbResults = queryInfluxdb(remoteService, metricSetName, query);
    
    return buildMetricSets(metricSetName, influxDbResults);
  }

  private List<InfluxDbResult> queryInfluxdb(InfluxDbRemoteService remoteService, String metricSetName, String query) {
    long startTime = registry.clock().monotonicTime();
    List<InfluxDbResult> influxDbResults;

    try {
      influxDbResults = remoteService.query(metricSetName, query);
    } finally {
      long endTime = registry.clock().monotonicTime();
      Id influxDbFetchTimerId = registry.createId("influxdb.fetchTime");
      registry.timer(influxDbFetchTimerId).record(endTime - startTime, TimeUnit.NANOSECONDS);
    }
    return influxDbResults;
  }
  
  private List<MetricSet> buildMetricSets(String metricSetName, List<InfluxDbResult> influxDbResults) {
    List<MetricSet> metricSets = new ArrayList<MetricSet>();
    if (influxDbResults != null) {
      for (InfluxDbResult influxDbResult : influxDbResults) {
        Instant endtime = Instant.ofEpochMilli(influxDbResult.getStartTimeMillis() + influxDbResult.getStepMillis() * influxDbResult.getValues().size());
        MetricSetBuilder metricSetBuilder = MetricSet.builder()
            .name(metricSetName)
            .startTimeMillis(influxDbResult.getStartTimeMillis())
            .startTimeIso(Instant.ofEpochMilli(influxDbResult.getStartTimeMillis()).toString())
            .endTimeMillis(endtime.toEpochMilli())
            .endTimeIso(endtime.toString())
            .stepMillis(influxDbResult.getStepMillis())
            .values(influxDbResult.getValues())
            .tag("field", influxDbResult.getId());
        
        Map<String, String> tags = influxDbResult.getTags();
        if (tags != null) {
          metricSetBuilder.tags(tags);
        }
        
        metricSets.add(metricSetBuilder.build());
      }
    }
    return metricSets;
  }
}
