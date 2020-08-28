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

import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.api.NotificationFrequency.quiet
import com.netflix.spinnaker.keel.api.NotificationType.slack
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.core.api.randomUID
import com.netflix.spinnaker.keel.model.NotificationEvent.ORCHESTRATION_FAILED
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.OrcaTaskLauncher
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty

class OrcaTaskLauncherTests : JUnit5Minutests {
  class Fixture {
    val orcaService: OrcaService = mockk()
    val publisher: EventPublisher = mockk(relaxUnitFun = true)
    val combinedRepository = mockk<KeelRepository>()
    val taskLauncher = OrcaTaskLauncher(orcaService, combinedRepository, publisher)
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
        val notification = NotificationConfig(slack, "#my-channel", quiet)
        val env = Environment(
          name = "test",
          resources = setOf(resource),
          notifications = setOf(notification)
        )
        every { combinedRepository.environmentFor(resource.id) } returns env

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
