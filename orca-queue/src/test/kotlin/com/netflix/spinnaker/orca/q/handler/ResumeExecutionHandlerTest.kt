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
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.ResumeExecution
import com.netflix.spinnaker.orca.q.ResumeStage
import com.netflix.spinnaker.orca.q.pipeline
import com.netflix.spinnaker.orca.q.stage
import com.netflix.spinnaker.q.Queue
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek

object ResumeExecutionHandlerTest : SubjectSpek<ResumeExecutionHandler>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()

  subject(GROUP) {
    ResumeExecutionHandler(queue, repository)
  }

  fun resetMocks() = reset(queue, repository)

  describe("resuming a paused execution") {
    val pipeline = pipeline {
      application = "spinnaker"
      status = PAUSED
      stage {
        refId = "1"
        status = SUCCEEDED
      }
      stage {
        refId = "2a"
        requisiteStageRefIds = listOf("1")
        status = PAUSED
      }
      stage {
        refId = "2b"
        requisiteStageRefIds = listOf("1")
        status = PAUSED
      }
      stage {
        refId = "3"
        requisiteStageRefIds = listOf("2a", "2b")
        status = NOT_STARTED
      }
    }
    val message = ResumeExecution(pipeline.type, pipeline.id, pipeline.application)

    beforeGroup {
      whenever(repository.retrieve(pipeline.type, pipeline.id)) doReturn pipeline
    }

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      subject.handle(message)
    }

    it("resumes all paused stages") {
      verify(queue).push(ResumeStage(message, pipeline.stageByRef("2a").id))
      verify(queue).push(ResumeStage(message, pipeline.stageByRef("2b").id))
      verifyNoMoreInteractions(queue)
    }
  }
})
