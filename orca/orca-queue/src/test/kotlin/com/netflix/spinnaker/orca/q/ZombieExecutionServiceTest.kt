/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.api.test.pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.q.metrics.MonitorableQueue
import com.netflix.spinnaker.time.fixedClock
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode
import org.jetbrains.spek.subject.SubjectSpek
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers

object ZombieExecutionServiceTest : SubjectSpek<ZombieExecutionService>({

  val queue: MonitorableQueue = mock()
  val repository: ExecutionRepository = mock()
  val clock = fixedClock(instant = Instant.now().minus(Duration.ofHours(1)))

  subject(CachingMode.GROUP) {
    ZombieExecutionService(
      repository,
      queue,
      clock,
      Optional.of(Schedulers.trampoline())
    )
  }

  fun resetMocks() =
    reset(queue, repository)

  describe("detecting zombie executions") {
    val criteria = ExecutionRepository.ExecutionCriteria().setStatuses(ExecutionStatus.RUNNING)

    given("a non-running pipeline with no associated messages") {
      val pipeline = pipeline {
        status = ExecutionStatus.SUCCEEDED
        buildTime = clock.instant().minus(1, ChronoUnit.HOURS).toEpochMilli()
      }

      beforeGroup {
        whenever(repository.retrieve(ExecutionType.PIPELINE, criteria)) doReturn Observable.just(pipeline)
        whenever(repository.retrieve(ExecutionType.ORCHESTRATION, criteria)) doReturn Observable.empty()
        whenever(queue.containsMessage(any())) doReturn true
      }

      afterGroup(::resetMocks)

      on("looking for zombies") {
        val result = subject.findAllZombies(Duration.ofMinutes(10))

        it("does not increment the counter") {
          assert(result.size == 0)
        }
      }
    }

    given("a running pipeline with an associated messages") {
      val pipeline = pipeline {
        status = ExecutionStatus.RUNNING
        buildTime = clock.instant().minus(1, ChronoUnit.HOURS).toEpochMilli()
      }

      beforeGroup {
        whenever(repository.retrieve(pipeline.type, criteria)) doReturn Observable.just(pipeline)
        whenever(repository.retrieve(ExecutionType.ORCHESTRATION, criteria)) doReturn Observable.empty()
        whenever(queue.containsMessage(any())) doReturn true
      }

      afterGroup(::resetMocks)

      on("looking for zombies") {
        val result = subject.findAllZombies(Duration.ofMinutes(10))

        it("does not increment the counter") {
          assert(result.size == 0)
        }
      }
    }

    given("a running pipeline with no associated messages") {
      val pipeline = pipeline {
        status = ExecutionStatus.RUNNING
        buildTime = clock.instant().minus(1, ChronoUnit.HOURS).toEpochMilli()
      }

      beforeGroup {
        whenever(repository.retrieve(ExecutionType.PIPELINE, criteria)) doReturn Observable.just(pipeline)
        whenever(repository.retrieve(ExecutionType.ORCHESTRATION, criteria)) doReturn Observable.empty()
        whenever(queue.containsMessage(any())) doReturn false
      }

      afterGroup(::resetMocks)

      on("looking for zombies") {
        val result = subject.findAllZombies(Duration.ofMinutes(10))

        it("returns zombie") {
          assert(result.size == 1)
        }
      }
    }
  }
})
