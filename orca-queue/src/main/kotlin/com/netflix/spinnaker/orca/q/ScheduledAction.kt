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

package com.netflix.spinnaker.orca.q

import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.Executors.newSingleThreadScheduledExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.SECONDS

/**
 * Encapsulates an action that runs regularly. Used by queue implementation for
 * checking for unacknowledged messages and re-delivering them.
 *
 * The function passed to [action] is called on a regular cycle by a single
 * dedicated thread.
 */
class ScheduledAction(
  private val action: () -> Unit,
  initialDelay: Long = 10,
  delay: Long = 10,
  unit: TimeUnit = SECONDS
) : Closeable {

  private val executor = newSingleThreadScheduledExecutor()
  private val watcher = executor
    .scheduleWithFixedDelay({
      try {
        action.invoke()
      } catch(e: Exception) {
        // this really indicates a code issue but if it's not caught here it
        // will kill the scheduled action.
        log.error("Uncaught exception in scheduled action", e)
      }
    }, initialDelay, delay, unit)

  private val log = LoggerFactory.getLogger(javaClass)

  override fun close() {
    watcher.cancel(false)
    executor.shutdown()
  }
}
