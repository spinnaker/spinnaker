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
import java.util.List;
import java.util.Map;

public class MetricSetMixerService {
  public MetricSetPair mix(MetricSet baselineMetricSet, MetricSet canaryMetricSet) {
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
      // TODO: Since this just deals with one pair of metric sets, the tags must match. When we deal with multiple
      // sets, we must identify each pair of metric sets via name + tagMap.
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
}
