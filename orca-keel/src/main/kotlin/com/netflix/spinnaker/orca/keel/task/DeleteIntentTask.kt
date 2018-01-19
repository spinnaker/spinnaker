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

package com.netflix.spinnaker.orca.keel.task

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.KeelService
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class DeleteIntentTask
@Autowired constructor(
  private val keelService: KeelService
) : RetryableTask {
  private val log = LoggerFactory.getLogger(javaClass)

  override fun execute(stage: Stage) : TaskResult {
    if (!stage.context.containsKey("intentId")) {
      throw IllegalArgumentException("Missing required task parameter (intentId)")
    }

    val intentId = stage.context["intentId"].toString()

    val status = stage.context["status"]?.toString()

    val response = keelService.deleteIntents(intentId, status)

    val outputs = mapOf("intent.id" to intentId)

    return TaskResult(
      if (response.status == HttpStatus.NO_CONTENT.value()) ExecutionStatus.SUCCEEDED else ExecutionStatus.TERMINAL,
      outputs
    )
  }

  override fun getBackoffPeriod() = TimeUnit.SECONDS.toMillis(15)

  override fun getTimeout() = TimeUnit.MINUTES.toMillis(1)
}
