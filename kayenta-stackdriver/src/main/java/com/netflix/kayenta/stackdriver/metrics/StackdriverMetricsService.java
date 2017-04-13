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
import com.google.api.services.monitoring.v3.model.ListTimeSeriesResponse;
import com.google.api.services.monitoring.v3.model.Point;
import com.google.api.services.monitoring.v3.model.TimeSeries;
import com.netflix.kayenta.google.security.GoogleNamedAccountCredentials;
import com.netflix.kayenta.metrics.MetricSet;
import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Builder
@Slf4j
public class StackdriverMetricsService implements MetricsService {

  @NotNull
  @Singular
  @Getter
  private List<String> accountNames;

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository;

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  @Override
  // These are still placeholder arguments. Each metrics service will have its own set of required/optional arguments. The return type is a placeholder as well.
  public Optional<MetricSet> queryMetrics(String accountName,
                                          String metricSetName,
                                          String instanceNamePrefix,
                                          String intervalStartTime,
                                          String intervalEndTime) throws IOException {
    GoogleNamedAccountCredentials credentials = (GoogleNamedAccountCredentials)accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));
    Monitoring monitoring = credentials.getMonitoring();
    // Some sample query parameters (mainly leaving all of these here so that I remember the api).
    int alignmentPeriodSec = 3600;
    ListTimeSeriesResponse response = monitoring
      .projects()
      .timeSeries()
      .list("projects/" + credentials.getProject())
      .setAggregationAlignmentPeriod(alignmentPeriodSec + "s")
      .setAggregationCrossSeriesReducer("REDUCE_MEAN")
// Leaving this here for the time-being so I don't have to hunt for the label name later.
//      .setAggregationGroupByFields(Arrays.asList("metric.label.instance_name"))
      .setAggregationPerSeriesAligner("ALIGN_MEAN")
      .setFilter("metric.type=\"compute.googleapis.com/instance/cpu/utilization\" AND metric.label.instance_name=starts_with(\"" + instanceNamePrefix + "\")")
      .setIntervalStartTime(intervalStartTime)
      .setIntervalEndTime(intervalEndTime)
      .execute();

    Instant requestStartTimeInstant = Instant.parse(intervalStartTime);
    long requestStartTimeMillis = requestStartTimeInstant.toEpochMilli();
    Instant requestEndTimeInstant = Instant.parse(intervalEndTime);
    long requestEndTimeMillis = requestEndTimeInstant.toEpochMilli();
    long elapsedSeconds = (requestEndTimeMillis - requestStartTimeMillis) / 1000;
    long numIntervals = elapsedSeconds / alignmentPeriodSec;
    long remainder = elapsedSeconds % alignmentPeriodSec;

    if (remainder > 0) {
      numIntervals++;
    }

    List<TimeSeries> timeSeriesList = response.getTimeSeries();

    if (timeSeriesList.size() == 0) {
      throw new IllegalArgumentException("No time series was returned.");
    } else if (timeSeriesList.size() > 1) {
      log.warn("Expected 1 time series, but {} were returned; using just the first time series.", timeSeriesList.size());
    }

    TimeSeries timeSeries = timeSeriesList.get(0);
    List<Point> points = timeSeries.getPoints();

    if (points.size() != numIntervals) {
      String pointOrPoints = numIntervals == 1 ? "point" : "points";

      log.warn("Expected {} data {}, but received {}.", numIntervals, pointOrPoints, points.size());
    }

    Collections.reverse(points);

    Instant responseStartTimeInstant =
      points.size() > 0 ? Instant.parse(points.get(0).getInterval().getStartTime()) : requestStartTimeInstant;
    long responseStartTimeMillis = responseStartTimeInstant.toEpochMilli();

    // TODO(duftler): What if there are no data points?
    List<Double> pointValues =
      points
        .stream()
        .map(point -> point.getValue().getDoubleValue())
        .collect(Collectors.toList());

    MetricSet.MetricSetBuilder metricSetBuilder =
      MetricSet.builder()
        .name(metricSetName)
        .startTimeMillis(responseStartTimeMillis)
        .startTimeIso(responseStartTimeInstant.toString())
        .stepMillis(alignmentPeriodSec * 1000)
        .values(pointValues);

    Map<String, String> labels = timeSeries.getMetric().getLabels();

    if (labels != null) {
      metricSetBuilder.tags(labels);
    }

    return Optional.of(metricSetBuilder.build());
  }
}
