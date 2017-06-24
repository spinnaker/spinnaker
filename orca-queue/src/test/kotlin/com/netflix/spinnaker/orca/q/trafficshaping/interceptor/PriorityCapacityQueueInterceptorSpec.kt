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
package com.netflix.spinnaker.orca.q.trafficshaping.interceptor

import com.netflix.spectator.api.Id
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.config.TrafficShapingProperties
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.q.StartExecution
import com.netflix.spinnaker.orca.q.trafficshaping.capacity.GlobalCapacity
import com.netflix.spinnaker.orca.q.trafficshaping.capacity.PrioritizationStrategy
import com.netflix.spinnaker.orca.q.trafficshaping.capacity.Priority
import com.netflix.spinnaker.orca.q.trafficshaping.capacity.PriorityCapacityRepository
import com.nhaarman.mockito_kotlin.*
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertNull
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe

object PriorityCapacityQueueInterceptorSpec : Spek({

  val repository: PriorityCapacityRepository = mock()
  val strategy: PrioritizationStrategy = mock()
  val registry = NoopRegistry()
  val properties = TrafficShapingProperties.PriorityCapacityProperties()
  val timeShapedId: Id = mock()

  describe("a priority capacity queue interceptor") {
    val subject = PriorityCapacityQueueInterceptor(repository, strategy, registry, properties, timeShapedId)

    afterGroup {
      reset(strategy)
    }

    describe("when learning") {
      properties.learning = true

      whenever(strategy.getPriority(execution = any())) doReturn Priority.MEDIUM
      whenever(strategy.getPriority(message = any())) doReturn Priority.MEDIUM

      afterGroup {
        reset(repository)
      }

      val message = StartExecution(Pipeline::class.java, "1", "foo")

      describe("when under capacity") {
        whenever(repository.getGlobalCapacity()) doReturn GlobalCapacity(ceiling = 10, criticalUsage = 0, highUsage = 0, mediumUsage = 0, lowUsage = 0)
        assertNull(subject.interceptMessage(message))
      }

      describe("when over capacity") {
        whenever(repository.getGlobalCapacity()) doReturn GlobalCapacity(ceiling = 10, criticalUsage = 10, highUsage = 10, mediumUsage = 10, lowUsage = 10)
        assertNull(subject.interceptMessage(message))
      }
    }

    describe("when enforcing") {
      properties.learning = false

      afterGroup {
        reset(repository)
      }

      describe("when under capacity") {
        val message = StartExecution(Pipeline::class.java, "1", "foo")

        whenever(repository.getGlobalCapacity()) doReturn GlobalCapacity(ceiling = 10, criticalUsage = 0, highUsage = 0, mediumUsage = 0, lowUsage = 0)
        assertNull(subject.interceptMessage(message))
      }

      describe("when over capacity") {
        whenever(repository.getGlobalCapacity()) doReturn GlobalCapacity(ceiling = 10, criticalUsage = 10, highUsage = 10, mediumUsage = 10, lowUsage = 10)

        afterGroup {
          reset(strategy)
        }

        describe("when intercepting critical message") {
          whenever(strategy.getPriority(message = any())) doReturn Priority.CRITICAL

          val message = StartExecution(Pipeline::class.java, "1", "foo")

          assertNull(subject.interceptMessage(message))
        }

        describe("when intercepting low message") {
          whenever(strategy.getPriority(message = any())) doReturn Priority.LOW

          val message = StartExecution(Pipeline::class.java, "1", "foo")

          assertNotNull(subject.interceptMessage(message))
        }
      }
    }
  }
})
