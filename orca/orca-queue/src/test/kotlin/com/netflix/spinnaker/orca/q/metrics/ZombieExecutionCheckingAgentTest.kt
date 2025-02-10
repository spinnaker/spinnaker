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

package com.netflix.spinnaker.orca.q.metrics

import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Tag
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.ORCHESTRATION
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.api.test.pipeline
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria
import com.netflix.spinnaker.orca.q.ZombieExecutionService
import com.netflix.spinnaker.q.Activator
import com.netflix.spinnaker.q.metrics.MonitorableQueue
import com.netflix.spinnaker.time.fixedClock
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.rxjava3.core.Observable
import java.time.Duration
import java.time.Instant.now
import java.time.temporal.ChronoUnit.HOURS
import java.util.Optional
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek
import io.reactivex.rxjava3.core.Observable.just
import io.reactivex.rxjava3.schedulers.Schedulers

object ZombieExecutionCheckingAgentTest : SubjectSpek<ZombieExecutionCheckingAgent>({

  val queue: MonitorableQueue = mock()
  val repository: ExecutionRepository = mock()
  val clock = fixedClock(instant = now().minus(Duration.ofHours(1)))
  val activator: Activator = mock()
  val conch: NotificationClusterLock = mock()

  val zombieCounter: Counter = mock()

  val registry: Registry = mock {
    on { counter(eq("queue.zombies"), any<Iterable<Tag>>()) } doReturn zombieCounter
  }

  subject(GROUP) {
    ZombieExecutionCheckingAgent(
      ZombieExecutionService(
        repository,
        queue,
        clock,
        Optional.of(Schedulers.trampoline())
      ),
      registry,
      clock,
      conch,
      10,
      true,
      10,
      queueEnabled = true
    )
  }

  fun resetMocks() =
    reset(queue, repository, zombieCounter)

  describe("detecting zombie executions") {
    val criteria = ExecutionCriteria().setStatuses(RUNNING)

    given("the instance is disabled") {
      beforeGroup {
        whenever(activator.enabled) doReturn false
        whenever(conch.tryAcquireLock(eq("zombie"), any())) doReturn true
      }

      afterGroup {
        reset(activator, conch)
      }

      given("a running pipeline with no associated messages") {
        val pipeline = pipeline {
          status = RUNNING
          buildTime = clock.instant().minus(1, HOURS).toEpochMilli()
        }

        beforeGroup {
          whenever(repository.retrieve(PIPELINE, criteria)) doReturn just(pipeline)
          whenever(repository.retrieve(ORCHESTRATION, criteria)) doReturn Observable.empty()
          whenever(queue.containsMessage(any())) doReturn true
        }

        afterGroup(::resetMocks)

        on("looking for zombies") {
          subject.checkForZombies()

          it("does not increment zombie counter") {
            verifyNoMoreInteractions(zombieCounter)
          }
        }
      }
    }

    given("the instance is active and can acquire a cluster lock") {
      beforeGroup {
        whenever(activator.enabled) doReturn true
        whenever(conch.tryAcquireLock(eq("zombie"), any())) doReturn true
      }

      afterGroup {
        reset(activator, conch)
      }

      given("a non-running pipeline with no associated messages") {
        val pipeline = pipeline {
          status = SUCCEEDED
          buildTime = clock.instant().minus(1, HOURS).toEpochMilli()
        }

        beforeGroup {
          whenever(repository.retrieve(PIPELINE, criteria)) doReturn just(pipeline)
          whenever(repository.retrieve(ORCHESTRATION, criteria)) doReturn Observable.empty()
          whenever(queue.containsMessage(any())) doReturn true
        }

        afterGroup(::resetMocks)

        on("looking for zombies") {
          subject.checkForZombies()

          it("does not increment the counter") {
            verifyNoMoreInteractions(zombieCounter)
          }
        }
      }

      given("a running pipeline with an associated messages") {
        val pipeline = pipeline {
          status = RUNNING
          buildTime = clock.instant().minus(1, HOURS).toEpochMilli()
        }

        beforeGroup {
          whenever(repository.retrieve(pipeline.type, criteria)) doReturn just(pipeline)
          whenever(repository.retrieve(ORCHESTRATION, criteria)) doReturn Observable.empty()
          whenever(queue.containsMessage(any())) doReturn true
        }

        afterGroup(::resetMocks)

        on("looking for zombies") {
          subject.checkForZombies()

          it("does not increment the counter") {
            verifyNoMoreInteractions(zombieCounter)
          }
        }
      }

      given("a running pipeline with no associated messages") {
        val pipeline = pipeline {
          status = RUNNING
          buildTime = clock.instant().minus(1, HOURS).toEpochMilli()
        }

        beforeGroup {
          whenever(repository.retrieve(PIPELINE, criteria)) doReturn just(pipeline)
          whenever(repository.retrieve(ORCHESTRATION, criteria)) doReturn Observable.empty()
          whenever(queue.containsMessage(any())) doReturn false
        }

        afterGroup(::resetMocks)

        on("looking for zombies") {
          subject.checkForZombies()

          it("increments the counter") {
            verify(zombieCounter).increment()
          }
        }
      }
    }
  }
})

private fun Sequence<Duration>.average() =
  map { it.toMillis() }
    .average()
    .let { Duration.ofMillis(it.toLong()) }
