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

package com.netflix.kayenta.metrics

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class MetricSetMixerServiceTest extends Specification {

  @Shared
  MetricSet baselineCpuMetricSet =
    MetricSet.builder()
      .name('cpu')
      .values([1, 3, 5, 7])
      .tag("tagName", "tagValue")
      .build()

  @Shared
  MetricSet canaryCpuMetricSet =
    MetricSet.builder()
      .name('cpu')
      .values([2, 4, 6, 8])
      .tag("tagName", "tagValue")
      .build()

  @Shared
  MetricSet canaryRequestsMetricSet =
    MetricSet.builder()
      .name('requests')
      .build()

  @Shared
  MetricSet canaryCpuWrongTagsMetricSet =
    MetricSet.builder()
      .name('cpu')
      .tag("differentTagName", "differentTagValue")
      .build()

  @Shared
  MetricSet baselineNullNameMetricSet =
    MetricSet.builder()
      .values([1, 3, 5, 7])
      .tag("tagName", "tagValue")
      .build()

  @Shared
  MetricSet baselineEmptyNameMetricSet =
    MetricSet.builder()
      .name('')
      .values([1, 3, 5, 7])
      .tag("tagName", "tagValue")
      .build()

  @Shared
  MetricSet canaryCpuMissingValuesMetricSet =
    MetricSet.builder()
      .name('cpu')
      .values([2, 4])
      .tag("tagName", "tagValue")
      .build()

  @Shared
  MetricSet canaryCpuNullValuesMetricSet =
    MetricSet.builder()
      .name('cpu')
      .tag("tagName", "tagValue")
      .build()

  @Shared
  MetricSet canaryCpuEmptyValuesMetricSet =
    MetricSet.builder()
      .name('cpu')
      .values([])
      .tag("tagName", "tagValue")
      .build()

  void "mixing should succeed if metric set names, tags and # of values all match"() {
    setup:
    MetricSetMixerService metricSetMixerService = new MetricSetMixerService()

    when:
    MetricSetPair metricSetPair = metricSetMixerService.mix(baselineCpuMetricSet, canaryCpuMetricSet)

    then:
    metricSetPair.name == 'cpu'
    metricSetPair.values == [
      baseline: [1, 3, 5, 7],
      canary: [2, 4, 6, 8]
    ]
    metricSetPair.tags == [tagName: "tagValue"]
  }

  void "missing values should be backfilled with trailing NaNs"() {
    setup:
    MetricSetMixerService metricSetMixerService = new MetricSetMixerService()

    when:
    MetricSetPair metricSetPair = metricSetMixerService.mix(baselineCpuMetricSet, canaryCpuMissingValuesMetricSet)

    then:
    metricSetPair.name == 'cpu'
    metricSetPair.values == [
      baseline: [1, 3, 5, 7],
      canary: [2, 4, Double.NaN, Double.NaN]
    ]
    metricSetPair.tags == [tagName: "tagValue"]
  }

  @Unroll
  void "null or empty values list should not be backfilled with NaNs, since it is implicitly treated as filled with NaNs by the analysis engine"() {
    setup:
    MetricSetMixerService metricSetMixerService = new MetricSetMixerService()

    when:
    MetricSetPair metricSetPair = metricSetMixerService.mix(baselineCpuMetricSet, canaryMetricSet)

    then:
    metricSetPair.name == 'cpu'
    metricSetPair.values == [
      baseline: [1, 3, 5, 7],
      canary: []
    ]
    metricSetPair.tags == [tagName: "tagValue"]

    where:
    canaryMetricSet << [canaryCpuNullValuesMetricSet, canaryCpuEmptyValuesMetricSet]
  }

  @Unroll
  void "mixing #baselineMetricSet and #canaryMetricSet should fail"() {
    setup:
    MetricSetMixerService metricSetMixerService = new MetricSetMixerService()

    when:
    metricSetMixerService.mix(baselineMetricSet, canaryMetricSet)

    then:
    thrown IllegalArgumentException

    where:
    baselineMetricSet                       | canaryMetricSet
    baselineCpuMetricSet                    | canaryRequestsMetricSet
    baselineCpuMetricSet                    | canaryCpuWrongTagsMetricSet
    baselineNullNameMetricSet               | canaryCpuMetricSet
    baselineEmptyNameMetricSet              | canaryCpuMetricSet
  }
}
