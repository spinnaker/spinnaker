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

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.plugin.TaskLauncher
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import java.time.Clock

class TaskLauncherTests : JUnit5Minutests {
  object Fixture {
    val clock = Clock.systemDefaultZone()
    val orcaService: OrcaService = mockk()
    val deliveryConfigRepository = InMemoryDeliveryConfigRepository(clock)
    val taskLauncher = TaskLauncher(orcaService, deliveryConfigRepository)
    val resource: Resource<DummyResourceSpec> = resource()
  }

  fun tests() = rootContext<Fixture> {
//    fixture { Fixture }
//
//    context("no environments") {
//      test("empty list gets returned") {
//        expectThat(taskLauncher.getNotificationsFor(ResourceId("blah"))).isEmpty()
//      }
//    }
//
//    context("an environment exists") {
//      before {
//        val notification = NotificationConfig(SLACK, "#my-channel", QUIET)
//        val env = Environment(
//          name = "test",
//          resources = setOf(resource),
//          notifications = setOf(notification)
//        )
//        val deliveryConfig = DeliveryConfig(name = "keel", application = "keel", environments = setOf(env))
//        deliveryConfigRepository.store(deliveryConfig)
//      }
//
//      test("notifications get retrieved") {
//        val n = taskLauncher.getNotificationsFor(resource.id)
//        expect {
//          that(n).isNotEmpty()
//          that(n.size).isEqualTo(1)
//          that(n.first().`when`).isEqualTo(listOf(ORCHESTRATION_FAILED.text()))
//          that(n.first().message).isEqualTo(
//            mapOf(ORCHESTRATION_FAILED.text() to ORCHESTRATION_FAILED.notificationMessage())
//          )
//        }
//      }
//    }
  }
}
