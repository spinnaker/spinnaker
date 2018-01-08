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
import com.netflix.spinnaker.config.ScheduleConvergeHandlerProperties
import com.netflix.spinnaker.keel.IntentRepository
import com.netflix.spinnaker.keel.scheduler.ConvergeIntent
import com.netflix.spinnaker.keel.scheduler.ScheduleConvergence
import com.netflix.spinnaker.keel.test.TestIntent
import com.netflix.spinnaker.keel.test.TestIntentSpec
import com.netflix.spinnaker.q.Queue
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

object ScheduleConvergeHandlerTest {

  val queue = mock<Queue>()
  val properties = ScheduleConvergeHandlerProperties(10000, 60000, 30000)
  val intentRepository = mock<IntentRepository>()
  val registry = NoopRegistry()
  val clock = Clock.fixed(Instant.ofEpochMilli(0), ZoneId.systemDefault())
  val applicationEventPublisher = mock<ApplicationEventPublisher>()

  val subject = ScheduleConvergeHandler(queue, properties, intentRepository, emptyList(), registry, clock, applicationEventPublisher)

  @Test
  fun `should push converge messages for each active intent`() {
    val message = ScheduleConvergence()

    val intent1 = TestIntent(TestIntentSpec("1", emptyMap()))
    val intent2 = TestIntent(TestIntentSpec("2", emptyMap()))
    whenever(intentRepository.getIntents(any())) doReturn listOf(intent1, intent2)

    subject.handle(message)

    verify(queue).push(ConvergeIntent(intent1, 10000, 60000))
    verify(queue).push(ConvergeIntent(intent2, 10000, 60000))
  }
}
