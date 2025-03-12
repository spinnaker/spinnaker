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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.model.CloudMetricProvider
import spock.lang.Shared
import spock.lang.Specification
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class CloudMetricControllerSpec extends Specification {

  @Shared
  CloudMetricController controller

  @Shared
  CloudMetricProvider provider

  def setup() {
    provider = Mock(CloudMetricProvider)
    controller = new CloudMetricController(
        [provider],
        Clock.fixed(Instant.ofEpochMilli(0), ZoneId.systemDefault()))
  }

  void "getStatistics sends defaults for start/end time"() {
    when:
    controller.getStatistics("aws", "test", "us-east-1", "theMetric", null, null, [:])

    then:
    provider.getCloudProvider() >> "aws"
    1 * provider.getStatistics("test", "us-east-1", "theMetric", [:], 0 - 24 * 60 * 60 * 1000, 0) >> null
  }

  void "getStatistics removes startTime/endTime fields from filters if present"() {
    when:
    Map<String, String> filters = [ startTime: "0", endTime: "1", other: 'filter']
    controller.getStatistics("aws", "test", "us-east-1", "theMetric", 1, 2, filters)

    then:
    provider.getCloudProvider() >> "aws"
    1 * provider.getStatistics("test", "us-east-1", "theMetric", [other: 'filter'], 1, 2) >> null
  }
}
