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
import com.netflix.spinnaker.keel.AssetRepository
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import com.netflix.spinnaker.keel.orca.OrcaAssetLauncher
import com.netflix.spinnaker.keel.orca.OrcaLaunchedAssetResult
import com.netflix.spinnaker.keel.scheduler.ConvergeAsset
import com.netflix.spinnaker.keel.test.GenericTestAssetSpec
import com.netflix.spinnaker.keel.test.TestAsset
import com.netflix.spinnaker.q.Queue
import com.nhaarman.mockito_kotlin.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

object ConvergeAssetHandlerTest {

  val queue = mock<Queue>()
  val assetRepository = mock<AssetRepository>()
  val orcaAssetLauncher = mock<OrcaAssetLauncher>()
  val clock = Clock.fixed(Instant.ofEpochSecond(500), ZoneId.systemDefault())
  val registry = NoopRegistry()
  val applicationEventPublisher = mock<ApplicationEventPublisher>()

  val subject = ConvergeAssetHandler(queue, assetRepository, orcaAssetLauncher, clock, registry, applicationEventPublisher)

  @AfterEach
  fun cleanup() {
    reset(queue, assetRepository, orcaAssetLauncher, applicationEventPublisher)
  }

  @Test
  fun `should timeout asset if after timeout ttl`() {
    val message = ConvergeAsset(TestAsset(GenericTestAssetSpec("1", emptyMap())), 30000, 30000)

    subject.handle(message)

    verifyZeroInteractions(queue, assetRepository, orcaAssetLauncher)
  }

  @Test
  fun `should cancel converge if asset is stale and no longer exists`() {
    val message = ConvergeAsset(
      TestAsset(GenericTestAssetSpec("1", emptyMap())),
      clock.instant().minusSeconds(30).toEpochMilli(),
      clock.instant().plusSeconds(30).toEpochMilli()
    )

    subject.handle(message)

    verify(assetRepository).getAsset("test:1")
    verifyZeroInteractions(assetRepository)
  }

  @Test
  fun `should refresh asset state if stale`() {

    val message = ConvergeAsset(
      TestAsset(GenericTestAssetSpec("1", mapOf("refreshed" to false))),
      clock.instant().minusSeconds(30).toEpochMilli(),
      clock.instant().plusSeconds(30).toEpochMilli()
    )

    val refreshedAsset = TestAsset(GenericTestAssetSpec("1", mapOf("refreshed" to true)))
    whenever(assetRepository.getAsset("test:1")) doReturn refreshedAsset
    whenever(orcaAssetLauncher.launch(refreshedAsset)) doReturn
      OrcaLaunchedAssetResult(listOf("one"), ChangeSummary("foo"))

    subject.handle(message)

    verify(orcaAssetLauncher).launch(refreshedAsset)
    verifyNoMoreInteractions(orcaAssetLauncher)
  }
}
