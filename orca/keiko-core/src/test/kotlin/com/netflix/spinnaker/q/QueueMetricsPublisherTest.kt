/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.q

import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.q.metrics.LockFailed
import com.netflix.spinnaker.q.metrics.MessageAcknowledged
import com.netflix.spinnaker.q.metrics.MessageDead
import com.netflix.spinnaker.q.metrics.MessageDuplicate
import com.netflix.spinnaker.q.metrics.MessageProcessing
import com.netflix.spinnaker.q.metrics.MessagePushed
import com.netflix.spinnaker.q.metrics.MessageRetried
import com.netflix.spinnaker.q.metrics.QueueMetricsPublisher
import com.netflix.spinnaker.q.metrics.QueuePolled
import com.netflix.spinnaker.q.metrics.RetryPolled
import com.netflix.spinnaker.time.fixedClock
import java.time.Duration
import java.time.Instant.now
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek

object QueueMetricsPublisherTest : SubjectSpek<QueueMetricsPublisher>({
  val clock = fixedClock(instant = now().minus(Duration.ofHours(1)))
  val registry: Registry = DefaultRegistry()

  subject(GROUP) {
    QueueMetricsPublisher(
      registry,
      clock
    )
  }

  describe("default values") {
    it("reports system uptime if the queue has never been polled") {
      assertThat(subject.lastQueuePoll).isEqualTo(clock.instant())
      assertThat(subject.lastRetryPoll).isEqualTo(clock.instant())
    }
  }

  describe("responding to queue events") {
    describe("when the queue is polled") {
      val event = QueuePolled

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.publishEvent(event)
      }

      it("updates the last poll time") {
        assertThat(subject.lastQueuePoll).isEqualTo(clock.instant())
      }
    }

    describe("when the retry queue is polled") {
      val event = RetryPolled

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.publishEvent(event)
      }

      it("updates the last poll time") {
        assertThat(subject.lastRetryPoll).isEqualTo(clock.instant())
      }
    }

    describe("when a message is processed") {
      val event = MessageProcessing(SimpleMessage("message"), Duration.ofMillis(42))

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.publishEvent(event)
      }

      it("records the lag") {
        assertThat(registry.timer("queue.message.lag").totalTime())
          .isEqualTo(event.lag.toNanos())
      }
    }

    describe("when a message is pushed") {
      val event = MessagePushed(SimpleMessage("message"))

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.publishEvent(event)
      }

      it("increments a counter") {
        assertThat(registry.counter("queue.pushed.messages").count()).isEqualTo(1)
      }
    }

    describe("when a message is acknowledged") {
      val event = MessageAcknowledged

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.publishEvent(event)
      }

      it("increments a counter") {
        assertThat(registry.counter("queue.acknowledged.messages").count()).isEqualTo(1)
      }
    }

    describe("when a message is retried") {
      val event = MessageRetried

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.publishEvent(event)
      }

      it("increments a counter") {
        assertThat(registry.counter("queue.retried.messages").count()).isEqualTo(1)
      }
    }

    describe("when a message is dead") {
      val event = MessageDead

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.publishEvent(event)
      }

      it("increments a counter") {
        assertThat(registry.counter("queue.dead.messages").count()).isEqualTo(1)
      }
    }

    describe("when a duplicate message is pushed") {
      val event = MessageDuplicate(SimpleMessage("message"))

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.publishEvent(event)
      }

      it("increments a counter") {
        assertThat(
          registry.counter(
            "queue.duplicate.messages",
            "messageType", event.payload.javaClass.simpleName
          ).count()
        )
          .isEqualTo(1)
      }
    }

    describe("when an instance fails to lock a message") {
      val event = LockFailed

      on("receiving a ${event.javaClass.simpleName} event") {
        subject.publishEvent(event)
      }

      it("increments a counter") {
        assertThat(registry.counter("queue.lock.failed").count()).isEqualTo(1)
      }
    }
  }
})
