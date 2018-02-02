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
import com.netflix.spinnaker.keel.event.AfterIntentUpsertEvent
import com.netflix.spinnaker.keel.event.BeforeIntentUpsertEvent
import com.netflix.spinnaker.keel.event.IntentAwareEvent
import com.netflix.spinnaker.keel.event.IntentConvergeTimeoutEvent
import com.netflix.spinnaker.keel.exceptions.GuardConditionFailed
import com.netflix.spinnaker.keel.test.ApplicationAwareTestIntentSpec
import com.netflix.spinnaker.keel.test.GenericTestIntentSpec
import com.netflix.spinnaker.keel.test.TestIntent
import org.junit.jupiter.api.Test

// TODO rz - abstract to individually test guards
object WhitelistingIntentGuardTest {

  @Test
  fun `should match event types`() {
    ApplicationIntentGuard(WhitelistingIntentGuardProperties()).run {
      assert(matchesEventTypes(BeforeIntentUpsertEvent(passingIntent)))
      assert(!matchesEventTypes(AfterIntentUpsertEvent(passingIntent)))
    }
  }

  @Test
  fun `should fail when given un-whitelisted value`() {
    val subject = ApplicationIntentGuard(
      WhitelistingIntentGuardProperties().apply {
        whitelist = listOf("spintest")
      }
    )

    assertGuardConditionFailed(subject, BeforeIntentUpsertEvent(failingIntent))

    subject.onApplicationEvent(BeforeIntentUpsertEvent(passingIntent))
    subject.onApplicationEvent(BeforeIntentUpsertEvent(ignoredIntent))
  }

  @Test
  fun `should ignore un-supported events`() {
    val subject = ApplicationIntentGuard(
      WhitelistingIntentGuardProperties().apply {
        whitelist = listOf("spintest")
      }
    )

    subject.onApplicationEvent(IntentConvergeTimeoutEvent(failingIntent))
    subject.onApplicationEvent(IntentConvergeTimeoutEvent(passingIntent))
    subject.onApplicationEvent(IntentConvergeTimeoutEvent(ignoredIntent))
  }

  private fun assertGuardConditionFailed(subject: WhitelistingIntentGuard, event: IntentAwareEvent) {
    assertThat(
      { subject.onApplicationEvent(event) },
      throws<GuardConditionFailed>()
    )
  }

  val failingIntent = TestIntent(ApplicationAwareTestIntentSpec(
    id = "id",
    application = "KEEL"
  ))

  val passingIntent = TestIntent(ApplicationAwareTestIntentSpec(
    id = "id",
    application = "spintest"
  ))

  val ignoredIntent = TestIntent(GenericTestIntentSpec("id"))
}
