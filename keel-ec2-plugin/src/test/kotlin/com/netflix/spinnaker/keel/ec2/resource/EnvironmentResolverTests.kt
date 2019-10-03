/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.keel.ec2.resource

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.api.NotificationFrequency.QUIET
import com.netflix.spinnaker.keel.api.NotificationType.SLACK
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.model.NotificationEvent.ORCHESTRATION_FAILED
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty
import java.time.Clock

class EnvironmentResolverTests : JUnit5Minutests {
  data class Fixture(
    val clock: Clock = Clock.systemDefaultZone(),
    val deliveryConfigRepository: InMemoryDeliveryConfigRepository = InMemoryDeliveryConfigRepository(clock),
    val environmentResolver: EnvironmentResolver = EnvironmentResolver(deliveryConfigRepository),
    val resource: Resource<DummyResourceSpec> = resource()
  )

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    context("no environments") {
      test("empty list gets returned") {
        expectThat(environmentResolver.getNotificationsFor(ResourceId("blah"))).isEmpty()
      }
    }

    context("an environment exists") {
      before {
        val notification = NotificationConfig(SLACK, "#my-channel", QUIET)
        val env = Environment(
          name = "test",
          resources = setOf(resource),
          notifications = setOf(notification)
        )
        val deliveryConfig = DeliveryConfig(name = "keel", application = "keel", environments = setOf(env))
        deliveryConfigRepository.store(deliveryConfig)
      }

      test("notifications get retrieved") {
        val n = environmentResolver.getNotificationsFor(resource.id)
        expect {
          that(n).isNotEmpty()
          that(n.size).isEqualTo(1)
          that(n.first().`when`).isEqualTo(listOf(ORCHESTRATION_FAILED.text()))
          that(n.first().message).isEqualTo(
            mapOf(ORCHESTRATION_FAILED.text() to ORCHESTRATION_FAILED.notificationMessage())
          )
        }
      }
    }
  }
}
