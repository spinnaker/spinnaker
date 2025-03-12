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

import com.amazonaws.services.cloudwatch.model.Datapoint
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult
import spock.lang.Specification

class AmazonMetricStatisticsSpec extends Specification {

  void "should sort metrics by timestamp"() {
    given:
    GetMetricStatisticsResult amazonResult = new GetMetricStatisticsResult()
    .withDatapoints(
        new Datapoint().withTimestamp(new Date(4)).withAverage(3.0),
        new Datapoint().withTimestamp(new Date(0)).withAverage(1.0),
        new Datapoint().withTimestamp(new Date(1)).withAverage(2.0),
        new Datapoint().withTimestamp(new Date(3)).withAverage(1.0)
    )
    when:
    AmazonMetricStatistics result = AmazonMetricStatistics.from(amazonResult)

    then:
    result.datapoints.timestamp.time == [0, 1, 3, 4]
    result.datapoints.average == [1.0, 2.0, 1.0, 3.0]
  }

  void "should add any statistics provided by datapoints"() {
    given:
    GetMetricStatisticsResult amazonResult = new GetMetricStatisticsResult()
        .withDatapoints(
        new Datapoint().withTimestamp(new Date(0)).withAverage(3.0),
        new Datapoint().withTimestamp(new Date(1)).withMaximum(1.0),
        new Datapoint().withTimestamp(new Date(2)).withMinimum(2.0),
        new Datapoint().withTimestamp(new Date(3)).withSampleCount(1.0),
        new Datapoint().withTimestamp(new Date(3)).withSum(6.0)
    )
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
    GetMetricStatisticsResult amazonResult = new GetMetricStatisticsResult()
        .withDatapoints(
        new Datapoint().withTimestamp(new Date(4)).withAverage(3.0).withUnit("hectares"),
        new Datapoint().withTimestamp(new Date(0)).withAverage(1.0).withUnit("stone"),
        new Datapoint().withTimestamp(new Date(1)).withAverage(2.0).withUnit("siriometers"),
        new Datapoint().withTimestamp(new Date(3)).withAverage(1.0).withUnit("metric ounces")
    )
    when:
    AmazonMetricStatistics result = AmazonMetricStatistics.from(amazonResult)

    then:
    result.unit == "stone"
  }
}
