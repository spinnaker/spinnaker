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

import com.google.api.client.googleapis.services.AbstractGoogleClientRequest

import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spectator.api.ManualClock
import com.netflix.spectator.api.Registry

import spock.lang.Specification
import java.util.concurrent.TimeUnit


/**
 * Note: These tests always create a statusCode of 0.
 *       Ideally we'd inject a 200 on success and something else on error.
 *       However it is not possible to do this. The Google API status codes
 *       appear to come from the HTTP response headers, so would need to be
 *       injected there. But to do that requires all kinds of support machinery
 *       which isnt even clear how to do. You'd probably need to mock out the
 *       entire transport. The clouddriver implementation does not have a hook
 *       for that. We could add one in, but using it seems really hard. Instead
 *       we'll assume that the status codes will be correct (since the underlying
 *       API is stable), and that the presence of the 0 indicates the wiring was
 *       all hooked up.
 */
class GoogleExecutorTraitsSpec extends Specification {
  class Example implements GoogleExecutorTraits {
    ManualClock clock = new ManualClock(777, 1000)  // wallTime is not used
    Registry registry = new DefaultRegistry(clock)
  }

  final String SEE_NOTE = "0"  // See class note

  void "increment success timer"() {
    setup:
      def example = new Example()
      def registry = example.registry
      def request = Mock(AbstractGoogleClientRequest)
      def tags = [api: "TestApi", success: "true", statusCode: SEE_NOTE, random: "xyz"]

    when:
      // Put an existing timer with data into the registry to show accumulation
      registry.timer(registry.createId("google.api", tags)).record(456, TimeUnit.NANOSECONDS)
      example.timeExecute(request, "TestApi", "random", "xyz")

    then:
      1 * request.execute() >> { example.clock.setMonotonicTime(3 + 1000) }
      registry.timer(registry.createId("google.api", tags)).count() == 1 + 1
      registry.timer(registry.createId("google.api", tags)).totalTime() == 3 + 456
      registry.timers().count() == 1
  }

  void "increment IOException failure timer"() {
    setup:
    def example = new Example()
    def registry = example.registry
    def request = Mock(AbstractGoogleClientRequest)
    def tags = [api: "TestApi", success: "false", statusCode: SEE_NOTE]

    when:
    example.timeExecute(request, "TestApi")

    then:
    1 * request.execute() >> { example.clock.setMonotonicTime(123 + 1000); throw new IOException() }
    thrown(IOException)
    registry.timer(registry.createId("google.api", tags)).count() == 1
    registry.timer(registry.createId("google.api", tags)).totalTime() == 123
    registry.timers().count() == 1
  }

  void "increment generic failure timer and timers accumulate"() {
    setup:
      def example = new Example()
      def registry = example.registry
      def request = Mock(AbstractGoogleClientRequest)
      def tags = [api: "TestApi", success: "false", statusCode: SEE_NOTE]

    when:
      example.timeExecute(request, "TestApi")

    then:
      1 * request.execute() >> { example.clock.setMonotonicTime(123 + 1000); throw new NullPointerException() }
      thrown(NullPointerException)
      registry.timer(registry.createId("google.api", tags)).count() == 1
      registry.timer(registry.createId("google.api", tags)).totalTime() == 123
      registry.timers().count() == 1
  }
}
