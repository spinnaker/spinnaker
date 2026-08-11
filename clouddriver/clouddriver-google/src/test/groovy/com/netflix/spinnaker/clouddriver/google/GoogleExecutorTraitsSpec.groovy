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

package com.netflix.spinnaker.clouddriver.google

import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.MockClock
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleConfig
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import static com.netflix.spinnaker.clouddriver.google.security.AccountForClient.UNKNOWN_ACCOUNT

import spock.lang.Specification
import java.util.concurrent.TimeUnit


/**
 * Note: These tests always create a statusCode of 0.^[[m
 *       Ideally we'd inject a 200 on success and something else on error.
 *       However it is not possible to do this. The Google API status codes
 *       appear to come from the HTTP response headers, so would need to be
 *       which isnt even clear how to do. You'd probably need to mock out the
 *       entire transport. The clouddriver implementation does not have a hook
 *       for that. We could add one in, but using it seems really hard. Instead
 *       API is stable), and that the presence of the 0 indicates the wiring was
 *       all hooked up.
 */
class GoogleExecutorTraitsSpec extends Specification {
  class Example implements GoogleExecutorTraits {
    MockClock clock = new MockClock()
    MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock)
  }

  private static Tags toTags(Map<String, String> map) {
    Tags.of(map.collect { k, v -> Tag.of(k.toString(), v.toString()) })
  }

  void "increment success timer"() {
    given:
      def example = new Example()
      def registry = example.registry
      def lastStatusCode = "0" // see NOTE above
      def lastStatus = "0xx"
      def request = Mock(AbstractGoogleClientRequest)

      // See note as to why this is 0
      def tags = GoogleApiTestUtils.makeTraitsTagMap("TestApi", 0, [account: UNKNOWN_ACCOUNT, random: "xyz"])

    when:
      // Put an existing timer with data into the registry to show accumulation
      registry.timer("google.api", toTags(tags)).record(3, TimeUnit.NANOSECONDS)
      example.timeExecute(request, "TestApi", "random", "xyz")

    then:
      tags == [api: "TestApi", success: "true", statusCode: lastStatusCode, status: lastStatus,
               random: "xyz", account: UNKNOWN_ACCOUNT]
      1 * request.execute() >> { example.clock.add(456, TimeUnit.NANOSECONDS) }
      registry.timer("google.api", toTags(tags)).count() == 1 + 1
      registry.timer("google.api", toTags(tags)).totalTime(TimeUnit.NANOSECONDS) == 3 + 456
      registry.getMeters().count { it instanceof Timer } == 1
  }

  void "increment Exception failure timer"() {
    setup:
    def example = new Example()
    def registry = example.registry
    def request = Mock(AbstractGoogleClientRequest)
    def tags = GoogleApiTestUtils.makeTraitsTagMap("TestApi", 543, [account: UNKNOWN_ACCOUNT])

    when:
    registry.timer("google.api", toTags(tags)).record(3, TimeUnit.NANOSECONDS)
    example.timeExecute(request, "TestApi")

    then:
    tags == [api: "TestApi", success: "false", statusCode: "543", status: "5xx", account: UNKNOWN_ACCOUNT]
    1 * request.execute() >> {
        example.clock.add(123, TimeUnit.NANOSECONDS)
        throw GoogleApiTestUtils.makeHttpResponseException(543)
    }
    thrown(HttpResponseException)
    registry.timer("google.api", toTags(tags)).count() == 1 + 1
    registry.timer("google.api", toTags(tags)).totalTime(TimeUnit.NANOSECONDS) == 123 + 3
    registry.getMeters().count { it instanceof Timer } == 1
  }

  void "increment generic failure timer and timers accumulate"() {
    setup:
      def example = new Example()
      def registry = example.registry
      def request = Mock(AbstractGoogleClientRequest)
      def tags = GoogleApiTestUtils.makeTraitsTagMap("TestApi", -1, [account: UNKNOWN_ACCOUNT])

    when:
      registry.timer("google.api", toTags(tags)).record(3, TimeUnit.NANOSECONDS)
      example.timeExecute(request, "TestApi")

    then:
      tags == [api: "TestApi", success: "false", statusCode: "-1", status: "-xx", account: UNKNOWN_ACCOUNT]
      1 * request.execute() >> { example.clock.add(123, TimeUnit.NANOSECONDS); throw new NullPointerException() }
      thrown(NullPointerException)
      registry.timer("google.api", toTags(tags)).count() == 1 + 1
      registry.timer("google.api", toTags(tags)).totalTime(TimeUnit.NANOSECONDS) == 123 + 3
      registry.getMeters().count { it instanceof Timer } == 1
  }
}
