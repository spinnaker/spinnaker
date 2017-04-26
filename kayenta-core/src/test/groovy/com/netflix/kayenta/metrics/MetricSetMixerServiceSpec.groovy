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

class MetricSetMixerServiceSpec extends Specification {

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

  @Shared
  MetricSet baselineErrorsMetricSet =
    MetricSet.builder()
      .name('errors')
      .values([10, 20, 30])
      .tag("tagNameA", "tagValueA")
      .tag("tagNameB", "tagValueB")
      .build()

  @Shared
  MetricSet canaryErrorsMetricSet =
    MetricSet.builder()
      .name('errors')
      .values([60, 70, 80])
      .tag("tagNameA", "tagValueA")
      .tag("tagNameB", "tagValueB")
      .build()

  @Shared
  MetricSet canaryErrorsNoTagsMetricSet =
    MetricSet.builder()
      .name('errors')
      .values([70, 80, 90])
      .build()

  void "mixing should succeed if metric set names, tags and # of values all match"() {
    setup:
    MetricSetMixerService metricSetMixerService = new MetricSetMixerService()

    when:
    MetricSetPair metricSetPair = metricSetMixerService.mixOne(baselineCpuMetricSet, canaryCpuMetricSet)

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
    MetricSetPair metricSetPair = metricSetMixerService.mixOne(baselineCpuMetricSet, canaryCpuMissingValuesMetricSet)

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
    MetricSetPair metricSetPair = metricSetMixerService.mixOne(baselineCpuMetricSet, canaryMetricSet)

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
    metricSetMixerService.mixOne(baselineMetricSet, canaryMetricSet)

    then:
    thrown IllegalArgumentException

    where:
    baselineMetricSet                       | canaryMetricSet
    baselineCpuMetricSet                    | canaryRequestsMetricSet
    baselineCpuMetricSet                    | canaryCpuWrongTagsMetricSet
    baselineNullNameMetricSet               | canaryCpuMetricSet
    baselineEmptyNameMetricSet              | canaryCpuMetricSet
  }

  @Unroll
  void "lists of metric sets are paired properly"() {
    setup:
    MetricSetMixerService metricSetMixerService = new MetricSetMixerService()

    when:
    List<MetricSetPair> metricSetPairs = metricSetMixerService.mixAll(baselineMetricSetList, canaryMetricSetList)

    then:
    metricSetPairs.size() == Math.max(baselineMetricSetList ? baselineMetricSetList.size() : 0, canaryMetricSetList ? canaryMetricSetList.size() : 0)
    metricSetPairs.collect { it.name } == expectedMetricSetNames
    metricSetPairs.collect { it.tags } == expectedTagMaps
    metricSetPairs.collect { it.values } == expectedValues

    where:
    baselineMetricSetList                           | canaryMetricSetList                                    || expectedMetricSetNames | expectedTagMaps                                                         | expectedValues
    // 1:1
    [baselineCpuMetricSet]                          | [canaryCpuEmptyValuesMetricSet]                        || ['cpu']                | [[tagName: 'tagValue']]                                                 | [[baseline: [1, 3, 5, 7], canary: []]]
    [baselineErrorsMetricSet]                       | [canaryErrorsMetricSet]                                || ['errors']             | [[tagNameA: 'tagValueA', tagNameB: 'tagValueB']]                        | [[baseline: [10, 20, 30], canary: [60, 70, 80]]]

    // 1:1, mismatched tags
    [baselineCpuMetricSet]                          | [canaryCpuWrongTagsMetricSet]                          || ['cpu', 'cpu']         | [[differentTagName: 'differentTagValue'], [tagName: 'tagValue']]        | [[baseline: [],           canary: []],           [baseline: [1, 3, 5, 7], canary: []]]
    [baselineErrorsMetricSet]                       | [canaryErrorsNoTagsMetricSet]                          || ['errors', 'errors']   | [[tagNameA: 'tagValueA', tagNameB: 'tagValueB'], [:]]                   | [[baseline: [10, 20, 30], canary: []],           [baseline: [],           canary: [70, 80, 90]]]

    // 2:2
    [baselineCpuMetricSet, baselineErrorsMetricSet] | [canaryCpuEmptyValuesMetricSet, canaryErrorsMetricSet] || ['cpu', 'errors']      | [[tagName: 'tagValue'], [tagNameA: 'tagValueA', tagNameB: 'tagValueB']] | [[baseline: [1, 3, 5, 7], canary: []],           [baseline: [10, 20, 30], canary: [60, 70, 80]]]
    [baselineErrorsMetricSet, baselineCpuMetricSet] | [canaryCpuEmptyValuesMetricSet, canaryErrorsMetricSet] || ['cpu', 'errors']      | [[tagName: 'tagValue'], [tagNameA: 'tagValueA', tagNameB: 'tagValueB']] | [[baseline: [1, 3, 5, 7], canary: []],           [baseline: [10, 20, 30], canary: [60, 70, 80]]]
    [baselineCpuMetricSet, baselineErrorsMetricSet] | [canaryErrorsMetricSet, canaryCpuMetricSet]            || ['cpu', 'errors']      | [[tagName: 'tagValue'], [tagNameA: 'tagValueA', tagNameB: 'tagValueB']] | [[baseline: [1, 3, 5, 7], canary: [2, 4, 6, 8]], [baseline: [10, 20, 30], canary: [60, 70, 80]]]

    // 1:2
    [baselineCpuMetricSet]                          | [canaryCpuEmptyValuesMetricSet, canaryErrorsMetricSet] || ['cpu', 'errors']      | [[tagName: 'tagValue'], [tagNameA: 'tagValueA', tagNameB: 'tagValueB']] | [[baseline: [1, 3, 5, 7], canary: []],           [baseline: [],           canary: [60, 70, 80]]]
    [baselineErrorsMetricSet]                       | [canaryCpuEmptyValuesMetricSet, canaryErrorsMetricSet] || ['cpu', 'errors']      | [[tagName: 'tagValue'], [tagNameA: 'tagValueA', tagNameB: 'tagValueB']] | [[baseline: [],           canary: []],           [baseline: [10, 20, 30], canary: [60, 70, 80]]]

    // 2:1
    [baselineCpuMetricSet, baselineErrorsMetricSet] | [canaryCpuEmptyValuesMetricSet]                        || ['cpu', 'errors']      | [[tagName: 'tagValue'], [tagNameA: 'tagValueA', tagNameB: 'tagValueB']] | [[baseline: [1, 3, 5, 7], canary: []],           [baseline: [10, 20, 30], canary: []]]
    [baselineCpuMetricSet, baselineErrorsMetricSet] | [canaryErrorsMetricSet]                                || ['cpu', 'errors']      | [[tagName: 'tagValue'], [tagNameA: 'tagValueA', tagNameB: 'tagValueB']] | [[baseline: [1, 3, 5, 7], canary: []],           [baseline: [10, 20, 30], canary: [60, 70, 80]]]

    // null:1
    null                                            | [canaryErrorsMetricSet]                                || ['errors']             | [[tagNameA: 'tagValueA', tagNameB: 'tagValueB']]                        | [[baseline: [],           canary: [60, 70, 80]]]

    // []:1
    []                                              | [canaryErrorsMetricSet]                                || ['errors']             | [[tagNameA: 'tagValueA', tagNameB: 'tagValueB']]                        | [[baseline: [],           canary: [60, 70, 80]]]

    // 2:null
    [baselineCpuMetricSet, baselineErrorsMetricSet] | null                                                   || ['cpu', 'errors']      | [[tagName: 'tagValue'], [tagNameA: 'tagValueA', tagNameB: 'tagValueB']] | [[baseline: [1, 3, 5, 7], canary: []],           [baseline: [10, 20, 30], canary: []]]

    // 2:[]
    [baselineCpuMetricSet, baselineErrorsMetricSet] | []                                                     || ['cpu', 'errors']      | [[tagName: 'tagValue'], [tagNameA: 'tagValueA', tagNameB: 'tagValueB']] | [[baseline: [1, 3, 5, 7], canary: []],           [baseline: [10, 20, 30], canary: []]]
  }
}
