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
import com.google.common.annotations.VisibleForTesting;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.providers.StackdriverCanaryMetricSetQueryConfig;
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
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
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
    return "stackdriver";
  }

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  @Override
  public List<MetricSet> queryMetrics(String metricsAccountName,
                                      CanaryConfig canaryConfig,
                                      CanaryMetricConfig canaryMetricConfig,
                                      CanaryScope canaryScope) throws IOException {
    if (!(canaryScope instanceof StackdriverCanaryScope)) {
      throw new IllegalArgumentException("Canary scope not instance of StackdriverCanaryScope: " + canaryScope);
    }

    StackdriverCanaryScope stackdriverCanaryScope = (StackdriverCanaryScope)canaryScope;
    GoogleNamedAccountCredentials stackdriverCredentials = (GoogleNamedAccountCredentials)accountCredentialsRepository
      .getOne(metricsAccountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + metricsAccountName + "."));
    Monitoring monitoring = stackdriverCredentials.getMonitoring();
    StackdriverCanaryMetricSetQueryConfig stackdriverMetricSetQuery = (StackdriverCanaryMetricSetQueryConfig)canaryMetricConfig.getQuery();
    String projectId = stackdriverCanaryScope.getProject();
    String region = stackdriverCanaryScope.getRegion();
    String resourceType = stackdriverCanaryScope.getResourceType();

    if (StringUtils.isEmpty(projectId)) {
      projectId = stackdriverCredentials.getProject();
    }

    String customFilter = expandCustomFilter(canaryConfig, stackdriverMetricSetQuery, stackdriverCanaryScope);
    String filter = "metric.type=\"" + stackdriverMetricSetQuery.getMetricType() + "\"" +
                    " AND resource.type=" + resourceType;

    // TODO(duftler): Replace direct string-manipulating with helper functions.
    // TODO-maybe(duftler): Replace this logic with a library of templates, one for each resource type.
    if (StringUtils.isEmpty(customFilter)) {
      if ("gce_instance".equals(resourceType)) {
        filter += " AND resource.labels.project_id=" + projectId +
                  " AND metadata.user_labels.\"spinnaker-region\"=" + region +
                  " AND metadata.user_labels.\"spinnaker-server-group\"=" + stackdriverCanaryScope.getScope();
      } else if ("aws_ec2_instance".equals(resourceType)) {
        filter += " AND resource.labels.region=\"aws:" + region + "\"" +
                  " AND metadata.user_labels.\"aws:autoscaling:groupname\"=" + stackdriverCanaryScope.getScope();
      } else if ("gae_app".equals(resourceType)) {
        filter += " AND resource.labels.project_id=" + projectId +
                  " AND resource.labels.version_id=" + stackdriverCanaryScope.getScope();

        Map<String, String> extendedScopeParams = stackdriverCanaryScope.getExtendedScopeParams();

        if (extendedScopeParams != null && extendedScopeParams.containsKey("service")) {
          filter += " AND resource.labels.module_id=" + extendedScopeParams.get("service");
        }
      } else if (Arrays.asList("k8s_container", "k8s_pod", "k8s_node").contains(resourceType)) {
        // TODO(duftler): Figure out where it makes sense to use 'scope'. It is available as a template variable binding,
        // and maps to the control and experiment scopes. Will probably be useful to use expressions in those fields in
        // the ui, and then map 'scope' to some user label value in a customer filter template.
        // TODO(duftler): Should cluster_name be automatically included or required?
        filter += " AND resource.labels.project_id=" + projectId;
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
      } else if (!"global".equals(resourceType)) {
        throw new IllegalArgumentException("Resource type '" + resourceType + "' not yet supported.");
      }
    } else {
      filter += " AND " + customFilter;
    }

    log.debug("filter={}", filter);

    long alignmentPeriodSec = stackdriverCanaryScope.getStep();
    Monitoring.Projects.TimeSeries.List list = monitoring
      .projects()
      .timeSeries()
      .list("projects/" + stackdriverCredentials.getProject())
      .setAggregationAlignmentPeriod(alignmentPeriodSec + "s")
      .setAggregationCrossSeriesReducer("REDUCE_MEAN")
      .setAggregationPerSeriesAligner("ALIGN_MEAN")
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

      if (!StringUtils.isEmpty(region)) {
        stackdriverFetchTimerId = stackdriverFetchTimerId.withTag("region", region);
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
          .stepMillis(alignmentPeriodSec * 1000)
          .values(pointValues);

      // TODO(duftler): Still not quite sure whether this is right or if we should use timeSeries.getMetric().getLabels() instead.
      MonitoredResource monitoredResource = timeSeries.getResource();

      if (monitoredResource != null) {
        Map<String, String> labels = monitoredResource.getLabels();

        if (labels != null) {
          metricSetBuilder.tags(labels);
        }
      }

      metricSetList.add(metricSetBuilder.build());
    }

    return metricSetList;
  }

  @VisibleForTesting
  String expandCustomFilter(CanaryConfig canaryConfig,
                            StackdriverCanaryMetricSetQueryConfig stackdriverMetricSetQuery,
                            StackdriverCanaryScope stackdriverCanaryScope) throws IOException {
    String customFilter = stackdriverMetricSetQuery.getCustomFilter();
    String customFilterTemplate = stackdriverMetricSetQuery.getCustomFilterTemplate();

    log.debug("customFilter={}", customFilter);
    log.debug("customFilterTemplate={}", customFilterTemplate);

    if (StringUtils.isEmpty(customFilter) && !StringUtils.isEmpty(customFilterTemplate)) {
      Map<String, String> templates = canaryConfig.getTemplates();

      // TODO(duftler): Handle this as a config validation step instead.
      if (CollectionUtils.isEmpty(templates)) {
        throw new IllegalArgumentException("Custom filter template '" + customFilterTemplate + "' was referenced, " +
                                           "but no templates were defined.");
      } else if (!templates.containsKey(customFilterTemplate)) {
        throw new IllegalArgumentException("Custom filter template '" + customFilterTemplate + "' was not found.");
      }

      Configuration configuration = new Configuration(Configuration.VERSION_2_3_26);
      String templateStr = templates.get(customFilterTemplate);
      Template template = new Template(customFilterTemplate, new StringReader(templateStr), configuration);

      try {
        log.debug("extendedScopeParams={}", stackdriverCanaryScope.getExtendedScopeParams());

        Map<String, String> templateBindings = new LinkedHashMap<>();

        if (!StringUtils.isEmpty(stackdriverCanaryScope.getProject())) {
          templateBindings.put("project", stackdriverCanaryScope.getProject());
        }

        if (!StringUtils.isEmpty(stackdriverCanaryScope.getResourceType())) {
          templateBindings.put("resourceType", stackdriverCanaryScope.getResourceType());
        }

        if (!StringUtils.isEmpty(stackdriverCanaryScope.getScope())) {
          templateBindings.put("scope", stackdriverCanaryScope.getScope());
        }

        if (!StringUtils.isEmpty(stackdriverCanaryScope.getRegion())) {
          templateBindings.put("region", stackdriverCanaryScope.getRegion());
        }

        if (!CollectionUtils.isEmpty(stackdriverCanaryScope.getExtendedScopeParams())) {
          templateBindings.putAll(stackdriverCanaryScope.getExtendedScopeParams());
        }

        log.debug("templateBindings={}", templateBindings);

        customFilter = FreeMarkerTemplateUtils.processTemplateIntoString(template, templateBindings);
      } catch (TemplateException e) {
        throw new IllegalArgumentException("Problem evaluating custom filter template:", e);
      }
    }

    log.debug("Expanded: customFilter={}", customFilter);

    return customFilter;
  }

  @Override
  public List<Map> getMetadata(String metricsAccountName, String filter) throws IOException {
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
          metricDescriptorsCache = metricDescriptors;

          log.debug("Updated cache with {} metric descriptors via account {}.", metricDescriptors.size(), stackdriverCredentials.getName());
        } else {
          log.debug("While updating cache, found no metric descriptors via account {}.", stackdriverCredentials.getName());
        }
      }
    }
  }
}
