/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.q.discovery

import com.netflix.spinnaker.kork.discovery.DiscoveryStatusChangeEvent
import com.netflix.spinnaker.kork.discovery.InstanceStatus
import com.netflix.spinnaker.kork.discovery.InstanceStatus.OUT_OF_SERVICE
import com.netflix.spinnaker.kork.discovery.InstanceStatus.UP
import com.netflix.spinnaker.kork.discovery.RemoteStatusChangedEvent
import com.netflix.spinnaker.spek.and
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it

object DiscoveryActivatorTest : Spek({

  describe("a discovery-activated poller") {

    val subject = DiscoveryActivator()

    describe("by default") {
      it("is disabled") {
        assertThat(subject.enabled).isFalse()
      }
    }

    given("the instance is up in discovery") {
      beforeGroup {
        subject.triggerEvent(OUT_OF_SERVICE, UP)
      }

      it("is enabled") {
        assertThat(subject.enabled).isTrue()
      }

      and("the instance goes out of service") {
        beforeGroup {
          subject.triggerEvent(UP, OUT_OF_SERVICE)
        }

        it("is disabled again") {
          assertThat(subject.enabled).isFalse()
        }
      }
    }
  }
})

private fun DiscoveryActivator.triggerEvent(from: InstanceStatus, to: InstanceStatus) =
  onApplicationEvent(event(from, to))

private fun event(from: InstanceStatus, to: InstanceStatus) =
  RemoteStatusChangedEvent(DiscoveryStatusChangeEvent(from, to))
