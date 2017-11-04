/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.keel.scheduler.handler

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.ScheduleConvergeHandlerProperties
import com.netflix.spinnaker.keel.IntentRepository
import com.netflix.spinnaker.keel.scheduler.ConvergeIntent
import com.netflix.spinnaker.keel.scheduler.ScheduleConvergence
import com.netflix.spinnaker.keel.test.TestIntent
import com.netflix.spinnaker.keel.test.TestIntentSpec
import com.netflix.spinnaker.q.Queue
import spock.lang.Specification
import spock.lang.Subject

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class ScheduleConvergeHandlerSpec extends Specification {

  Queue queue = Mock()
  def properties = new ScheduleConvergeHandlerProperties(10000, 60000, 30000)
  IntentRepository intentRepository = Mock()
  Registry registry = new NoopRegistry()
  Clock clock = Clock.fixed(Instant.ofEpochMilli(0), ZoneId.systemDefault())

  @Subject subject = new ScheduleConvergeHandler(queue, properties, intentRepository, registry, clock)

  def "should push converge messages for each active intent"() {
    given:
    def message = new ScheduleConvergence()

    def intent1 = new TestIntent(new TestIntentSpec("1", [:]))
    def intent2 = new TestIntent(new TestIntentSpec("2", [:]))

    when:
    subject.handle(message)

    then:
    1 * intentRepository.getIntents(_) >> { [intent1, intent2] }
    1 * queue.push(new ConvergeIntent(intent1, 10000, 60000))
    1 * queue.push(new ConvergeIntent(intent2,  10000, 60000))
    1 * queue.push(message, Duration.ofMillis(30000))
  }
}
