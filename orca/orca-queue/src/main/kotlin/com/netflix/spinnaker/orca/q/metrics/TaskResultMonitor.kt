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

import com.netflix.spinnaker.orca.events.TaskComplete
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

@Component
class TaskResultMonitor(private val registry: MeterRegistry) :
  ApplicationListener<TaskComplete> {

  override fun onApplicationEvent(event: TaskComplete) {
    registry
      .counter("orca.task.result", "status", event.task.status.name, "task", event.task.implementingClass)
      .increment()
  }
}
