/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.q

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.q.Activator
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.q.QueueCallback
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode
import org.jetbrains.spek.subject.SubjectSpek

class QueueShovelTest : SubjectSpek<QueueShovel>({

  val queue: Queue = mock()
  val previousQueue: Queue = mock()
  val registry = NoopRegistry()
  val activator = object : Activator {
    override val enabled = true
  }

  subject(CachingMode.GROUP) {
    QueueShovel(queue, previousQueue, registry, activator)
  }

  describe("polling the previous queue") {
    val message = StartExecution(PIPELINE, "1", "spinnaker")

    beforeGroup {
      whenever(previousQueue.poll(any())) doAnswer {
        it.getArgument<QueueCallback>(0)(message, {})
      }
    }

    on("the shovel poll method is invoked") {
      subject.migrateOne()
    }

    it("pushes the message onto the current queue") {
      verify(queue).push(message)
    }
  }
})
