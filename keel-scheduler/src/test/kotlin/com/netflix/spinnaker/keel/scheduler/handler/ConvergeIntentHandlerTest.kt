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
import com.netflix.spinnaker.keel.IntentRepository
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import com.netflix.spinnaker.keel.orca.OrcaIntentLauncher
import com.netflix.spinnaker.keel.orca.OrcaLaunchedIntentResult
import com.netflix.spinnaker.keel.scheduler.ConvergeIntent
import com.netflix.spinnaker.keel.test.GenericTestIntentSpec
import com.netflix.spinnaker.keel.test.TestIntent
import com.netflix.spinnaker.q.Queue
import com.nhaarman.mockito_kotlin.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

object ConvergeIntentHandlerTest {

  val queue = mock<Queue>()
  val intentRepository = mock<IntentRepository>()
  val orcaIntentLauncher = mock<OrcaIntentLauncher>()
  val clock = Clock.fixed(Instant.ofEpochSecond(500), ZoneId.systemDefault())
  val registry = NoopRegistry()
  val applicationEventPublisher = mock<ApplicationEventPublisher>()

  val subject = ConvergeIntentHandler(queue, intentRepository, orcaIntentLauncher, clock, registry, applicationEventPublisher)

  @AfterEach
  fun cleanup() {
    reset(queue, intentRepository, orcaIntentLauncher, applicationEventPublisher)
  }

  @Test
  fun `should timeout intent if after timeout ttl`() {
    val message = ConvergeIntent(TestIntent(GenericTestIntentSpec("1", emptyMap())), 30000, 30000)

    subject.handle(message)

    verifyZeroInteractions(queue, intentRepository, orcaIntentLauncher)
  }

  @Test
  fun `should cancel converge if intent is stale and no longer exists`() {
    val message = ConvergeIntent(
      TestIntent(GenericTestIntentSpec("1", emptyMap())),
      clock.instant().minusSeconds(30).toEpochMilli(),
      clock.instant().plusSeconds(30).toEpochMilli()
    )

    subject.handle(message)

    verify(intentRepository).getIntent("test:1")
    verifyZeroInteractions(intentRepository)
  }

  @Test
  fun `should refresh intent state if stale`() {

    val message = ConvergeIntent(
      TestIntent(GenericTestIntentSpec("1", mapOf("refreshed" to false))),
      clock.instant().minusSeconds(30).toEpochMilli(),
      clock.instant().plusSeconds(30).toEpochMilli()
    )

    val refreshedIntent = TestIntent(GenericTestIntentSpec("1", mapOf("refreshed" to true)))
    whenever(intentRepository.getIntent("test:1")) doReturn refreshedIntent
    whenever(orcaIntentLauncher.launch(refreshedIntent)) doReturn
      OrcaLaunchedIntentResult(listOf("one"), ChangeSummary("foo"))

    subject.handle(message)

    verify(orcaIntentLauncher).launch(refreshedIntent)
    verifyNoMoreInteractions(orcaIntentLauncher)
  }
}
