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

package com.netflix.spinnaker.q.metrics

import io.micrometer.core.instrument.MeterRegistry
import java.time.Clock
import java.util.concurrent.atomic.AtomicReference
import org.springframework.scheduling.annotation.Scheduled

/**
 * publishes gauges based on regular polling of the queue state
 */
class QueueMonitor(
  val registry: MeterRegistry,
  val clock: Clock,
  val queue: MonitorableQueue
) {
  init {
    registry.gauge("queue.depth", this) {
      it.lastState.depth.toDouble()
    }

    registry.gauge("queue.unacked.depth", this) {
      it.lastState.unacked.toDouble()
    }

    registry.gauge("queue.ready.depth", this) {
      it.lastState.ready.toDouble()
    }

    registry.gauge("queue.orphaned.messages", this) {
      it.lastState.orphaned.toDouble()
    }
  }

  val lastState: QueueState
    get() = _lastState.get()
  private val _lastState = AtomicReference<QueueState>(QueueState(0, 0, 0))

  @Scheduled(fixedDelayString = "\${queue.depth.metric.frequency:30000}")
  fun pollQueueState() {
    _lastState.set(queue.readState())
  }
}
