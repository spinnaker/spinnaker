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
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.q.Activator
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.q.QueueCallback
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode
import org.jetbrains.spek.subject.SubjectSpek
import org.mockito.ArgumentMatchers.anyString

class QueueShovelTest : SubjectSpek<QueueShovel>({
  val message = StartExecution(PIPELINE, "executionId", "app")
  val queue: Queue = mock()
  val registry = NoopRegistry()
  val ackCallback = mock<() -> Unit>()
  val previousQueue: Queue = mock()
  val executionRepository: ExecutionRepository = mock()
  val execution: PipelineExecution = mock()

  val activator = object : Activator {
    override val enabled = true
  }

  subject(CachingMode.GROUP) {
    QueueShovel(queue, previousQueue, registry, activator, DynamicConfigService.NOOP, executionRepository)
  }

  beforeEachTest {
    whenever(previousQueue.poll(any())).doAnswer {
      it.getArgument<QueueCallback>(0)(message, ackCallback)
    }

    whenever(execution.partition).doReturn("some-partition")
  }

  afterEachTest {
    reset(executionRepository, queue, ackCallback, execution)
  }

  describe("polling the previous queue") {
    beforeGroup {
      whenever(executionRepository.retrieve(any(), anyString())).doReturn(execution)
      whenever(executionRepository.handlesPartition(anyString())).doReturn(true)
    }

    on("the shovel poll method is invoked") {
      subject.migrateOne()

      it("pushes the message onto the current queue and acks it") {
        verify(queue).push(message)
        verify(ackCallback).invoke()
      }
    }
  }

  describe("dealing with a foreign execution") {
    beforeGroup {
      whenever(executionRepository.retrieve(any(), anyString())).doReturn(execution)
      whenever(executionRepository.handlesPartition(anyString())).doReturn(false)
      whenever(executionRepository.partition).thenReturn("local-partition")
    }

    on("a poll cycle where the message belongs to a foreign execution") {
      subject.migrateOne()

      it("overwrites the partition to be the local one") {
        verify(execution).partition = "local-partition"
        verify(executionRepository).store(execution)
        verify(queue).push(message)
      }
    }
  }

  describe("dealing with execution repository read errors") {
    beforeGroup {
      whenever(executionRepository.retrieve(any(), anyString())).thenThrow(ExecutionNotFoundException("womp womp"))
    }

    on("a poll cycle") {
      subject.migrateOne()

      it("leaves the message on the old queue") {
        // not pushed
        verifyZeroInteractions(queue)

        // not acked
        verifyZeroInteractions(ackCallback)

        // execution not updated
        verify(executionRepository, never()).handlesPartition(anyString())
        verify(executionRepository, never()).store(any())
      }
    }
  }

  describe("dealing with execution repository write errors") {
    beforeGroup {
      whenever(executionRepository.retrieve(any(), anyString())).doReturn(execution)
      whenever(executionRepository.handlesPartition(anyString())).doReturn(false)
      whenever(executionRepository.partition).thenReturn("local-partition")
      whenever(executionRepository.store(execution)).thenThrow(RuntimeException("something unexpected"))
    }

    on("a poll cycle") {
      subject.migrateOne()

      it("leaves the message on the old queue") {
        // attempted to transfer ownership
        verify(execution).partition = "local-partition"
        verify(executionRepository).store(execution)

        // not pushed
        verifyZeroInteractions(queue)

        // not acked
        verifyZeroInteractions(ackCallback)
      }
    }
  }
})
