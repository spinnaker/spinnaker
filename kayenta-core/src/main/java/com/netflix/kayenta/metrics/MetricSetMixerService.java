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
  public MetricSetPair mixOne(MetricSet baselineMetricSet, MetricSet canaryMetricSet) {
    String baselineName = baselineMetricSet.getName();
    String canaryName = canaryMetricSet.getName();
    Map<String, String> baselineTags = baselineMetricSet.getTags();
    Map<String, String> canaryTags = canaryMetricSet.getTags();
    List<Double> baselineValues = baselineMetricSet.getValues();
    List<Double> canaryValues = canaryMetricSet.getValues();

    if (StringUtils.isEmpty(baselineName)) {
      throw new IllegalArgumentException("Baseline metric set name was not set.");
    } else if (StringUtils.isEmpty(canaryName)) {
      throw new IllegalArgumentException("Canary metric set name was not set.");
    } else if (!baselineName.equals(canaryName)) {
      throw new IllegalArgumentException("Baseline metric set name '" + baselineName +
        "' does not match canary metric set name '" + canaryName + "'.");
    } else if (!baselineTags.equals(canaryTags)) {
      throw new IllegalArgumentException("Baseline metric set tags " + baselineTags +
        " does not match canary metric set tags " + canaryTags + ".");
    }

    if (baselineValues.size() != canaryValues.size()) {
      List<Double> smallerList;

      if (baselineValues.size() > canaryValues.size()) {
        smallerList = canaryValues = new ArrayList<>(canaryValues);
      } else {
        smallerList = baselineValues = new ArrayList<>(baselineValues);
      }

      long maxSize = Math.max(baselineValues.size(), canaryValues.size());

      // As an optimization, we don't backfill completely empty arrays with NaNs.
      if (smallerList.size() > 0) {
        while (smallerList.size() < maxSize) {
          smallerList.add(Double.NaN);
        }
      }
    }

    MetricSetPair.MetricSetPairBuilder metricSetPairBuilder =
      MetricSetPair.builder()
        .name(baselineMetricSet.getName())
        .tags(baselineMetricSet.getTags())
        .value("baseline", baselineValues)
        .value("canary", canaryValues);

    return metricSetPairBuilder.build();
  }

  public List<MetricSetPair> mixAll(List<MetricSet> baselineMetricSetList, List<MetricSet> canaryMetricSetList) {
    if (baselineMetricSetList == null) {
      baselineMetricSetList = new ArrayList<>();
    }

    if (canaryMetricSetList == null) {
      canaryMetricSetList = new ArrayList<>();
    }

    // Build 'metric set key' -> 'metric set' maps of baseline and canary so we can efficiently identify missing metric sets.
    Map<String, MetricSet> baselineMetricSetMap = buildMetricSetMap(baselineMetricSetList);
    Map<String, MetricSet> canaryMetricSetMap = buildMetricSetMap(canaryMetricSetList);

    // Identify metric sets missing from each map.
    List<MetricSet> missingFromCanary = findMissingMetricSets(baselineMetricSetList, canaryMetricSetMap);
    List<MetricSet> missingFromBaseline = findMissingMetricSets(canaryMetricSetList, baselineMetricSetMap);

    // Add placeholder metric sets for each one that is missing.
    addMissingMetricSets(baselineMetricSetList, missingFromBaseline);
    addMissingMetricSets(canaryMetricSetList, missingFromCanary);

    // Sort each metric set list so that we can pair them.
    baselineMetricSetList.sort(Comparator.comparing(metricSet -> metricSet.getMetricSetKey()));
    canaryMetricSetList.sort(Comparator.comparing(metricSet -> metricSet.getMetricSetKey()));

    // Produce the list of metric set pairs from the pair of metric set lists.
    List<MetricSetPair> ret = new ArrayList<>();

    for (int i = 0; i < baselineMetricSetList.size(); i++) {
      ret.add(mixOne(baselineMetricSetList.get(i), canaryMetricSetList.get(i)));
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
