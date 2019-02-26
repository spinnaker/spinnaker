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

import com.netflix.kayenta.canary.CanaryMetricConfig
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
      .attribute("attributeName", "attributeValue")
      .build()

  @Shared
  MetricSet experimentCpuMetricSet =
    MetricSet.builder()
      .name('cpu')
      .values([2, 4, 6, 8])
      .tag("tagName", "tagValue")
      .attribute("attributeName", "attributeValue")
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

  void "mixOne() throws if control metric set name was not set"() {
    setup:
    MetricSetMixerService metricSetMixerService = new MetricSetMixerService()
    MetricSet control = MetricSet.builder().name(null).tag("tagKeyA", "tagValueA").build()
    MetricSet experiment = MetricSet.builder().name('metric1').tag("tagKeyB", "tagValueA").build()

    when:
    metricSetMixerService.mixOne(control, experiment)

    then:
    def error = thrown(IllegalArgumentException)
    error.message.contains('Control metric set name was not set')
  }

  void "mixOne() throws if experiment metric set name was not set"() {
    setup:
    MetricSetMixerService metricSetMixerService = new MetricSetMixerService()
    MetricSet control = MetricSet.builder().name('metric1').tag("tagKeyA", "tagValueA").build()
    MetricSet experiment = MetricSet.builder().name(null).tag("tagKeyB", "tagValueA").build()

    when:
    metricSetMixerService.mixOne(control, experiment)

    then:
    def error = thrown(IllegalArgumentException)
    error.message.contains('Experiment metric set name was not set')
  }

  void "inconsistent metric set names should throw an exception"() {
    setup:
    MetricSetMixerService metricSetMixerService = new MetricSetMixerService()
    MetricSet control = MetricSet.builder().name('metric1').tag("tagKeyA", "tagValueA").build()
    MetricSet experiment = MetricSet.builder().name('metric2').tag("tagKeyB", "tagValueA").build()

    when:
    metricSetMixerService.mixOne(control, experiment)

    then:
    def error = thrown(IllegalArgumentException)
    error.message.contains('does not match experiment metric set name')
  }

  void "inconsistent tag keys should throw an exception"() {
    setup:
    MetricSetMixerService metricSetMixerService = new MetricSetMixerService()
    List<CanaryMetricConfig> canaryMetricConfig = makeConfig(['metric1'])
    MetricSet control = MetricSet.builder().name('metric1').tag("tagKeyA", "tagValueA").build()
    MetricSet experiment = MetricSet.builder().name('metric1').tag("tagKeyB", "tagValueA").build()

    when:
    metricSetMixerService.mixAll(canaryMetricConfig, [control], [experiment])

    then:
    def error = thrown(IllegalArgumentException)
    error.message.contains('different tag keys')
  }

  void "Missing metrics for control should throw an exception"() {
    setup:
    MetricSetMixerService metricSetMixerService = new MetricSetMixerService()
    List<CanaryMetricConfig> canaryMetricConfig = makeConfig(['metric1'])
    MetricSet experiment = MetricSet.builder().name('metric1').tag("tagKeyA", "tagValueA").build()

    when:
    metricSetMixerService.mixAll(canaryMetricConfig, [], [experiment])

    then:
    def error = thrown(IllegalArgumentException)
    error.message.contains('No control metrics found')
  }

  void "Missing metrics for experiment should throw an exception"() {
    setup:
    MetricSetMixerService metricSetMixerService = new MetricSetMixerService()
    List<CanaryMetricConfig> canaryMetricConfig = makeConfig(['metric1'])
    MetricSet control = MetricSet.builder().name('metric1').tag("tagKeyA", "tagValueA").build()

    when:
    metricSetMixerService.mixAll(canaryMetricConfig, [control], [])

    then:
    def error = thrown(IllegalArgumentException)
    error.message.contains('No experiment metrics found')
  }

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
    metricSetPair.attributes == [ control: [ attributeName: "attributeValue" ], experiment: [ attributeName: "attributeValue" ]]
  }

  void "Mixing sets should produce an id field"() {
    setup:
    MetricSetMixerService metricSetMixerService = new MetricSetMixerService()

    when:
    MetricSetPair metricSetPair = metricSetMixerService.mixOne(controlCpuMetricSet, experimentCpuMetricSet)

    then:
    !metricSetPair.id.empty
  }

  void "Mixing data sets with no data should back-fill both to all NaNs, with appropriate length"() {
    setup:
    MetricSetMixerService metricSetMixerService = new MetricSetMixerService()
    MetricSet controlCpuMetricSetWithTimes =
      MetricSet.builder()
        .name('cpu')
        .values([])
        .startTimeMillis(0)
        .endTimeMillis(60000 * 4)
        .stepMillis(60000)
        .tag("tagName", "tagValue")
        .attribute("attributeName", "attributeValue")
        .build()

    MetricSet experimentCpuMetricSetWithTimes =
      MetricSet.builder()
        .name('cpu')
        .values([])
        .startTimeMillis(0)
        .endTimeMillis(60000 * 4)
        .stepMillis(60000)
        .tag("tagName", "tagValue")
        .attribute("attributeName", "attributeValue")
        .build()

    when:
    MetricSetPair metricSetPair = metricSetMixerService.mixOne(controlCpuMetricSetWithTimes, experimentCpuMetricSetWithTimes)

    then:
    metricSetPair.values == [
      control: [ Double.NaN, Double.NaN, Double.NaN, Double.NaN ],
      experiment: [ Double.NaN, Double.NaN, Double.NaN, Double.NaN ]
    ]
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
    List<CanaryMetricConfig> canaryMetricConfig = makeConfig(names)
    List<MetricSetPair> metricSetPairs = metricSetMixerService.mixAll(canaryMetricConfig, controlMetricSetList, experimentMetricSetList)

    then:
    metricSetPairs.size() == Math.max(controlMetricSetList.size(), experimentMetricSetList.size())
    metricSetPairs.collect { it.name } == expectedMetricSetNames
    metricSetPairs.collect { it.tags } == expectedTagMaps
    metricSetPairs.collect { it.values } == expectedValues

    where:
    names               | controlMetricSetList                          | experimentMetricSetList                                        || expectedMetricSetNames | expectedTagMaps                                                         | expectedValues
    // 1:1
    [ 'cpu' ]           | [controlCpuMetricSet]                         | [experimentCpuEmptyValuesMetricSet]                            || ['cpu']                | [[tagName: 'tagValue']]                                                 | [[control: [1, 3, 5, 7], experiment: []]]
    [ 'errors' ]        | [controlErrorsMetricSet]                      | [experimentErrorsMetricSet]                                    || ['errors']             | [[tagNameA: 'tagValueA', tagNameB: 'tagValueB']]                        | [[control: [10, 20, 30], experiment: [60, 70, 80]]]

    // 1:1, no-tags used as a placeholder
    [ 'errors' ]        | [controlErrorsMetricSet]                      | [experimentErrorsNoTagsMetricSet]                              || ['errors']             | [[tagNameA: 'tagValueA', tagNameB: 'tagValueB']]                        | [[control: [10, 20, 30], experiment: []]]
    [ 'errors' ]        | [experimentErrorsNoTagsMetricSet]             | [controlErrorsMetricSet]                                       || ['errors']             | [[tagNameA: 'tagValueA', tagNameB: 'tagValueB']]                        | [[control: [], experiment: [10, 20, 30]]]

    // 2:2
    [ 'cpu', 'errors' ] | [controlCpuMetricSet, controlErrorsMetricSet] | [experimentCpuEmptyValuesMetricSet, experimentErrorsMetricSet] || ['cpu', 'errors']      | [[tagName: 'tagValue'], [tagNameA: 'tagValueA', tagNameB: 'tagValueB']] | [[control: [1, 3, 5, 7], experiment: []],           [control: [10, 20, 30], experiment: [60, 70, 80]]]
    [ 'cpu', 'errors' ] | [controlErrorsMetricSet, controlCpuMetricSet] | [experimentCpuEmptyValuesMetricSet, experimentErrorsMetricSet] || ['cpu', 'errors']      | [[tagName: 'tagValue'], [tagNameA: 'tagValueA', tagNameB: 'tagValueB']] | [[control: [1, 3, 5, 7], experiment: []],           [control: [10, 20, 30], experiment: [60, 70, 80]]]
    [ 'cpu', 'errors' ] | [controlCpuMetricSet, controlErrorsMetricSet] | [experimentErrorsMetricSet, experimentCpuMetricSet]            || ['cpu', 'errors']      | [[tagName: 'tagValue'], [tagNameA: 'tagValueA', tagNameB: 'tagValueB']] | [[control: [1, 3, 5, 7], experiment: [2, 4, 6, 8]], [control: [10, 20, 30], experiment: [60, 70, 80]]]
  }

  List<CanaryMetricConfig> makeConfig(List<String> names) {
    List<CanaryMetricConfig> ret = []

    names.each { name ->
      ret.add(CanaryMetricConfig.builder().name(name).build())
    }
    ret
  }
}
