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

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.should.shouldMatch
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.PauseStage
import com.netflix.spinnaker.orca.q.PauseTask
import com.netflix.spinnaker.orca.q.buildTasks
import com.netflix.spinnaker.orca.q.multiTaskStage
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.spek.shouldEqual
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek

object PauseTaskHandlerTest : SubjectSpek<PauseTaskHandler>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()

  subject(GROUP) {
    PauseTaskHandler(queue, repository)
  }

  fun resetMocks() = reset(queue, repository)

  describe("when a task is paused") {
    val pipeline = pipeline {
      application = "foo"
      stage {
        type = multiTaskStage.type
        multiTaskStage.buildTasks(this)
      }
    }
    val message = PauseTask(pipeline.type, pipeline.id, "foo", pipeline.stages.first().id, "1")

    beforeGroup {
      whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
    }

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      subject.handle(message)
    }

    it("updates the task state in the stage") {
      verify(repository).storeStage(check {
        it.tasks.first().apply {
          status shouldEqual ExecutionStatus.PAUSED
          endTime shouldMatch absent()
        }
      })
    }

    it("pauses the stage") {
      verify(queue).push(PauseStage(message))
    }
  }
})
