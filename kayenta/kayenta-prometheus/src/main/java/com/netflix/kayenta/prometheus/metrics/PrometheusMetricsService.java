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

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.providers.metrics.PrometheusCanaryMetricSetQueryConfig;
import com.netflix.kayenta.canary.providers.metrics.QueryConfigUtils;
import com.netflix.kayenta.metrics.MetricSet;
import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.prometheus.canary.PrometheusCanaryScope;
import com.netflix.kayenta.prometheus.config.PrometheusManagedAccount;
import com.netflix.kayenta.prometheus.model.PrometheusResults;
import com.netflix.kayenta.prometheus.service.PrometheusRemoteService;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Builder
@Slf4j
public class PrometheusMetricsService implements MetricsService {

  @NotNull private String scopeLabel;

  @NotNull @Singular @Getter private List<String> accountNames;

  @Autowired private final AccountCredentialsRepository accountCredentialsRepository;

  @Autowired private final Registry registry;

  @Autowired private final PrometheusMetricDescriptorsCache metricDescriptorsCache;

  @Override
  public String getType() {
    return "prometheus";
  }

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  private StringBuilder addScopeFilter(
      StringBuilder queryBuilder,
      PrometheusCanaryScope prometheusCanaryScope,
      String resourceType,
      PrometheusCanaryMetricSetQueryConfig queryConfig,
      String customFilter) {
    String scope = prometheusCanaryScope.getScope();
    String projectId = prometheusCanaryScope.getProject();
    String location = prometheusCanaryScope.getLocation();
    List<String> filters = queryConfig.getLabelBindings();
    if (filters == null) {
      filters = new ArrayList<>();
    }

    if (StringUtils.isEmpty(customFilter)) {
      if ("gce_instance".equals(resourceType)) {
        addGCEFilters(scopeLabel, scope, projectId, location, filters);
      } else if ("aws_ec2_instance".equals(resourceType)) {
        addEC2Filters("asg_groupName", scope, location, filters);
      } else if (!StringUtils.isEmpty(resourceType)) {
        throw new IllegalArgumentException(
            "There is no explicit support for resourceType '"
                + resourceType
                + "'. "
                + "You may build whatever query makes sense for your environment via label "
                + "bindings and custom filter templates.");
      } else {
        throw new IllegalArgumentException(
            "Either a resource type or a custom filter is required.");
      }
    } else {
      List<String> customFilterTokens = Arrays.asList(customFilter.split(","));

      filters = new ArrayList(filters);
      filters.addAll(0, customFilterTokens);
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

  private static void addGCEFilters(
      String scopeLabel, String scope, String projectId, String location, List<String> filters) {
    if (StringUtils.isEmpty(location)) {
      throw new IllegalArgumentException(
          "Location (i.e. region) is required when resourceType is 'gce_instance'.");
    }

    if (!StringUtils.isEmpty(scope)) {
      scope += "-.{4}";

      filters.add(scopeLabel + "=~\"" + scope + "\"");
    }

    String zoneRegex = ".+/";

    if (!StringUtils.isEmpty(projectId)) {
      zoneRegex += "projects/" + projectId + "/";
    }

    zoneRegex += "zones/" + location + "-.{1}";
    filters.add("zone=~\"" + zoneRegex + "\"");
  }

  private static void addEC2Filters(
      String scopeLabel, String scope, String location, List<String> filters) {
    if (StringUtils.isEmpty(location)) {
      throw new IllegalArgumentException(
          "Location (i.e. region) is required when resourceType is 'aws_ec2_instance'.");
    }

    if (!StringUtils.isEmpty(scope)) {
      filters.add(scopeLabel + "=\"" + scope + "\"");
    }

    filters.add("zone=~\"" + location + ".{1}\"");
  }

  private static StringBuilder addAvgQuery(StringBuilder queryBuilder) {
    return queryBuilder.insert(0, "avg(").append(")");
  }

  private static StringBuilder addGroupByQuery(
      StringBuilder queryBuilder, PrometheusCanaryMetricSetQueryConfig queryConfig) {
    List<String> groupByFields = queryConfig.getGroupByFields();

    if (!CollectionUtils.isEmpty(groupByFields)) {
      queryBuilder.append(" by (");
      String sep = "";
      for (String elem : groupByFields) {
        queryBuilder.append(sep);
        queryBuilder.append(elem);
        sep = ",";
      }
      queryBuilder.append(")");
    }
    return queryBuilder;
  }

  @Override
  public String buildQuery(
      String metricsAccountName,
      CanaryConfig canaryConfig,
      CanaryMetricConfig canaryMetricConfig,
      CanaryScope canaryScope) {
    PrometheusCanaryMetricSetQueryConfig queryConfig =
        (PrometheusCanaryMetricSetQueryConfig) canaryMetricConfig.getQuery();
    PrometheusCanaryScope prometheusCanaryScope = (PrometheusCanaryScope) canaryScope;
    String resourceType =
        StringUtils.hasText(queryConfig.getResourceType())
            ? queryConfig.getResourceType()
            : prometheusCanaryScope.getResourceType();

    String customFilter =
        QueryConfigUtils.expandCustomFilter(
            canaryConfig,
            queryConfig,
            prometheusCanaryScope,
            new String[] {"project", "resourceType", "scope", "location"});

    if (!StringUtils.isEmpty(customFilter) && customFilter.startsWith("PromQL:")) {
      String promQlExpr = customFilter.substring(7);

      log.debug("Detected complete PromQL expression: {}", promQlExpr);

      return promQlExpr;
    } else {
      if (StringUtils.isEmpty(queryConfig.getMetricName())) {
        throw new IllegalArgumentException("Metric Name is required when query type is Default.");
      }
      StringBuilder queryBuilder = new StringBuilder(queryConfig.getMetricName());

      queryBuilder =
          addScopeFilter(
              queryBuilder, prometheusCanaryScope, resourceType, queryConfig, customFilter);
      queryBuilder = addAvgQuery(queryBuilder);
      queryBuilder = addGroupByQuery(queryBuilder, queryConfig);

      log.debug("query={}", queryBuilder);

      return queryBuilder.toString();
    }
  }

  @Override
  public List<MetricSet> queryMetrics(
      String accountName,
      CanaryConfig canaryConfig,
      CanaryMetricConfig canaryMetricConfig,
      CanaryScope canaryScope)
      throws IOException {
    if (!(canaryScope instanceof PrometheusCanaryScope)) {
      throw new IllegalArgumentException(
          "Canary scope not instance of PrometheusCanaryScope: "
              + canaryScope
              + ". One common cause is having multiple METRICS_STORE accounts configured but "
              + "neglecting to explicitly specify which account to use for a given request.");
    }

    PrometheusManagedAccount credentials = accountCredentialsRepository.getRequiredOne(accountName);
    PrometheusRemoteService prometheusRemoteService = credentials.getPrometheusRemoteService();

    if (StringUtils.isEmpty(canaryScope.getStart())) {
      throw new IllegalArgumentException("Start time is required.");
    }

    if (StringUtils.isEmpty(canaryScope.getEnd())) {
      throw new IllegalArgumentException("End time is required.");
    }

    String query =
        buildQuery(accountName, canaryConfig, canaryMetricConfig, canaryScope).toString();

    long startTime = registry.clock().monotonicTime();
    List<PrometheusResults> prometheusResultsList;

    try {
      prometheusResultsList =
          prometheusRemoteService.rangeQuery(
              query,
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

    if (!CollectionUtils.isEmpty(prometheusResultsList)) {
      for (PrometheusResults prometheusResults : prometheusResultsList) {
        Instant responseStartTimeInstant =
            Instant.ofEpochMilli(prometheusResults.getStartTimeMillis());
        Instant responseEndTimeInstant = Instant.ofEpochMilli(prometheusResults.getEndTimeMillis());
        MetricSet.MetricSetBuilder metricSetBuilder =
            MetricSet.builder()
                .name(canaryMetricConfig.getName())
                .startTimeMillis(prometheusResults.getStartTimeMillis())
                .startTimeIso(responseStartTimeInstant.toString())
                .endTimeMillis(prometheusResults.getEndTimeMillis())
                .endTimeIso(responseEndTimeInstant.toString())
                .stepMillis(TimeUnit.SECONDS.toMillis(prometheusResults.getStepSecs()))
                .values(prometheusResults.getValues());

        Map<String, String> tags = prometheusResults.getTags();

        if (tags != null) {
          metricSetBuilder.tags(tags);
        }

        metricSetBuilder.attribute("query", query);

        metricSetList.add(metricSetBuilder.build());
      }
    } else {
      log.warn("Received no data from Prometheus for query: {} scope: {}", query, canaryScope);
      MetricSet.MetricSetBuilder metricSetBuilder =
          MetricSet.builder()
              .name(canaryMetricConfig.getName())
              .startTimeMillis(canaryScope.getStart().toEpochMilli())
              .startTimeIso(canaryScope.getStart().toString())
              .endTimeMillis(canaryScope.getEnd().toEpochMilli())
              .endTimeIso(canaryScope.getEnd().toString())
              .stepMillis(TimeUnit.SECONDS.toMillis(canaryScope.getStep()))
              .values(Collections.emptyList());

      metricSetBuilder.attribute("query", query);

      metricSetList.add(metricSetBuilder.build());
    }

    return metricSetList;
  }

  @Override
  public List<Map> getMetadata(String metricsAccountName, String filter) {
    return metricDescriptorsCache.getMetadata(metricsAccountName, filter);
  }
}
