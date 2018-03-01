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
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.fixture.task
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.ResumeStage
import com.netflix.spinnaker.orca.q.ResumeTask
import com.netflix.spinnaker.q.Queue
import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek

object ResumeStageHandlerTest : SubjectSpek<ResumeStageHandler>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()

  subject(GROUP) {
    ResumeStageHandler(queue, repository)
  }

  fun resetMocks() = reset(queue, repository)

  describe("resuming a paused execution") {
    val pipeline = pipeline {
      application = "spinnaker"
      status = RUNNING
      stage {
        refId = "1"
        status = PAUSED
        task {
          id = "1"
          status = SUCCEEDED
        }
        task {
          id = "2"
          status = PAUSED
        }
        task {
          id = "3"
          status = NOT_STARTED
        }
      }
    }
    val message = ResumeStage(pipeline.type, pipeline.id, pipeline.application, pipeline.stages.first().id)

    beforeGroup {
      whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
    }

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      subject.handle(message)
    }

    it("sets the stage status to running") {
      verify(repository).storeStage(check {
        assertThat(it.id).isEqualTo(message.stageId)
        assertThat(it.status).isEqualTo(RUNNING)
      })
    }

    it("resumes all paused tasks") {
      verify(queue).push(ResumeTask(message, "2"))
      verifyNoMoreInteractions(queue)
    }
  }
})
