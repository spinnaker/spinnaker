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

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.KeelService
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.keel.model.Intent
import com.netflix.spinnaker.orca.keel.model.UpsertIntentDryRunResponse
import com.netflix.spinnaker.orca.keel.model.UpsertIntentRequest
import com.netflix.spinnaker.orca.keel.model.UpsertIntentResponse
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class UpsertIntentTask
@Autowired constructor(
  private val keelService: KeelService,
  private val keelObjectMapper: ObjectMapper
) : RetryableTask {
  private val log = LoggerFactory.getLogger(javaClass)

  override fun execute(stage: Stage) : TaskResult {
    val missingParams = mutableListOf<String>()

    if (!stage.context.containsKey("intents")) {
      missingParams.add("intents")
    }

    if (!stage.context.containsKey("dryRun") ) {
      missingParams.add("dryRun")
    }

    if (missingParams.isNotEmpty()) {
      throw IllegalArgumentException("Missing required task parameters (${missingParams.joinToString(",")})")
    }

    val upsertIntentRequest = UpsertIntentRequest(
      intents = keelObjectMapper.convertValue(stage.context["intents"], object : TypeReference<List<Intent>>() {}),
      dryRun = stage.context["dryRun"].toString().toBoolean()
    )

    val response = keelService.upsertIntents(upsertIntentRequest)

    val outputs = mutableMapOf<String, Any>()

    try {
      if (upsertIntentRequest.dryRun) {
        val dryRunResponse = keelObjectMapper.readValue<List<UpsertIntentDryRunResponse>>(response.body.`in`(), object : TypeReference<List<UpsertIntentDryRunResponse>>(){})
        outputs.put("upsertIntentResponse", dryRunResponse)
      } else {
        val upsertResponse = keelObjectMapper.readValue<List<UpsertIntentResponse>>(response.body.`in`(), object : TypeReference<List<UpsertIntentResponse>>(){})
        outputs.put("upsertIntentResponse", upsertResponse)
      }
    } catch (e: Exception) {
      log.error("Error processing upsert intent response from keel", e)
      throw e
    }

    val executionStatus = if (response.status == HttpStatus.ACCEPTED.value()) ExecutionStatus.SUCCEEDED else ExecutionStatus.TERMINAL
    return TaskResult.builder(executionStatus).context(outputs).build()
  }

  override fun getBackoffPeriod() =  TimeUnit.SECONDS.toMillis(15)

  override fun getTimeout() = TimeUnit.MINUTES.toMillis(1)

}
