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

package com.netflix.spinnaker.keel.scheduler.handler

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.keel.IntentActivityRepository
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.TaskDetailResponse
import com.netflix.spinnaker.keel.scheduler.MonitorOrchestrations
import com.netflix.spinnaker.q.Queue
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import org.junit.jupiter.api.Test
import java.time.Duration

object MonitorOrchestrationsHandlerTest {
  val queue = mock<Queue>()
  val intentActivityRepository = mock<IntentActivityRepository>()
  val orcaService = mock<OrcaService>()
  val registry = NoopRegistry()

  val subject = MonitorOrchestrationsHandler(queue, intentActivityRepository, orcaService, registry)

  val intentId = "Application:emilykeeltest"
  val orchestrationsStatusId = registry.createId("intent.orchestrations.status")
  val orchestrationId = "a36ccc2c-8f40-4e63-852c-8107dd819ada"

  @Test
  fun `succeeded message is recorded and cleared`() {

    val message = MonitorOrchestrations(intentId, "Application")

    whenever(intentActivityRepository.getCurrent(intentId))
      .doReturn(listOf(orchestrationId))
      .doReturn(emptyList<String>())
    whenever(orcaService.getTask(orchestrationId)) doReturn TaskDetailResponse(
      orchestrationId,
      "Converging on desired application state",
      "emilykeeltest",
      "1511998323980",
      "1511998324027",
      "1511998324552",
      OrcaExecutionStatus.SUCCEEDED)

    subject.handle(message)

    verifyNoMoreInteractions(queue)
  }

  @Test
  fun `running message is followed up on`() {
    val message = MonitorOrchestrations(intentId, "Application")

    whenever(intentActivityRepository.getCurrent(intentId)) doReturn listOf(orchestrationId)
    whenever(orcaService.getTask(orchestrationId)) doReturn TaskDetailResponse(
      orchestrationId,
      "Converging on desired application state",
      "emilykeeltest",
      "1511998323980",
      "1511998324027",
      "1511998324552",
      OrcaExecutionStatus.RUNNING)

    subject.handle(message)

    verify(queue).push(message, Duration.ofMillis(10000))
    verifyNoMoreInteractions(queue)
  }
}
