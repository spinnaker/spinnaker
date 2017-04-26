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

class MetricSetSpec extends Specification {

  @Shared
  MetricSet cpuMetricSet1 =
    MetricSet.builder()
      .name('cpu')
      .tag("aTag", "some-value1")
      .tag("bTag", "some-value2")
      .tag("cTag", "some-value3")
      .tag("dTag", "some-value4")
      .build()

  @Shared
  MetricSet cpuMetricSet2 =
    MetricSet.builder()
      .name('cpu')
      .tag("dTag", "some-value4")
      .tag("cTag", "some-value3")
      .tag("bTag", "some-value2")
      .tag("aTag", "some-value1")
      .build()

  @Shared
  MetricSet cpuMetricSet3 =
    MetricSet.builder()
      .name('cpu')
      .tag("aTag", "some-value1")
      .tag("dTag", "some-value4")
      .tag("bTag", "some-value2")
      .tag("cTag", "some-value3")
      .build()

  @Shared
  MetricSet requestsNoTagsMetricSet =
    MetricSet.builder().name('requests').build()

  @Shared
  MetricSet nullNameMetricSet =
    MetricSet.builder().build()

  @Shared
  MetricSet emptyNameMetricSet =
    MetricSet.builder().name('').build()

  @Unroll
  void "metric set key should sort tag map by keys"() {
    expect:
    metricSet.getMetricSetKey() == 'cpu -> {aTag:some-value1, bTag:some-value2, cTag:some-value3, dTag:some-value4}'

    where:
    metricSet << [cpuMetricSet1, cpuMetricSet2, cpuMetricSet3]
  }

  void "metric set key should tolerate zero tags"() {
    expect:
    requestsNoTagsMetricSet.getMetricSetKey() == 'requests -> {}'
  }

  @Unroll
  void "metric set key should reject null or empty name"() {
    when:
    metricSet.getMetricSetKey()

    then:
    thrown IllegalArgumentException

    where:
    metricSet << [nullNameMetricSet, emptyNameMetricSet]
  }
}
