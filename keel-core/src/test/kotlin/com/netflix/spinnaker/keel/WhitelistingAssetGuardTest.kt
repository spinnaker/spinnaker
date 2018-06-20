/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.keel

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.config.ApplicationAssetGuardProperties
import com.netflix.spinnaker.keel.event.AfterAssetUpsertEvent
import com.netflix.spinnaker.keel.event.AssetAwareEvent
import com.netflix.spinnaker.keel.event.AssetConvergeTimeoutEvent
import com.netflix.spinnaker.keel.event.BeforeAssetUpsertEvent
import com.netflix.spinnaker.keel.exceptions.GuardConditionFailed
import com.netflix.spinnaker.keel.test.ApplicationAwareTestAssetSpec
import com.netflix.spinnaker.keel.test.GenericTestAssetSpec
import com.netflix.spinnaker.keel.test.TestAsset
import org.junit.jupiter.api.Test

// TODO rz - abstract to individually test guards
object WhitelistingAssetGuardTest {

  @Test
  fun `should match event types`() {

    ApplicationAssetGuard(NoopRegistry(), ApplicationAssetGuardProperties()).run {
      assert(matchesEventTypes(BeforeAssetUpsertEvent(passingAsset)))
      assert(!matchesEventTypes(AfterAssetUpsertEvent(passingAsset)))
    }
  }

  @Test
  fun `should fail when given un-whitelisted value`() {
    val subject = ApplicationAssetGuard(
      NoopRegistry(),
      ApplicationAssetGuardProperties().apply {
        whitelist = mutableListOf("spintest")
      }
    )

    assertGuardConditionFailed(subject, BeforeAssetUpsertEvent(failingAsset))

    subject.onAssetAwareEvent(BeforeAssetUpsertEvent(passingAsset))
    subject.onAssetAwareEvent(BeforeAssetUpsertEvent(ignoredAsset))
  }

  @Test
  fun `should ignore un-supported events`() {
    val subject = ApplicationAssetGuard(
      NoopRegistry(),
      ApplicationAssetGuardProperties().apply {
        whitelist = mutableListOf("spintest")
      }
    )

    subject.onAssetAwareEvent(AssetConvergeTimeoutEvent(failingAsset))
    subject.onAssetAwareEvent(AssetConvergeTimeoutEvent(passingAsset))
    subject.onAssetAwareEvent(AssetConvergeTimeoutEvent(ignoredAsset))
  }

  private fun assertGuardConditionFailed(subject: WhitelistingAssetGuard, event: AssetAwareEvent) {
    assertThat(
      { subject.onAssetAwareEvent(event) },
      throws<GuardConditionFailed>()
    )
  }

  val failingAsset = TestAsset(ApplicationAwareTestAssetSpec(
    id = "id",
    application = "KEEL"
  ))

  val passingAsset = TestAsset(ApplicationAwareTestAssetSpec(
    id = "id",
    application = "spintest"
  ))

  val ignoredAsset = TestAsset(GenericTestAssetSpec("id"))
}
