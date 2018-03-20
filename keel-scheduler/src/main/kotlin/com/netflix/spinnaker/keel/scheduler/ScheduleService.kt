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
package com.netflix.spinnaker.keel.scheduler

import com.netflix.spinnaker.config.ScheduleConvergeHandlerProperties
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentSpec
import com.netflix.spinnaker.q.Queue
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class ScheduleService(
  private val queue: Queue,
  private val scheduleConvergenceHandlerProperties: ScheduleConvergeHandlerProperties,
  private val clock: Clock
) {

  fun converge(intent: Intent<IntentSpec>) {
    queue.push(ConvergeIntent(
      intent,
      clock.instant().plusMillis(scheduleConvergenceHandlerProperties.stalenessTtl).toEpochMilli(),
      clock.instant().plusMillis(scheduleConvergenceHandlerProperties.timeoutTtl).toEpochMilli()
    ))
  }
}
