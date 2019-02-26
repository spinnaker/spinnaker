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

package com.netflix.kayenta.metrics;

import com.netflix.kayenta.canary.CanaryMetricConfig;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

public class MetricSetMixerService {
  protected MetricSetPair mixOne(MetricSet controlMetricSet,
                                 MetricSet experimentMetricSet) {
    String controlName = controlMetricSet.getName();
    String experimentName = experimentMetricSet.getName();
    Map<String, String> controlTags = controlMetricSet.getTags();
    Map<String, String> experimentTags = experimentMetricSet.getTags();
    List<Double> controlValues = controlMetricSet.getValues();
    List<Double> experimentValues = experimentMetricSet.getValues();
    MetricSetPair.MetricSetScope controlScope =
      MetricSetPair.MetricSetScope.builder()
        .startTimeIso(controlMetricSet.getStartTimeIso())
        .startTimeMillis(controlMetricSet.getStartTimeMillis())
        .stepMillis(controlMetricSet.getStepMillis())
        .build();
    MetricSetPair.MetricSetScope experimentScope =
      MetricSetPair.MetricSetScope.builder()
        .startTimeIso(experimentMetricSet.getStartTimeIso())
        .startTimeMillis(experimentMetricSet.getStartTimeMillis())
        .stepMillis(experimentMetricSet.getStepMillis())
        .build();

    if (StringUtils.isEmpty(controlName)) {
      throw new IllegalArgumentException("Control metric set name was not set.");
    } else if (StringUtils.isEmpty(experimentName)) {
      throw new IllegalArgumentException("Experiment metric set name was not set.");
    } else if (!controlName.equals(experimentName)) {
      throw new IllegalArgumentException("Control metric set name '" + controlName +
        "' does not match experiment metric set name '" + experimentName + "'.");
    } else if (!controlTags.equals(experimentTags)) {
      throw new IllegalArgumentException("Control metric set tags " + controlTags +
        " do not match experiment metric set tags " + experimentTags + ".");
    }

    // If we know how many data points we should expect, pad the array to contain that number.
    // This typically only happens when one side (control or experiment) have no data at all.
    if (controlMetricSet.expectedDataPoints() > controlValues.size()) {
      controlValues = new ArrayList<>(controlValues);
      while (controlMetricSet.expectedDataPoints() > controlValues.size()) {
        controlValues.add(Double.NaN);
      }
    }
    if (experimentMetricSet.expectedDataPoints() > experimentValues.size()) {
      experimentValues = new ArrayList<>(experimentValues);
      while (experimentMetricSet.expectedDataPoints() > experimentValues.size()) {
        experimentValues.add(Double.NaN);
      }
    }

    MetricSetPair.MetricSetPairBuilder metricSetPairBuilder =
      MetricSetPair.builder()
        .name(controlMetricSet.getName())
        .id(UUID.randomUUID().toString())
        .tags(controlMetricSet.getTags())
        .value("control", controlValues)
        .value("experiment", experimentValues)
        .scope("control", controlScope)
        .scope("experiment", experimentScope);

    if (controlMetricSet.getAttributes() != null) {
      metricSetPairBuilder.attribute("control", controlMetricSet.getAttributes());
    }
    if (experimentMetricSet.getAttributes() != null) {
      metricSetPairBuilder.attribute("experiment", experimentMetricSet.getAttributes());
    }

    return metricSetPairBuilder.build();
  }

  // This looks through the in-memory MetricSets many times.
  protected List<MetricSetPair> mixOneMetric(List<MetricSet> controlMetricSetList,
                                             List<MetricSet> experimentMetricSetList) {
    List<MetricSetPair> ret = new ArrayList<>();

    // Collect the set of tags on both sides.  Depending on what these contain, we will do different
    // things below.
    Set<Map<String, String>> controlTags = controlMetricSetList.stream()
      .map(MetricSet::getTags)
      .collect(Collectors.toSet());
    Set<Map<String, String>> experimentTags = experimentMetricSetList.stream()
      .map(MetricSet::getTags)
      .collect(Collectors.toSet());

    boolean controlHasEmptyTags = controlTags.contains(Collections.emptyMap());
    boolean experimentHasEmptyTags = experimentTags.contains(Collections.emptyMap());

    MetricSet controlTemplate = controlMetricSetList.get(0);
    MetricSet experimentTemplate = experimentMetricSetList.get(0);

    // If the control has empty tags but the experiment does not, we will use the control
    // as a template to create pairs for the experiment data.  The empty tag group for
    // the control will be ignored.  The same for the reverse case.
    if (controlHasEmptyTags && !experimentHasEmptyTags) {
      for (MetricSet ms: experimentMetricSetList) {
        MetricSet template = makeTemplate(controlTemplate, ms.getTags());
        ret.add(mixOne(template, ms));
      }
    } else if (!controlHasEmptyTags && experimentHasEmptyTags) {
      for (MetricSet ms: controlMetricSetList) {
        MetricSet template = makeTemplate(experimentTemplate, ms.getTags());
        ret.add(mixOne(ms, template));
      }
    } else {
      // If both have empty tags, or both have no empty tags, we will just mix them by
      // comparing tag-for-tag in each, and making templates as needed.
      Set<Map<String, String>> allTags = new HashSet<>();
      allTags.addAll(controlTags);
      allTags.addAll(experimentTags);
      for (Map<String, String> tags: allTags) {
        MetricSet controlMetricSet = controlMetricSetList.stream()
          .filter((ms) -> ms.getTags().equals(tags))
          .findFirst()
          .orElse(makeTemplate(controlTemplate, tags));
        MetricSet experimentMetricSet = experimentMetricSetList.stream()
          .filter((ms) -> ms.getTags().equals(tags))
          .findFirst()
          .orElse(makeTemplate(experimentTemplate, tags));
        ret.add(mixOne(controlMetricSet, experimentMetricSet));
      }
    }

    return ret;
  }

  protected MetricSet makeTemplate(MetricSet template, Map<String, String> tags) {
    List<Double> values = DoubleStream
      .generate(() -> Double.NaN)
      .limit(template.expectedDataPoints())
      .boxed()
      .collect(Collectors.toList());

    return MetricSet.builder()
      .attributes(template.getAttributes())
      .name(template.getName())
      .startTimeIso(template.getStartTimeIso())
      .startTimeMillis(template.getStartTimeMillis())
      .stepMillis(template.getStepMillis())
      .endTimeIso(template.getEndTimeIso())
      .endTimeMillis(template.getEndTimeMillis())
      .values(values)
      .tags(tags)
      .build();
  }

  public List<MetricSetPair> mixAll(List<CanaryMetricConfig> canaryMetricConfig,
                                    List<MetricSet> controlMetricSetList,
                                    List<MetricSet> experimentMetricSetList) {

    List<MetricSetPair> ret = new ArrayList<>();

    for (CanaryMetricConfig metric: canaryMetricConfig) {
      List<MetricSet> controlMetrics = controlMetricSetList.stream()
        .filter((ms) -> ms.getName().equals(metric.getName()))
        .collect(Collectors.toList());
      List<MetricSet> experimentMetrics = experimentMetricSetList.stream()
        .filter((ms) -> ms.getName().equals(metric.getName()))
        .collect(Collectors.toList());
      if (controlMetrics.size() == 0) {
        throw new IllegalArgumentException("No control metrics found for " + metric.getName() + " and the metric service did not create a placeholder.");
      }
      if (experimentMetrics.size() == 0) {
        throw new IllegalArgumentException("No experiment metrics found for " + metric.getName() + " and the metric service did not create a placeholder.");
      }

      Set<String> controlTagKeys = controlMetrics.stream().map((t) -> t.getTags().keySet()).flatMap(Collection::stream).collect(Collectors.toSet());
      Set<String> experimentTagKeys = experimentMetrics.stream().map((t) -> t.getTags().keySet()).flatMap(Collection::stream).collect(Collectors.toSet());
      if (controlTagKeys.size() > 0 && experimentTagKeys.size() > 0 && !controlTagKeys.equals(experimentTagKeys)) {
        throw new IllegalArgumentException("Control metrics have different tag keys than the experiment tag set ("
                                             + controlTagKeys + " != " + experimentTagKeys + ").");
      }

      ret.addAll(mixOneMetric(controlMetrics, experimentMetrics));
    }

    return ret;
  }
}
