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
import com.netflix.spinnaker.keel.IntentActivityRepository
import com.netflix.spinnaker.keel.IntentRepository
import com.netflix.spinnaker.keel.orca.OrcaIntentLauncher
import com.netflix.spinnaker.keel.orca.OrcaLaunchedIntentResult
import com.netflix.spinnaker.keel.scheduler.ConvergeIntent
import com.netflix.spinnaker.keel.test.TestIntent
import com.netflix.spinnaker.keel.test.TestIntentSpec
import com.netflix.spinnaker.q.Queue
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification
import spock.lang.Subject

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class ConvergeIntentHandlerSpec extends Specification {

  Queue queue = Mock()
  IntentRepository intentRepository = Mock()
  IntentActivityRepository intentActivityRepository = Mock()
  OrcaIntentLauncher orcaIntentLauncher = Mock()
  Clock clock = Clock.fixed(Instant.ofEpochSecond(500), ZoneId.systemDefault())
  Registry registry = new NoopRegistry()
  ApplicationEventPublisher applicationEventPublisher = Mock()

  @Subject subject = new ConvergeIntentHandler(queue, intentRepository, intentActivityRepository, orcaIntentLauncher, clock, registry, applicationEventPublisher)

  def "should timeout intent if after timeout ttl"() {
    given:
    def message = new ConvergeIntent(new TestIntent(new TestIntentSpec("1", [:])), 30000, 30000)

    when:
    subject.handle(message)

    then:
    0 * _
  }

  def "should cancel converge if intent is stale and no longer exists"() {
    given:
    def message = new ConvergeIntent(
      new TestIntent(new TestIntentSpec("1", [:])),
      clock.instant().minusSeconds(30).toEpochMilli(),
      clock.instant().plusSeconds(30).toEpochMilli()
    )

    when:
    subject.handle(message)

    then:
    1 * intentRepository.getIntent("test:1") >> { null }
    0 * _
  }

  def "should refresh intent state if stale"() {
    given:
    def message = new ConvergeIntent(
      new TestIntent(new TestIntentSpec("1", [refreshed: false])),
      clock.instant().minusSeconds(30).toEpochMilli(),
      clock.instant().plusSeconds(30).toEpochMilli()
    )

    def refreshedIntent = new TestIntent(new TestIntentSpec("1", [refreshed: true]))

    when:
    subject.handle(message)

    then:
    1 * intentRepository.getIntent("test:1") >> { refreshedIntent }
    1 * orcaIntentLauncher.launch(refreshedIntent) >> { new OrcaLaunchedIntentResult(["one"]) }
    1 * intentActivityRepository.addOrchestrations("test:1", ["one"])
    0 * _
  }
}
