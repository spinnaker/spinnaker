/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.aws.model.AmazonReservationReport
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.ConcurrentHashMap

class ReservationReportCachingAgentSpec extends Specification {
  def registry = Mock(Registry)
  def registryId = Mock(Id)
  def counter = Mock(Counter)

  def "should record regional error and emit metric"() {
    given:
    def errorsByRegion = new ConcurrentHashMap()
    def credentials = Mock(NetflixAmazonCredentials) {
      getName() >> { "test" }
    }
    def usWest1 = Mock(AmazonCredentials.AWSRegion) {
      getName() >> { "us-west-1" }
    }
    def usWest2 = Mock(AmazonCredentials.AWSRegion) {
      getName() >> { "us-west-2" }
    }

    when:
    ReservationReportCachingAgent.recordError(registry, errorsByRegion, credentials, usWest1, new NullPointerException("An NPE a day keeps the chaos monkey away"))
    ReservationReportCachingAgent.recordError(registry, errorsByRegion, credentials, usWest1, new NullPointerException("This is not right!"))
    ReservationReportCachingAgent.recordError(registry, errorsByRegion, credentials, usWest2, new NullPointerException("This is even better!"))

    then:
    errorsByRegion["us-west-1"] == [
      "Failed to describe instances in test:us-west-1, reason: An NPE a day keeps the chaos monkey away",
      "Failed to describe instances in test:us-west-1, reason: This is not right!",
    ]
    errorsByRegion["us-west-2"] == [
      "Failed to describe instances in test:us-west-2, reason: This is even better!"
    ]

    3 * registry.createId("reservedInstances.errors") >> registryId
    2 * registryId.withTags([account: "test", region: "us-west-1"]) >> registryId
    1 * registryId.withTags([account: "test", region: "us-west-2"]) >> registryId
    3 * registry.counter(registryId) >> counter
    3 * counter.increment()
  }
}
