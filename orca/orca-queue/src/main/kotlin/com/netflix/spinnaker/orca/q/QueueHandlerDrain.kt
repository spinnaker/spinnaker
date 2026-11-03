/*
 * Copyright 2026 Harness, Inc.
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

import com.netflix.spinnaker.config.QueueProperties
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.q.Queue
import jakarta.annotation.PreDestroy
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component

/**
 * Lets message handlers that are already running finish when the application shuts down.
 *
 * Polling stops as soon as the context starts closing, but the handler pool would otherwise be
 * interrupted straight away, leaving every in-flight message unacknowledged until it is redelivered
 * after its ack timeout. This bean depends on the queue and the execution repository that the
 * handlers use, so Spring destroys it first and they are still usable while it waits.
 */
@Component
class QueueHandlerDrain(
  @Qualifier("messageHandlerPool") private val pool: ThreadPoolTaskExecutor,
  // Only here so these beans outlive the drain, see above.
  @Suppress("unused") private val queue: Queue,
  @Suppress("unused") private val executionRepository: ExecutionRepository,
  private val properties: QueueProperties
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @PreDestroy
  fun drain() {
    val executor = pool.threadPoolExecutor
    executor.shutdown()
    if (executor.awaitTermination(properties.shutdownWaitSeconds, TimeUnit.SECONDS)) {
      log.info("Message handlers finished")
    } else {
      log.warn(
        "Interrupting {} message handler(s) still running after {}s; their messages will be redelivered",
        executor.activeCount,
        properties.shutdownWaitSeconds
      )
    }
  }
}
