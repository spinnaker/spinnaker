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

import com.netflix.spinnaker.orca.ExecutionStatus.PAUSED
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.q.Queue
import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek

object PauseStageHandlerTest : SubjectSpek<PauseStageHandler>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()

  subject(GROUP) {
    PauseStageHandler(queue, repository)
  }

  fun resetMocks() = reset(queue, repository)

  describe("when a stage is paused") {
    val pipeline = pipeline {
      application = "foo"
      stage {
        refId = "1"
        type = singleTaskStage.type
      }
      stage {
        refId = "2"
        requisiteStageRefIds = listOf("1")
        type = singleTaskStage.type
      }
    }
    val message = PauseStage(PIPELINE, pipeline.id, "foo", pipeline.stages.first().id)

    beforeGroup {
      whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
    }

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      subject.handle(message)
    }

    it("updates the stage state") {
      verify(repository).storeStage(check {
        assertThat(it.status).isEqualTo(PAUSED)
        assertThat(it.endTime).isNull()
      })
    }

    it("does not take any further action") {
      verifyZeroInteractions(queue)
    }
  }

  context("when a synthetic stage is paused") {
    val pipeline = pipeline {
      application = "foo"
      stage {
        refId = "1"
        type = stageWithSyntheticBefore.type
        stageWithSyntheticBefore.buildSyntheticStages(this)
      }
    }
    val message = PauseStage(pipeline.type, pipeline.id, "foo", pipeline.stageByRef("1<1").id)

    beforeGroup {
      whenever(repository.retrieve(pipeline.type, message.executionId)) doReturn pipeline
    }

    action("the handler receives a message") {
      subject.handle(message)
    }

    afterGroup(::resetMocks)

    it("rolls up to the parent stage") {
      verify(queue).push(message.copy(stageId = pipeline.stageByRef("1").id))
      verifyNoMoreInteractions(queue)
    }
  }
})
