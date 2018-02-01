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

package com.netflix.spinnaker.orca.q.handler

import com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.events.TaskStarted
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.q.RunTask
import com.netflix.spinnaker.orca.q.StartTask
import com.netflix.spinnaker.q.Queue
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class StartTaskHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  override val contextParameterProcessor: ContextParameterProcessor,
  @Qualifier("queueEventPublisher") private val publisher: ApplicationEventPublisher,
  private val clock: Clock
) : OrcaMessageHandler<StartTask>, ExpressionAware {

  override fun handle(message: StartTask) {
    message.withTask { stage, task ->
      task.status = RUNNING
      task.startTime = clock.millis()
      val mergedContextStage = stage.withMergedContext()
      repository.storeStage(mergedContextStage)

      queue.push(RunTask(message, task.id, task.type))

      publisher.publishEvent(TaskStarted(this, mergedContextStage, task))
    }
  }

  override val messageType = StartTask::class.java

  @Suppress("UNCHECKED_CAST")
  private val com.netflix.spinnaker.orca.pipeline.model.Task.type
    get() = Class.forName(implementingClass) as Class<out Task>
}
