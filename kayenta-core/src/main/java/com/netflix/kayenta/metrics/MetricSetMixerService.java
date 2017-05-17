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

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MetricSetMixerService {
  public MetricSetPair mixOne(MetricSet controlMetricSet, MetricSet experimentMetricSet) {
    String controlName = controlMetricSet.getName();
    String experimentName = experimentMetricSet.getName();
    Map<String, String> controlTags = controlMetricSet.getTags();
    Map<String, String> experimentTags = experimentMetricSet.getTags();
    List<Double> controlValues = controlMetricSet.getValues();
    List<Double> experimentValues = experimentMetricSet.getValues();

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

    if (controlValues.size() != experimentValues.size()) {
      List<Double> smallerList;

      if (controlValues.size() > experimentValues.size()) {
        smallerList = experimentValues = new ArrayList<>(experimentValues);
      } else {
        smallerList = controlValues = new ArrayList<>(controlValues);
      }

      long maxSize = Math.max(controlValues.size(), experimentValues.size());

      // As an optimization, we don't backfill completely empty arrays with NaNs.
      if (smallerList.size() > 0) {
        while (smallerList.size() < maxSize) {
          smallerList.add(Double.NaN);
        }
      }
    }

    MetricSetPair.MetricSetPairBuilder metricSetPairBuilder =
      MetricSetPair.builder()
        .name(controlMetricSet.getName())
        .tags(controlMetricSet.getTags())
        .value("control", controlValues)
        .value("experiment", experimentValues);

    return metricSetPairBuilder.build();
  }

  public List<MetricSetPair> mixAll(List<MetricSet> controlMetricSetList, List<MetricSet> experimentMetricSetList) {
    if (controlMetricSetList == null) {
      controlMetricSetList = new ArrayList<>();
    }

    if (experimentMetricSetList == null) {
      experimentMetricSetList = new ArrayList<>();
    }

    // Build 'metric set key' -> 'metric set' maps of control and experiment so we can efficiently identify missing metric sets.
    Map<String, MetricSet> controlMetricSetMap = buildMetricSetMap(controlMetricSetList);
    Map<String, MetricSet> experimentMetricSetMap = buildMetricSetMap(experimentMetricSetList);

    // Identify metric sets missing from each map.
    List<MetricSet> missingFromExperiment = findMissingMetricSets(controlMetricSetList, experimentMetricSetMap);
    List<MetricSet> missingFromControl = findMissingMetricSets(experimentMetricSetList, controlMetricSetMap);

    // Add placeholder metric sets for each one that is missing.
    addMissingMetricSets(controlMetricSetList, missingFromControl);
    addMissingMetricSets(experimentMetricSetList, missingFromExperiment);

    // Sort each metric set list so that we can pair them.
    controlMetricSetList.sort(Comparator.comparing(metricSet -> metricSet.getMetricSetKey()));
    experimentMetricSetList.sort(Comparator.comparing(metricSet -> metricSet.getMetricSetKey()));

    // Produce the list of metric set pairs from the pair of metric set lists.
    List<MetricSetPair> ret = new ArrayList<>();

    for (int i = 0; i < controlMetricSetList.size(); i++) {
      ret.add(mixOne(controlMetricSetList.get(i), experimentMetricSetList.get(i)));
    }

    return ret;
  }

  private static Map<String, MetricSet> buildMetricSetMap(List<MetricSet> metricSetList) {
    return metricSetList
      .stream()
      .collect(Collectors.toMap(MetricSet::getMetricSetKey, Function.identity()));
  }

  private static List<MetricSet> findMissingMetricSets(List<MetricSet> requiredMetricSetList,
                                                       Map<String, MetricSet> knownMetricSetMap) {
    return requiredMetricSetList
      .stream()
      .filter(requiredMetricSet -> !knownMetricSetMap.containsKey(requiredMetricSet.getMetricSetKey()))
      .collect(Collectors.toList());
  }

  private static void addMissingMetricSets(List<MetricSet> knownMetricSetList,
                                           List<MetricSet> missingMetricSetList) {
    knownMetricSetList.addAll(
      missingMetricSetList
        .stream()
        .map(metricSet ->
               MetricSet
                 .builder()
                 .name(metricSet.getName())
                 .tags(metricSet.getTags())
                 .build())
        .collect(Collectors.toList()));
  }
}
