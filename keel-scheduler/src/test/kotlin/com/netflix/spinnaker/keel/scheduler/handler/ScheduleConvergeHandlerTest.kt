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
import com.netflix.spinnaker.keel.AssetRepository
import com.netflix.spinnaker.keel.scheduler.ConvergeAsset
import com.netflix.spinnaker.keel.scheduler.ScheduleConvergence
import com.netflix.spinnaker.keel.scheduler.ScheduleService
import com.netflix.spinnaker.keel.test.GenericTestAssetSpec
import com.netflix.spinnaker.keel.test.TestAsset
import com.netflix.spinnaker.q.Queue
import com.nhaarman.mockito_kotlin.*
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

object ScheduleConvergeHandlerTest {

  val queue = mock<Queue>()
  val properties = ScheduleConvergeHandlerProperties(10000, 60000, 30000)
  val assetRepository = mock<AssetRepository>()
  val registry = NoopRegistry()
  val clock = Clock.fixed(Instant.ofEpochMilli(0), ZoneId.systemDefault())
  val scheduleService = ScheduleService(queue, properties, clock)
  val applicationEventPublisher = mock<ApplicationEventPublisher>()

  val subject = ScheduleConvergeHandler(queue, scheduleService, assetRepository, emptyList(), registry, applicationEventPublisher)

  @Test
  fun `should push converge messages for each active asset`() {
    val message = ScheduleConvergence()

    val asset1 = TestAsset(GenericTestAssetSpec("1", emptyMap()))
    val asset2 = TestAsset(GenericTestAssetSpec("2", emptyMap()))
    whenever(assetRepository.getAssets(any())) doReturn listOf(asset1, asset2)

    subject.handle(message)

    verify(queue).push(ConvergeAsset(asset1, 10000, 60000))
    verify(queue).push(ConvergeAsset(asset2, 10000, 60000))
  }
}
