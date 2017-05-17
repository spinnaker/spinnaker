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
  MetricSet controlCpuMetricSet =
    MetricSet.builder()
      .name('cpu')
      .values([1, 3, 5, 7])
      .tag("tagName", "tagValue")
      .build()

  @Shared
  MetricSet experimentCpuMetricSet =
    MetricSet.builder()
      .name('cpu')
      .values([2, 4, 6, 8])
      .tag("tagName", "tagValue")
      .build()

  @Shared
  MetricSet experimentRequestsMetricSet =
    MetricSet.builder()
      .name('requests')
      .build()

  @Shared
  MetricSet experimentCpuWrongTagsMetricSet =
    MetricSet.builder()
      .name('cpu')
      .tag("differentTagName", "differentTagValue")
      .build()

  @Shared
  MetricSet controlNullNameMetricSet =
    MetricSet.builder()
      .values([1, 3, 5, 7])
      .tag("tagName", "tagValue")
      .build()

  @Shared
  MetricSet controlEmptyNameMetricSet =
    MetricSet.builder()
      .name('')
      .values([1, 3, 5, 7])
      .tag("tagName", "tagValue")
      .build()

  @Shared
  MetricSet experimentCpuMissingValuesMetricSet =
    MetricSet.builder()
      .name('cpu')
      .values([2, 4])
      .tag("tagName", "tagValue")
      .build()

  @Shared
  MetricSet experimentCpuNullValuesMetricSet =
    MetricSet.builder()
      .name('cpu')
      .tag("tagName", "tagValue")
      .build()

  @Shared
  MetricSet experimentCpuEmptyValuesMetricSet =
    MetricSet.builder()
      .name('cpu')
      .values([])
      .tag("tagName", "tagValue")
      .build()

  @Shared
  MetricSet controlErrorsMetricSet =
    MetricSet.builder()
      .name('errors')
      .values([10, 20, 30])
      .tag("tagNameA", "tagValueA")
      .tag("tagNameB", "tagValueB")
      .build()

  @Shared
  MetricSet experimentErrorsMetricSet =
    MetricSet.builder()
      .name('errors')
      .values([60, 70, 80])
      .tag("tagNameA", "tagValueA")
      .tag("tagNameB", "tagValueB")
      .build()

  @Shared
  MetricSet experimentErrorsNoTagsMetricSet =
    MetricSet.builder()
      .name('errors')
      .values([70, 80, 90])
      .build()

  void "mixing should succeed if metric set names, tags and # of values all match"() {
    setup:
    MetricSetMixerService metricSetMixerService = new MetricSetMixerService()

    when:
    MetricSetPair metricSetPair = metricSetMixerService.mixOne(controlCpuMetricSet, experimentCpuMetricSet)

    then:
    metricSetPair.name == 'cpu'
    metricSetPair.values == [
      control: [1, 3, 5, 7],
      experiment: [2, 4, 6, 8]
    ]
    metricSetPair.tags == [tagName: "tagValue"]
  }

  void "missing values should be backfilled with trailing NaNs"() {
    setup:
    MetricSetMixerService metricSetMixerService = new MetricSetMixerService()

    when:
    MetricSetPair metricSetPair = metricSetMixerService.mixOne(controlCpuMetricSet, experimentCpuMissingValuesMetricSet)

    then:
    metricSetPair.name == 'cpu'
    metricSetPair.values == [
      control: [1, 3, 5, 7],
      experiment: [2, 4, Double.NaN, Double.NaN]
    ]
    metricSetPair.tags == [tagName: "tagValue"]
  }

  @Unroll
  // TODO(duftler/mgraff): Consider this case carefully. We may want to in fact backfill the entire list.
  void "null or empty values list should not be backfilled with NaNs, since it is implicitly treated as filled with NaNs by the analysis engine"() {
    setup:
    MetricSetMixerService metricSetMixerService = new MetricSetMixerService()

    when:
    MetricSetPair metricSetPair = metricSetMixerService.mixOne(controlCpuMetricSet, experimentMetricSet)

    then:
    metricSetPair.name == 'cpu'
    metricSetPair.values == [
      control: [1, 3, 5, 7],
      experiment: []
    ]
    metricSetPair.tags == [tagName: "tagValue"]

    where:
    experimentMetricSet << [experimentCpuNullValuesMetricSet, experimentCpuEmptyValuesMetricSet]
  }

  @Unroll
  void "mixing #controlMetricSet and #experimentMetricSet should fail"() {
    setup:
    MetricSetMixerService metricSetMixerService = new MetricSetMixerService()

    when:
    metricSetMixerService.mixOne(controlMetricSet, experimentMetricSet)

    then:
    thrown IllegalArgumentException

    where:
    controlMetricSet          | experimentMetricSet
    controlCpuMetricSet       | experimentRequestsMetricSet
    controlCpuMetricSet       | experimentCpuWrongTagsMetricSet
    controlNullNameMetricSet  | experimentCpuMetricSet
    controlEmptyNameMetricSet | experimentCpuMetricSet
  }

  @Unroll
  void "lists of metric sets are paired properly"() {
    setup:
    MetricSetMixerService metricSetMixerService = new MetricSetMixerService()

    when:
    List<MetricSetPair> metricSetPairs = metricSetMixerService.mixAll(controlMetricSetList, experimentMetricSetList)

    then:
    metricSetPairs.size() == Math.max(controlMetricSetList ? controlMetricSetList.size() : 0, experimentMetricSetList ? experimentMetricSetList.size() : 0)
    metricSetPairs.collect { it.name } == expectedMetricSetNames
    metricSetPairs.collect { it.tags } == expectedTagMaps
    metricSetPairs.collect { it.values } == expectedValues

    where:
    controlMetricSetList                          | experimentMetricSetList                                        || expectedMetricSetNames | expectedTagMaps                                                         | expectedValues
    // 1:1
    [controlCpuMetricSet]                         | [experimentCpuEmptyValuesMetricSet]                            || ['cpu']                | [[tagName: 'tagValue']]                                                 | [[control: [1, 3, 5, 7], experiment: []]]
    [controlErrorsMetricSet]                      | [experimentErrorsMetricSet]                                    || ['errors']             | [[tagNameA: 'tagValueA', tagNameB: 'tagValueB']]                        | [[control: [10, 20, 30], experiment: [60, 70, 80]]]

    // 1:1, mismatched tags
    [controlCpuMetricSet]                         | [experimentCpuWrongTagsMetricSet]                              || ['cpu', 'cpu']         | [[differentTagName: 'differentTagValue'], [tagName: 'tagValue']]        | [[control: [],           experiment: []],           [control: [1, 3, 5, 7], experiment: []]]
    [controlErrorsMetricSet]                      | [experimentErrorsNoTagsMetricSet]                              || ['errors', 'errors']   | [[tagNameA: 'tagValueA', tagNameB: 'tagValueB'], [:]]                   | [[control: [10, 20, 30], experiment: []],           [control: [],           experiment: [70, 80, 90]]]

    // 2:2
    [controlCpuMetricSet, controlErrorsMetricSet] | [experimentCpuEmptyValuesMetricSet, experimentErrorsMetricSet] || ['cpu', 'errors']      | [[tagName: 'tagValue'], [tagNameA: 'tagValueA', tagNameB: 'tagValueB']] | [[control: [1, 3, 5, 7], experiment: []],           [control: [10, 20, 30], experiment: [60, 70, 80]]]
    [controlErrorsMetricSet, controlCpuMetricSet] | [experimentCpuEmptyValuesMetricSet, experimentErrorsMetricSet] || ['cpu', 'errors']      | [[tagName: 'tagValue'], [tagNameA: 'tagValueA', tagNameB: 'tagValueB']] | [[control: [1, 3, 5, 7], experiment: []],           [control: [10, 20, 30], experiment: [60, 70, 80]]]
    [controlCpuMetricSet, controlErrorsMetricSet] | [experimentErrorsMetricSet, experimentCpuMetricSet]            || ['cpu', 'errors']      | [[tagName: 'tagValue'], [tagNameA: 'tagValueA', tagNameB: 'tagValueB']] | [[control: [1, 3, 5, 7], experiment: [2, 4, 6, 8]], [control: [10, 20, 30], experiment: [60, 70, 80]]]

    // 1:2
    [controlCpuMetricSet]                         | [experimentCpuEmptyValuesMetricSet, experimentErrorsMetricSet] || ['cpu', 'errors']      | [[tagName: 'tagValue'], [tagNameA: 'tagValueA', tagNameB: 'tagValueB']] | [[control: [1, 3, 5, 7], experiment: []],           [control: [],           experiment: [60, 70, 80]]]
    [controlErrorsMetricSet]                      | [experimentCpuEmptyValuesMetricSet, experimentErrorsMetricSet] || ['cpu', 'errors']      | [[tagName: 'tagValue'], [tagNameA: 'tagValueA', tagNameB: 'tagValueB']] | [[control: [],           experiment: []],           [control: [10, 20, 30], experiment: [60, 70, 80]]]

    // 2:1
    [controlCpuMetricSet, controlErrorsMetricSet] | [experimentCpuEmptyValuesMetricSet]                            || ['cpu', 'errors']      | [[tagName: 'tagValue'], [tagNameA: 'tagValueA', tagNameB: 'tagValueB']] | [[control: [1, 3, 5, 7], experiment: []],           [control: [10, 20, 30], experiment: []]]
    [controlCpuMetricSet, controlErrorsMetricSet] | [experimentErrorsMetricSet]                                    || ['cpu', 'errors']      | [[tagName: 'tagValue'], [tagNameA: 'tagValueA', tagNameB: 'tagValueB']] | [[control: [1, 3, 5, 7], experiment: []],           [control: [10, 20, 30], experiment: [60, 70, 80]]]

    // null:1
    null                                          | [experimentErrorsMetricSet]                                    || ['errors']             | [[tagNameA: 'tagValueA', tagNameB: 'tagValueB']]                        | [[control: [],           experiment: [60, 70, 80]]]

    // []:1
    []                                            | [experimentErrorsMetricSet]                                    || ['errors']             | [[tagNameA: 'tagValueA', tagNameB: 'tagValueB']]                        | [[control: [],           experiment: [60, 70, 80]]]

    // 2:null
    [controlCpuMetricSet, controlErrorsMetricSet] | null                                                           || ['cpu', 'errors']      | [[tagName: 'tagValue'], [tagNameA: 'tagValueA', tagNameB: 'tagValueB']] | [[control: [1, 3, 5, 7], experiment: []],           [control: [10, 20, 30], experiment: []]]

    // 2:[]
    [controlCpuMetricSet, controlErrorsMetricSet] | []                                                             || ['cpu', 'errors']      | [[tagName: 'tagValue'], [tagNameA: 'tagValueA', tagNameB: 'tagValueB']] | [[control: [1, 3, 5, 7], experiment: []],           [control: [10, 20, 30], experiment: []]]
  }
}
