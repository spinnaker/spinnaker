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

package com.netflix.spinnaker.orca.q.handler

import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.events.ExecutionComplete
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.spek.shouldEqual
import com.netflix.spinnaker.q.Queue
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode
import org.jetbrains.spek.subject.SubjectSpek
import org.springframework.context.ApplicationEventPublisher

object CancelExecutionHandlerTest : SubjectSpek<CancelExecutionHandler>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val publisher: ApplicationEventPublisher = mock()

  subject(CachingMode.GROUP) {
    CancelExecutionHandler(queue, repository, publisher)
  }

  fun resetMocks() = reset(queue, repository, publisher)

  describe("cancelling an execution") {
    given("there are no paused stages") {
      val pipeline = pipeline {
        application = "covfefe"
        stage {
          refId = "1"
          type = "whatever"
          status = RUNNING
        }
      }
      val message = CancelExecution(pipeline, "fzlem@netflix.com", "because")

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving $message") {
        subject.handle(message)
      }

      it("sets the canceled flag on the pipeline") {
        verify(repository).cancel(pipeline.id, "fzlem@netflix.com", "because")
      }

      it("it triggers a reevaluate") {
        verify(queue).push(RescheduleExecution(pipeline))
      }

      it("publishes an execution complete event") {
        verify(publisher).publishEvent(check<ExecutionComplete> {
          it.executionType shouldEqual pipeline.type
          it.executionId shouldEqual pipeline.id
          it.status shouldEqual CANCELED
        })
      }

      it("does not send any further messages") {
        verifyZeroInteractions(queue)
      }
    }

    given("there are paused stages") {
      val pipeline = pipeline {
        application = "covfefe"
        stage {
          refId = "1"
          type = "whatever"
          status = PAUSED
        }
      }
      val message = CancelExecution(pipeline, "fzlem@netflix.com", "because")

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving $message") {
        subject.handle(message)
      }

      it("sets the canceled flag on the pipeline") {
        verify(repository).cancel(pipeline.id, "fzlem@netflix.com", "because")
      }

      it("unpauses the paused stage") {
        verify(queue).push(ResumeStage(pipeline.stageByRef("1")))
      }
    }
  }
})
