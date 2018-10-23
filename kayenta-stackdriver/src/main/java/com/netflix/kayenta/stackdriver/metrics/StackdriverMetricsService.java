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

package com.netflix.kayenta.stackdriver.metrics;

import com.google.api.services.monitoring.v3.Monitoring;
import com.google.api.services.monitoring.v3.model.ListMetricDescriptorsResponse;
import com.google.api.services.monitoring.v3.model.ListTimeSeriesResponse;
import com.google.api.services.monitoring.v3.model.Metric;
import com.google.api.services.monitoring.v3.model.MetricDescriptor;
import com.google.api.services.monitoring.v3.model.MonitoredResource;
import com.google.api.services.monitoring.v3.model.Point;
import com.google.api.services.monitoring.v3.model.TimeSeries;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.providers.metrics.QueryConfigUtils;
import com.netflix.kayenta.canary.providers.metrics.StackdriverCanaryMetricSetQueryConfig;
import com.netflix.kayenta.google.security.GoogleNamedAccountCredentials;
import com.netflix.kayenta.metrics.MetricSet;
import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;
import com.netflix.kayenta.stackdriver.canary.StackdriverCanaryScope;
import com.netflix.kayenta.stackdriver.config.StackdriverConfigurationProperties;
import com.netflix.spectator.api.Id;
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
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Builder
@Slf4j
public class StackdriverMetricsService implements MetricsService {

  @NotNull
  @Singular
  @Getter
  private List<String> accountNames;

  @Autowired
  private final AccountCredentialsRepository accountCredentialsRepository;

  @Autowired
  private final Registry registry;

  @Autowired
  private final StackdriverConfigurationProperties stackdriverConfigurationProperties;

  @Builder.Default
  private List<MetricDescriptor> metricDescriptorsCache = Collections.emptyList();

  @Override
  public String getType() {
    return StackdriverCanaryMetricSetQueryConfig.SERVICE_TYPE;
  }

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  @Override
  public String buildQuery(String metricsAccountName,
                           CanaryConfig canaryConfig,
                           CanaryMetricConfig canaryMetricConfig,
                           CanaryScope canaryScope) throws IOException {
    StackdriverCanaryMetricSetQueryConfig queryConfig = (StackdriverCanaryMetricSetQueryConfig)canaryMetricConfig.getQuery();
    StackdriverCanaryScope stackdriverCanaryScope = (StackdriverCanaryScope)canaryScope;
    String projectId = determineProjectId(metricsAccountName, stackdriverCanaryScope);
    String location = stackdriverCanaryScope.getLocation();
    String scope = stackdriverCanaryScope.getScope();
    String resourceType =
      StringUtils.hasText(queryConfig.getResourceType())
      ? queryConfig.getResourceType()
      : stackdriverCanaryScope.getResourceType();

    String customFilter = QueryConfigUtils.expandCustomFilter(
      canaryConfig,
            queryConfig,
      stackdriverCanaryScope,
      new String[]{"project", "resourceType", "scope", "location"});
    String filter = "metric.type=\"" + queryConfig.getMetricType() + "\"" +
                    " AND resource.type=" + resourceType;

    // TODO(duftler): Replace direct string-manipulating with helper functions.
    // TODO-maybe(duftler): Replace this logic with a library of templates, one for each resource type.
    if (StringUtils.isEmpty(customFilter)) {
      if ("gce_instance".equals(resourceType)) {
        if (StringUtils.isEmpty(location)) {
          throw new IllegalArgumentException("Location (i.e. region) is required when resourceType is 'gce_instance'.");
        }

        if (StringUtils.isEmpty(scope)) {
          throw new IllegalArgumentException("Scope is required when resourceType is 'gce_instance'.");
        }

        filter += " AND project=" + projectId +
                  " AND metadata.user_labels.\"spinnaker-region\"=" + location +
                  " AND metadata.user_labels.\"spinnaker-server-group\"=" + scope;
      } else if ("aws_ec2_instance".equals(resourceType)) {
        if (StringUtils.isEmpty(location)) {
          throw new IllegalArgumentException("Location (i.e. region) is required when resourceType is 'aws_ec2_instance'.");
        }

        if (StringUtils.isEmpty(scope)) {
          throw new IllegalArgumentException("Scope is required when resourceType is 'aws_ec2_instance'.");
        }

        filter += " AND resource.labels.region=\"aws:" + location + "\"" +
                  " AND metadata.user_labels.\"aws:autoscaling:groupname\"=" + scope;
      } else if ("gae_app".equals(resourceType)) {
        if (StringUtils.isEmpty(scope)) {
          throw new IllegalArgumentException("Scope is required when resourceType is 'gae_app'.");
        }

        filter += " AND project=" + projectId +
                  " AND resource.labels.version_id=" + scope;

        Map<String, String> extendedScopeParams = stackdriverCanaryScope.getExtendedScopeParams();

        if (extendedScopeParams != null && extendedScopeParams.containsKey("service")) {
          filter += " AND resource.labels.module_id=" + extendedScopeParams.get("service");
        }
      } else if (Arrays.asList("k8s_container", "k8s_pod", "k8s_node").contains(resourceType)) {
        // TODO(duftler): Figure out where it makes sense to use 'scope'. It is available as a template variable binding,
        // and maps to the control and experiment scopes. Will probably be useful to use expressions in those fields in
        // the ui, and then map 'scope' to some user label value in a custom filter template.
        // TODO(duftler): Should cluster_name be automatically included or required?
        filter += " AND project=" + projectId;
        Map<String, String> extendedScopeParams = stackdriverCanaryScope.getExtendedScopeParams();

        if (extendedScopeParams != null) {
          List<String> resourceLabelKeys = Arrays.asList("location", "node_name", "cluster_name", "pod_name", "container_name", "namespace_name");

          for (String resourceLabelKey : resourceLabelKeys) {
            if (extendedScopeParams.containsKey(resourceLabelKey)) {
              filter += " AND resource.labels." + resourceLabelKey + "=" + extendedScopeParams.get(resourceLabelKey);
            }
          }

          for (String extendedScopeParamsKey : extendedScopeParams.keySet()) {
            if (extendedScopeParamsKey.startsWith("user_labels.")) {
              String userLabelKey = extendedScopeParamsKey.substring(12);

              filter += " AND metadata.user_labels.\"" + userLabelKey + "\"=\"" + extendedScopeParams.get(extendedScopeParamsKey) + "\"";
            }
          }
        }
      } else if ("gke_container".equals(resourceType)) {
        filter += " AND project=" + projectId;
        Map<String, String> extendedScopeParams = stackdriverCanaryScope.getExtendedScopeParams();

        if (extendedScopeParams != null) {
          List<String> resourceLabelKeys = Arrays.asList("cluster_name", "namespace_id", "instance_id", "pod_id", "container_name", "zone");

          for (String resourceLabelKey : resourceLabelKeys) {
            if (extendedScopeParams.containsKey(resourceLabelKey)) {
              filter += " AND resource.labels." + resourceLabelKey + "=" + extendedScopeParams.get(resourceLabelKey);
            }
          }

          for (String extendedScopeParamsKey : extendedScopeParams.keySet()) {
            if (extendedScopeParamsKey.startsWith("user_labels.")) {
              String userLabelKey = extendedScopeParamsKey.substring(12);

              filter += " AND metadata.user_labels.\"" + userLabelKey + "\"=\"" + extendedScopeParams.get(extendedScopeParamsKey) + "\"";
            }
          }
        }
      } else if (!"global".equals(resourceType)) {
        throw new IllegalArgumentException("Resource type '" + resourceType + "' not yet explicitly supported. If you employ a " +
          "custom filter, you may use any resource type you like.");
      }
    } else {
      filter += " AND " + customFilter;
    }

    log.debug("filter={}", filter);

    return filter;
  }

  @Override
  public List<MetricSet> queryMetrics(String metricsAccountName,
                                      CanaryConfig canaryConfig,
                                      CanaryMetricConfig canaryMetricConfig,
                                      CanaryScope canaryScope) throws IOException {
    if (!(canaryScope instanceof StackdriverCanaryScope)) {
      throw new IllegalArgumentException("Canary scope not instance of StackdriverCanaryScope: " + canaryScope +
                                         ". One common cause is having multiple METRICS_STORE accounts configured but " +
                                         "neglecting to explicitly specify which account to use for a given request.");
    }

    StackdriverCanaryScope stackdriverCanaryScope = (StackdriverCanaryScope)canaryScope;
    GoogleNamedAccountCredentials stackdriverCredentials = (GoogleNamedAccountCredentials)accountCredentialsRepository
      .getOne(metricsAccountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + metricsAccountName + "."));
    Monitoring monitoring = stackdriverCredentials.getMonitoring();
    StackdriverCanaryMetricSetQueryConfig stackdriverMetricSetQuery = (StackdriverCanaryMetricSetQueryConfig)canaryMetricConfig.getQuery();
    String projectId = determineProjectId(metricsAccountName, stackdriverCanaryScope);
    String location = stackdriverCanaryScope.getLocation();
    String resourceType =
      StringUtils.hasText(stackdriverMetricSetQuery.getResourceType())
      ? stackdriverMetricSetQuery.getResourceType()
      : stackdriverCanaryScope.getResourceType();
    String crossSeriesReducer =
      StringUtils.hasText(stackdriverMetricSetQuery.getCrossSeriesReducer())
      ? stackdriverMetricSetQuery.getCrossSeriesReducer()
      : StringUtils.hasText(stackdriverCanaryScope.getCrossSeriesReducer())
        ? stackdriverCanaryScope.getCrossSeriesReducer()
        : "REDUCE_MEAN";
    String perSeriesAligner =
      StringUtils.hasText(stackdriverMetricSetQuery.getPerSeriesAligner())
      ? stackdriverMetricSetQuery.getPerSeriesAligner()
      : StringUtils.hasText(stackdriverCanaryScope.getPerSeriesAligner())
        ? stackdriverCanaryScope.getPerSeriesAligner()
        : "ALIGN_MEAN";

    if (StringUtils.isEmpty(projectId)) {
      projectId = stackdriverCredentials.getProject();
    }

    if (StringUtils.isEmpty(resourceType)) {
      throw new IllegalArgumentException("Resource type is required.");
    }

    if (StringUtils.isEmpty(stackdriverCanaryScope.getStart())) {
      throw new IllegalArgumentException("Start time is required.");
    }

    if (StringUtils.isEmpty(stackdriverCanaryScope.getEnd())) {
      throw new IllegalArgumentException("End time is required.");
    }

    String filter = buildQuery(metricsAccountName, canaryConfig, canaryMetricConfig, canaryScope);

    long alignmentPeriodSec = stackdriverCanaryScope.getStep();
    Monitoring.Projects.TimeSeries.List list = monitoring
      .projects()
      .timeSeries()
      .list("projects/" + stackdriverCredentials.getProject())
      .setAggregationAlignmentPeriod(alignmentPeriodSec + "s")
      .setAggregationCrossSeriesReducer(crossSeriesReducer)
      .setAggregationPerSeriesAligner(perSeriesAligner)
      .setFilter(filter)
      .setIntervalStartTime(stackdriverCanaryScope.getStart().toString())
      .setIntervalEndTime(stackdriverCanaryScope.getEnd().toString());

    List<String> groupByFields = stackdriverMetricSetQuery.getGroupByFields();

    if (groupByFields != null) {
      list.setAggregationGroupByFields(groupByFields);
    }

    long startTime = registry.clock().monotonicTime();
    ListTimeSeriesResponse response;

    try {
      response = list.execute();
    } finally {
      long endTime = registry.clock().monotonicTime();
      Id stackdriverFetchTimerId = registry.createId("stackdriver.fetchTime").withTag("project", projectId);

      if (!StringUtils.isEmpty(location)) {
        stackdriverFetchTimerId = stackdriverFetchTimerId.withTag("location", location);
      }

      registry.timer(stackdriverFetchTimerId).record(endTime - startTime, TimeUnit.NANOSECONDS);
    }

    long startAsLong = stackdriverCanaryScope.getStart().toEpochMilli();
    long endAsLong = stackdriverCanaryScope.getEnd().toEpochMilli();
    long elapsedSeconds = (endAsLong - startAsLong) / 1000;
    long numIntervals = elapsedSeconds / alignmentPeriodSec;
    long remainder = elapsedSeconds % alignmentPeriodSec;

    if (remainder > 0) {
      numIntervals++;
    }

    List<TimeSeries> timeSeriesList = response.getTimeSeries();

    if (timeSeriesList == null || timeSeriesList.size() == 0) {
      // Add placeholder metric set.
      timeSeriesList = Collections.singletonList(new TimeSeries().setMetric(new Metric()).setPoints(new ArrayList<>()));
    }

    List<MetricSet> metricSetList = new ArrayList<>();

    for (TimeSeries timeSeries : timeSeriesList) {
      List<Point> points = timeSeries.getPoints();

      if (points.size() != numIntervals) {
        String pointOrPoints = numIntervals == 1 ? "point" : "points";

        log.warn("Expected {} data {}, but received {}.", numIntervals, pointOrPoints, points.size());
      }

      Collections.reverse(points);

      Instant responseStartTimeInstant =
        points.size() > 0
        ? Instant.parse(points.get(0).getInterval().getStartTime())
        : stackdriverCanaryScope.getStart();
      long responseStartTimeMillis = responseStartTimeInstant.toEpochMilli();

      Instant responseEndTimeInstant =
        points.size() > 0
          ? Instant.parse(points.get(points.size() - 1).getInterval().getEndTime())
          : stackdriverCanaryScope.getEnd();

      // TODO(duftler): What if there are no data points?
      List<Double> pointValues =
        points
          .stream()
          .map(point -> point.getValue().getDoubleValue())
          .collect(Collectors.toList());

      MetricSet.MetricSetBuilder metricSetBuilder =
        MetricSet.builder()
          .name(canaryMetricConfig.getName())
          .startTimeMillis(responseStartTimeMillis)
          .startTimeIso(responseStartTimeInstant.toString())
          .endTimeMillis(responseEndTimeInstant.toEpochMilli())
          .endTimeIso(responseEndTimeInstant.toString())
          .stepMillis(alignmentPeriodSec * 1000)
          .values(pointValues);

      // TODO(duftler): Still not quite sure whether this is right or if we should use timeSeries.getMetric().getLabels() instead.
      MonitoredResource monitoredResource = timeSeries.getResource();

      if (monitoredResource != null) {
        Map<String, String> labels = monitoredResource.getLabels();

        if (labels != null) {
          Map<String, String> prunedLabels = new HashMap<>(labels);
          prunedLabels.remove("project_id");
          metricSetBuilder.tags(prunedLabels);
        }
      }

      metricSetBuilder.attribute("query", filter);
      metricSetBuilder.attribute("crossSeriesReducer", crossSeriesReducer);
      metricSetBuilder.attribute("perSeriesAligner", perSeriesAligner);

      metricSetList.add(metricSetBuilder.build());
    }

    return metricSetList;
  }

  private String determineProjectId(String metricsAccountName,
                                    StackdriverCanaryScope stackdriverCanaryScope) {
    String projectId = stackdriverCanaryScope.getProject();

    if (StringUtils.isEmpty(projectId)) {
      GoogleNamedAccountCredentials stackdriverCredentials = (GoogleNamedAccountCredentials)accountCredentialsRepository
              .getOne(metricsAccountName)
              .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + metricsAccountName + "."));

      projectId = stackdriverCredentials.getProject();
    }

    return projectId;
  }

  @Override
  public List<Map> getMetadata(String metricsAccountName, String filter) {
    if (!StringUtils.isEmpty(filter)) {
      String lowerCaseFilter = filter.toLowerCase();

      return metricDescriptorsCache
        .stream()
        .filter(metricDescriptor -> metricDescriptor.getName().toLowerCase().contains(lowerCaseFilter))
        .collect(Collectors.toList());
    } else {
      return metricDescriptorsCache
        .stream()
        .collect(Collectors.toList());
    }
  }

  @Scheduled(fixedDelayString = "#{@stackdriverConfigurationProperties.metadataCachingIntervalMS}")
  public void updateMetricDescriptorsCache() throws IOException {
    Set<AccountCredentials> accountCredentialsSet =
      CredentialsHelper.getAllAccountsOfType(AccountCredentials.Type.METRICS_STORE, accountCredentialsRepository);

    for (AccountCredentials credentials : accountCredentialsSet) {
      if (credentials instanceof GoogleNamedAccountCredentials) {
        GoogleNamedAccountCredentials stackdriverCredentials = (GoogleNamedAccountCredentials)credentials;
        ListMetricDescriptorsResponse listMetricDescriptorsResponse =
          stackdriverCredentials
            .getMonitoring()
            .projects()
            .metricDescriptors()
            .list("projects/" + stackdriverCredentials.getProject())
            .execute();
        List<MetricDescriptor> metricDescriptors = listMetricDescriptorsResponse.getMetricDescriptors();

        if (!CollectionUtils.isEmpty(metricDescriptors)) {
          // TODO(duftler): Should we instead be building the union across all accounts? This doesn't seem quite right yet.
          metricDescriptorsCache = metricDescriptors;

          log.debug("Updated cache with {} metric descriptors via account {}.", metricDescriptors.size(), stackdriverCredentials.getName());
        } else {
          log.debug("While updating cache, found no metric descriptors via account {}.", stackdriverCredentials.getName());
        }
      }
    }
  }
}
