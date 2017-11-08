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

package com.netflix.spinnaker.orca.q

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek

object MessageHandlerTest : SubjectSpek<MessageHandler<*>>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val handleCallback: (Message) -> Unit = mock()

  subject(GROUP) {
    object : MessageHandler<ConfigurationError> {
      override val queue
        get() = queue

      override val repository
        get() = repository

      override val messageType = ConfigurationError::class.java

      override fun handle(message: ConfigurationError) {
        handleCallback.invoke(message)
      }
    }
  }

  fun resetMocks() = reset(queue, handleCallback)

  describe("when the handler is passed the wrong type of message") {
    val message = CompleteExecution(PIPELINE, "1", "foo")

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      assertThat(
        { subject.invoke(message) },
        throws<IllegalArgumentException>()
      )
    }

    it("does not invoke the handler") {
      verifyZeroInteractions(handleCallback)
    }
  }

  describe("when the handler is passed a sub-type of message") {
    val message = InvalidExecutionId(PIPELINE, "1", "foo")

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      subject.invoke(message)
    }

    it("does invoke the handler") {
      verify(handleCallback).invoke(message)
    }
  }
})
