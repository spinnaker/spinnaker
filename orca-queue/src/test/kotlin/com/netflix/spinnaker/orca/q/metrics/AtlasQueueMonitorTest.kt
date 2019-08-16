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
import com.netflix.spectator.api.Timer
import com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria
import com.netflix.spinnaker.orca.q.StartExecution
import com.netflix.spinnaker.q.Activator
import com.netflix.spinnaker.q.metrics.LockFailed
import com.netflix.spinnaker.q.metrics.MessageAcknowledged
import com.netflix.spinnaker.q.metrics.MessageDead
import com.netflix.spinnaker.q.metrics.MessageDuplicate
import com.netflix.spinnaker.q.metrics.MessageProcessing
import com.netflix.spinnaker.q.metrics.MessagePushed
import com.netflix.spinnaker.q.metrics.MessageRetried
import com.netflix.spinnaker.q.metrics.MonitorableQueue
import com.netflix.spinnaker.q.metrics.QueuePolled
import com.netflix.spinnaker.q.metrics.QueueState
import com.netflix.spinnaker.q.metrics.RetryPolled
import com.netflix.spinnaker.time.fixedClock
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.anyVararg
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockito_kotlin.whenever
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek
import rx.Observable.just
import rx.schedulers.Schedulers
import java.time.Duration
import java.time.Instant.now
import java.time.temporal.ChronoUnit.HOURS
import java.util.Optional
import java.util.concurrent.TimeUnit.MILLISECONDS

object AtlasQueueMonitorTest : SubjectSpek<AtlasQueueMonitor>({

  val queue: MonitorableQueue = mock()
  val repository: ExecutionRepository = mock()
  val clock = fixedClock(instant = now().minus(Duration.ofHours(1)))
  val activator: Activator = mock()
  val conch: NotificationClusterLock = mock()

  val pushCounter: Counter = mock()
  val ackCounter: Counter = mock()
  val retryCounter: Counter = mock()
  val deadCounter: Counter = mock()
  val duplicateCounter: Counter = mock()
  val lockFailedCounter: Counter = mock()
  val messageLagTimer: Timer = mock()
  val zombieCounter: Counter = mock()

  val registry: Registry = mock {
    on { counter(eq("queue.pushed.messages"), anyVararg<String>()) } doReturn pushCounter
    on { counter("queue.acknowledged.messages") } doReturn ackCounter
    on { counter("queue.retried.messages") } doReturn retryCounter
    on { counter("queue.dead.messages") } doReturn deadCounter
    on { counter(eq("queue.duplicate.messages"), anyVararg<String>()) } doReturn duplicateCounter
    on { counter("queue.lock.failed") } doReturn lockFailedCounter
    on { timer("queue.message.lag") } doReturn messageLagTimer
    on { counter(eq("queue.zombies"), any<Iterable<Tag>>()) } doReturn zombieCounter
  }

  subject(GROUP) {
    AtlasQueueMonitor(
      queue,
      registry,
      repository,
      clock,
      conch,
      true,
      Optional.of(Schedulers.immediate()),
      10,
      queueEnabled = true
    )
  }

  fun resetMocks() =
    reset(queue, repository, pushCounter, ackCounter, retryCounter, deadCounter, duplicateCounter, zombieCounter)

  describe("default values") {
    it("reports system uptime if the queue has never been polled") {
      assertThat(subject.lastQueuePoll).isEqualTo(clock.instant())
      assertThat(subject.lastRetryPoll).isEqualTo(clock.instant())
    }
  }

  describe("responding to queue events") {
    describe("when the queue is polled") {
      afterGroup(::resetMocks)

      val event = QueuePolled

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.onQueueEvent(event)
      }

      it("updates the last poll time") {
        assertThat(subject.lastQueuePoll).isEqualTo(clock.instant())
      }
    }

    describe("when the retry queue is polled") {
      afterGroup(::resetMocks)

      val event = RetryPolled

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.onQueueEvent(event)
      }

      it("updates the last poll time") {
        assertThat(subject.lastRetryPoll).isEqualTo(clock.instant())
      }
    }

    describe("when a message is processed") {
      afterGroup(::resetMocks)

      val event = MessageProcessing(StartExecution(PIPELINE, "1", "covfefe"), Duration.ofSeconds(5))

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.onQueueEvent(event)
      }

      it("records the lag") {
        verify(messageLagTimer).record(event.lag.toMillis(), MILLISECONDS)
      }
    }

    describe("when a message is pushed") {
      afterGroup(::resetMocks)

      val event = MessagePushed(StartExecution(PIPELINE, "1", "covfefe"))

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.onQueueEvent(event)
      }

      it("increments a counter") {
        verify(pushCounter).increment()
      }
    }

    describe("when a message is acknowledged") {
      afterGroup(::resetMocks)

      val event = MessageAcknowledged

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.onQueueEvent(event)
      }

      it("increments a counter") {
        verify(ackCounter).increment()
      }
    }

    describe("when a message is retried") {
      afterGroup(::resetMocks)

      val event = MessageRetried

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.onQueueEvent(event)
      }

      it("increments a counter") {
        verify(retryCounter).increment()
      }
    }

    describe("when a message is dead") {
      afterGroup(::resetMocks)

      val event = MessageDead

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.onQueueEvent(event)
      }

      it("increments a counter") {
        verify(deadCounter).increment()
      }
    }

    describe("when a duplicate message is pushed") {
      afterGroup(::resetMocks)

      val event = MessageDuplicate(StartExecution(PIPELINE, "1", "covfefe"))

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.onQueueEvent(event)
      }

      it("increments a counter") {
        verify(duplicateCounter).increment()
      }
    }

    describe("when an instance fails to lock a message") {
      afterGroup(::resetMocks)

      val event = LockFailed

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.onQueueEvent(event)
      }

      it("increments a counter") {
        verify(lockFailedCounter).increment()
      }
    }
  }

  describe("checking queue state") {
    afterGroup(::resetMocks)

    val queueState = QueueState(4, 1, 2, 0)

    beforeGroup {
      whenever(queue.readState()) doReturn queueState
    }

    on("checking queue state") {
      subject.pollQueueState()
    }

    it("updates the queue state") {
      assertThat(subject.lastState).isEqualTo(queueState)
    }
  }

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
          whenever(queue.containsMessage(any())) doReturn true
        }

        afterGroup(::resetMocks)

        on("looking for zombies") {
          subject.checkForZombies()

          it("does not increment zombie counter") {
            verifyZeroInteractions(zombieCounter)
          }
        }
      }
    }

    given("the instance cannot acquire a cluster lock") {
      beforeGroup {
        whenever(activator.enabled) doReturn true
        whenever(conch.tryAcquireLock(eq("zombie"), any())) doReturn false
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
          whenever(queue.containsMessage(any())) doReturn true
        }

        afterGroup(::resetMocks)

        on("looking for zombies") {
          subject.checkForZombies()

          it("does not run a zombie check") {
            verifyZeroInteractions(repository, queue, zombieCounter)
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
          whenever(queue.containsMessage(any())) doReturn true
        }

        afterGroup(::resetMocks)

        on("looking for zombies") {
          subject.checkForZombies()

          it("does not increment the counter") {
            verifyZeroInteractions(zombieCounter)
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
          whenever(queue.containsMessage(any())) doReturn true
        }

        afterGroup(::resetMocks)

        on("looking for zombies") {
          subject.checkForZombies()

          it("does not increment the counter") {
            verifyZeroInteractions(zombieCounter)
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
