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

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek

object ConfigurationErrorHandlerSpec : SubjectSpek<ConfigurationErrorHandler>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()

  subject(GROUP) {
    ConfigurationErrorHandler(queue, repository)
  }

  fun resetMocks() = reset(queue)

  setOf(
    InvalidExecutionId(Pipeline::class.java, "1", "foo"),
    InvalidStageId(Pipeline::class.java, "1", "foo", "1"),
    InvalidTaskType(Pipeline::class.java, "1", "foo", "1", InvalidTask::class.java.name)
  ).forEach { message ->
    describe("handing a ${message.javaClass.simpleName} event") {
      afterGroup(::resetMocks)

      action("the handler receives a message") {
        subject.handle(message)
      }

      it("marks the execution as terminal") {
        verify(queue).push(CompleteExecution(
          Pipeline::class.java,
          message.executionId,
          "foo"
        ))
      }
    }
  }
})
