/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.orca.qos

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigSerivce
import com.netflix.spinnaker.orca.ExecutionStatus.BUFFERED
import com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.events.BeforeInitialExecutionPersist
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.qos.BufferAction.BUFFER
import com.netflix.spinnaker.orca.qos.BufferAction.ENQUEUE
import com.netflix.spinnaker.orca.qos.BufferState.ACTIVE
import com.netflix.spinnaker.orca.qos.BufferState.INACTIVE
import com.netflix.spinnaker.orca.qos.bufferstate.BufferStateSupplierProvider
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode
import org.jetbrains.spek.subject.SubjectSpek

class ExecutionBufferActuatorTest : SubjectSpek<ExecutionBufferActuator>({

  val configService: DynamicConfigSerivce = mock()
  val bufferStateSupplier: BufferStateSupplier = mock()
  val policy1: BufferPolicy = mock()
  val policy2: BufferPolicy = mock()

  subject(CachingMode.GROUP) {
    ExecutionBufferActuator(
      BufferStateSupplierProvider(listOf(bufferStateSupplier)),
      configService, NoopRegistry(), listOf(policy1, policy2)
    )
  }

  fun resetMocks() = reset(bufferStateSupplier, policy1, policy2)

  describe("buffering executions") {
    beforeGroup {
      whenever(configService.isEnabled(eq("qos"), any())) doReturn true
      whenever(configService.isEnabled(eq("qos.learningMode"), any())) doReturn false
    }
    afterGroup(::resetMocks)


    given("buffer state is INACTIVE") {
      val execution = pipeline {
        application = "spintest"
        status = NOT_STARTED
      }

      beforeGroup {
        whenever(bufferStateSupplier.enabled()) doReturn true
        whenever(bufferStateSupplier.get()) doReturn INACTIVE
      }

      afterGroup(::resetMocks)

      on("before initial persist event") {
        subject.beforeInitialPersist(BeforeInitialExecutionPersist("orca-core", execution))
      }

      it("does nothing") {
        verify(policy1, never()).apply(execution)
        verify(policy2, never()).apply(execution)
        assert(execution.status == NOT_STARTED)
      }
    }

    given("buffer state is ACTIVE and policies decide ENQUEUE") {
      val execution = pipeline {
        application = "spintest"
        status = NOT_STARTED
      }

      beforeGroup {
        whenever(bufferStateSupplier.enabled()) doReturn true
        whenever(bufferStateSupplier.get()) doReturn ACTIVE
        whenever(policy1.apply(any())) doReturn BufferResult(
          action = ENQUEUE,
          force = false,
          reason = "Cannot determine action"
        )
        whenever(policy2.apply(any())) doReturn BufferResult(
          action = ENQUEUE,
          force = false,
          reason = "Cannot determine action"
        )
      }

      on("before initial persist event") {
        subject.beforeInitialPersist(BeforeInitialExecutionPersist("orca-core", execution))
      }

      it("does nothing") {
        verify(policy1, times(1)).apply(execution)
        verify(policy2, times(1)).apply(execution)
        assert(execution.status == NOT_STARTED)
      }
    }

    given("buffer state is ACTIVE and policies decide BUFFER") {
      val execution = pipeline {
        application = "spintest"
        status = NOT_STARTED
      }

      beforeGroup {
        whenever(bufferStateSupplier.get()) doReturn ACTIVE
        whenever(policy1.apply(any())) doReturn BufferResult(
          action = BUFFER,
          force = false,
          reason = "Going to buffer"
        )
        whenever(policy2.apply(any())) doReturn BufferResult(
          action = ENQUEUE,
          force = false,
          reason = "Cannot determine action"
        )
      }

      on("before initial persist event") {
        subject.beforeInitialPersist(BeforeInitialExecutionPersist("orca-core", execution))
      }

      it("does nothing") {
        verify(policy1, times(1)).apply(execution)
        verify(policy2, times(1)).apply(execution)
        assert(execution.status == BUFFERED)
      }
    }

    given("buffer state is ACTIVE and policy forces ENQUEUE") {
      val execution = pipeline {
        application = "spintest"
        status = NOT_STARTED
      }

      beforeGroup {
        whenever(bufferStateSupplier.get()) doReturn ACTIVE
        whenever(policy1.apply(any())) doReturn BufferResult(
          action = ENQUEUE,
          force = true,
          reason = "Going to buffer"
        )
        whenever(policy2.apply(any())) doReturn BufferResult(
          action = BUFFER,
          force = false,
          reason = "Should buffer"
        )
      }

      on("before initial persist event") {
        subject.beforeInitialPersist(BeforeInitialExecutionPersist("orca-core", execution))
      }

      it("does nothing") {
        verify(policy1, times(1)).apply(execution)
        verify(policy2, times(1)).apply(execution)
        assert(execution.status == NOT_STARTED)
      }
    }

    given("in learning mode and buffer state is ACTIVE") {
      val execution = pipeline {
        application = "spintest"
        status = NOT_STARTED
      }

      beforeGroup {
        whenever(bufferStateSupplier.get()) doReturn ACTIVE
        whenever(policy1.apply(any())) doReturn BufferResult(
          action = BUFFER,
          force = false,
          reason = "Buffer"
        )
        whenever(policy2.apply(any())) doReturn BufferResult(
          action = BUFFER,
          force = false,
          reason = "Should"
        )
        whenever(configService.isEnabled(any(), any())) doReturn true
      }

      on("before initial persist event") {
        subject.beforeInitialPersist(BeforeInitialExecutionPersist("orca-core", execution))
      }

      it("does nothing") {
        verify(policy1, times(1)).apply(execution)
        verify(policy2, times(1)).apply(execution)
        assert(execution.status == NOT_STARTED)
      }
    }
  }
})
