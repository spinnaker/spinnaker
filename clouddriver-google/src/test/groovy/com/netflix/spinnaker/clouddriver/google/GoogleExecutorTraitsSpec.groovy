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

import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spectator.api.ManualClock
import com.netflix.spectator.api.Registry
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
    ManualClock clock = new ManualClock(777, 1000)  // wallTime is not used
    Registry registry = new DefaultRegistry(clock)
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
      registry.timer(registry.createId("google.api", tags)).record(3, TimeUnit.NANOSECONDS)
      example.timeExecute(request, "TestApi", "random", "xyz")

    then:
      tags == [api: "TestApi", success: "true", statusCode: lastStatusCode, status: lastStatus,
               random: "xyz", account: UNKNOWN_ACCOUNT]
      1 * request.execute() >> { example.clock.setMonotonicTime(456 + 1000) }
      registry.timer(registry.createId("google.api", tags)).count() == 1 + 1
      registry.timer(registry.createId("google.api", tags)).totalTime() == 3 + 456
      registry.timers().count() == 1
  }

  void "increment Exception failure timer"() {
    setup:
    def example = new Example()
    def registry = example.registry
    def request = Mock(AbstractGoogleClientRequest)
    def tags = GoogleApiTestUtils.makeTraitsTagMap("TestApi", 543, [account: UNKNOWN_ACCOUNT])

    when:
    registry.timer(registry.createId("google.api", tags)).record(3, TimeUnit.NANOSECONDS)
    example.timeExecute(request, "TestApi")

    then:
    tags == [api: "TestApi", success: "false", statusCode: "543", status: "5xx", account: UNKNOWN_ACCOUNT]
    1 * request.execute() >> {
        example.clock.setMonotonicTime(123 + 1000);
        throw GoogleApiTestUtils.makeHttpResponseException(543)
    }
    thrown(HttpResponseException)
    registry.timer(registry.createId("google.api", tags)).count() == 1 + 1
    registry.timer(registry.createId("google.api", tags)).totalTime() == 123 + 3
    registry.timers().count() == 1
  }

  void "increment generic failure timer and timers accumulate"() {
    setup:
      def example = new Example()
      def registry = example.registry
      def request = Mock(AbstractGoogleClientRequest)
      def tags = GoogleApiTestUtils.makeTraitsTagMap("TestApi", -1, [account: UNKNOWN_ACCOUNT])

    when:
      registry.timer(registry.createId("google.api", tags)).record(3, TimeUnit.NANOSECONDS)
      example.timeExecute(request, "TestApi")

    then:
      tags == [api: "TestApi", success: "false", statusCode: "-1", status: "-xx", account: UNKNOWN_ACCOUNT]
      1 * request.execute() >> { example.clock.setMonotonicTime(123 + 1000); throw new NullPointerException() }
      thrown(NullPointerException)
      registry.timer(registry.createId("google.api", tags)).count() == 1 + 1
      registry.timer(registry.createId("google.api", tags)).totalTime() == 123 + 3
      registry.timers().count() == 1
  }
}
