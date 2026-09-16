/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.model

import software.amazon.awssdk.services.cloudwatch.model.Datapoint
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsResponse
import spock.lang.Specification

class AmazonMetricStatisticsSpec extends Specification {

  void "should sort metrics by timestamp"() {
    given:
    GetMetricStatisticsResponse amazonResult = GetMetricStatisticsResponse.builder()
    .datapoints(
        Datapoint.builder().timestamp(new Date(4).toInstant()).average(3.0).build(),
        Datapoint.builder().timestamp(new Date(0).toInstant()).average(1.0).build(),
        Datapoint.builder().timestamp(new Date(1).toInstant()).average(2.0).build(),
        Datapoint.builder().timestamp(new Date(3).toInstant()).average(1.0).build()
    ).build()
    when:
    AmazonMetricStatistics result = AmazonMetricStatistics.from(amazonResult)

    then:
    result.datapoints.timestamp.time == [0, 1, 3, 4]
    result.datapoints.average == [1.0, 2.0, 1.0, 3.0]
  }

  void "should add any statistics provided by datapoints"() {
    given:
    GetMetricStatisticsResponse amazonResult = GetMetricStatisticsResponse.builder()
        .datapoints(
        Datapoint.builder().timestamp(new Date(0).toInstant()).average(3.0).build(),
        Datapoint.builder().timestamp(new Date(1).toInstant()).maximum(1.0).build(),
        Datapoint.builder().timestamp(new Date(2).toInstant()).minimum(2.0).build(),
        Datapoint.builder().timestamp(new Date(3).toInstant()).sampleCount(1.0).build(),
        Datapoint.builder().timestamp(new Date(3).toInstant()).sum(6.0).build()
    ).build()
    when:
    AmazonMetricStatistics result = AmazonMetricStatistics.from(amazonResult)

    then:
    result.datapoints.average == [3.0, null, null, null, null]
    result.datapoints.maximum == [null, 1.0, null, null, null]
    result.datapoints.minimum == [null, null, 2.0, null, null]
    result.datapoints.sampleCount == [null, null, null, 1.0, null]
    result.datapoints.sum == [null, null, null, null, 6.0]
  }

  void "should add unit from first datapoint"() {
    given:
    GetMetricStatisticsResponse amazonResult = GetMetricStatisticsResponse.builder()
        .datapoints(
        Datapoint.builder().timestamp(new Date(4).toInstant()).average(3.0).unit("hectares").build(),
        Datapoint.builder().timestamp(new Date(0).toInstant()).average(1.0).unit("stone").build(),
        Datapoint.builder().timestamp(new Date(1).toInstant()).average(2.0).unit("siriometers").build(),
        Datapoint.builder().timestamp(new Date(3).toInstant()).average(1.0).unit("metric ounces").build()
    ).build()
    when:
    AmazonMetricStatistics result = AmazonMetricStatistics.from(amazonResult)

    then:
    result.unit == "stone"
  }
}
