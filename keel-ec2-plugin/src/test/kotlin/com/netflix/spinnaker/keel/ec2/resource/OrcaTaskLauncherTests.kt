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
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.model.NotificationEvent.ORCHESTRATION_FAILED
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.plugin.OrcaTaskLauncher
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import java.time.Clock
import kotlinx.coroutines.runBlocking
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty

class OrcaTaskLauncherTests : JUnit5Minutests {
  class Fixture {
    val clock = Clock.systemDefaultZone()
    val orcaService: OrcaService = mockk()
    val deliveryConfigRepository = InMemoryDeliveryConfigRepository(clock)
    val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
    val taskLauncher = OrcaTaskLauncher(orcaService, deliveryConfigRepository, publisher)
    val resource: Resource<DummyResourceSpec> = resource()
    val request = slot<OrchestrationRequest>()
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    before {
      coEvery {
        orcaService.orchestrate(any(), capture(request))
      } returns TaskRefResponse("/tasks/${randomUID()}")
    }

    context("an environment exists") {
      before {
        val notification = NotificationConfig(SLACK, "#my-channel", QUIET)
        val env = Environment(
          name = "test",
          resources = setOf(resource),
          notifications = setOf(notification)
        )
        val deliveryConfig = DeliveryConfig(
          name = "keel",
          application = "keel",
          serviceAccount = "keel@spinnaker",
          environments = setOf(env)
        )
        deliveryConfigRepository.store(deliveryConfig)

        runBlocking {
          taskLauncher.submitJob(resource, "task description", "correlate this", mapOf())
        }
      }

      test("notifications get retrieved") {
        expectThat(request.captured.trigger.notifications) {
          isNotEmpty()
          hasSize(1)
          first().get { `when` }.isEqualTo(listOf(ORCHESTRATION_FAILED.text()))
          first().get { message }.isEqualTo(
            mapOf(ORCHESTRATION_FAILED.text() to ORCHESTRATION_FAILED.notificationMessage())
          )
        }
      }
    }
  }
}
