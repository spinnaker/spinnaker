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
package com.netflix.spinnaker.orca.q.migration

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import org.junit.Assert
import org.junit.jupiter.api.Test

object MinimalClassTypeInfoSerializationMigratorTest {

  val objectMapper = OrcaObjectMapper.newInstance().apply {
    registerModule(KotlinModule())
    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
  }

  val subject = MinimalClassTypeInfoSerializationMigrator()

  @Test
  fun `should migrate class type info serialized messages`() {
    messages.forEach { message ->
      val rawMap = objectMapper.readValue<MutableMap<String, Any?>>(message.second)

      val migrated = subject.migrate(rawMap)

      Assert.assertEquals(message.first, migrated["kind"])
      Assert.assertEquals("attempts", firstAttribute(migrated))
    }
  }

  private fun firstAttribute(message: MutableMap<String, Any?>): String? {
    val attributes = message["attributes"] as List<*>?
    if (attributes != null) {
      return (attributes[0] as Map<*, *>)["kind"] as String?
    }
    return null
  }

  private val messages = listOf(
    Pair("runTask", "{\"@class\":\".RunTask\",\"executionType\":\"com.netflix.spinnaker.orca.pipeline.model.Pipeline\",\"executionId\":\"c134df22-xxxx-4ea3-b992-b15297ea1e4a\",\"application\":\"spindemo\",\"stageId\":\"333a943f-d564-4f28-b7f3-a9fe72fab6fd\",\"taskId\":\"5\",\"taskType\":\"com.netflix.spinnaker.orca.clouddriver.tasks.cluster.WaitForClusterDisableTask\",\"attributes\":[{\"@class\":\".AttemptsAttribute\",\"attempts\":5}]}"),
    Pair("completeStage", "{\"@class\":\".CompleteStage\",\"executionType\":\"com.netflix.spinnaker.orca.pipeline.model.Pipeline\",\"executionId\":\"c134df22-xxxx-4ea3-b992-b15297ea1e4a\",\"application\":\"spindemo\",\"stageId\":\"43bc2fb6-de2e-4f07-a9ce-6af8ef247298\",\"taskId\":\"6\",\"taskType\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask\",\"attributes\":[{\"@class\":\".AttemptsAttribute\",\"attempts\":2}]}"),
    Pair("startExecution", "{\"@class\":\".StartExecution\",\"executionType\":\"com.netflix.spinnaker.orca.pipeline.model.Pipeline\",\"executionId\":\"27d3f467-xxxx-4391-b43f-bd5d3b74d741\",\"application\":\"spindemo\",\"stageId\":\"48e1cb38-5867-4e39-b95d-2ae71b9924c4\",\"taskId\":\"5\",\"taskType\":\"com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask\",\"attributes\":[{\"@class\":\".AttemptsAttribute\",\"attempts\":5}]}"),

    Pair("runTask", "{\"kind\":\"runTask\",\"executionType\":\"com.netflix.spinnaker.orca.pipeline.model.Pipeline\",\"executionId\":\"c134df22-xxxx-4ea3-b992-b15297ea1e4a\",\"application\":\"spindemo\",\"stageId\":\"6baf7090-0f11-4e8c-ae12-c19881a48538\",\"taskId\":\"3\",\"taskType\":\"com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask\",\"attributes\":[{\"kind\":\"attempts\",\"attempts\":1}]}"),

    Pair("completeTask", "{\"@class\":\".CompleteTask\",\"executionType\":\"com.netflix.spinnaker.orca.pipeline.model.Orchestration\",\"executionId\":\"24076bcc-38b8-4b54-b381-7f6c7cab407f\",\"application\":\"spindemo\",\"stageId\":\"e9f442d7-22b0-4c96-adaa-74354a38e002\",\"taskId\":\"3\",\"status\":\"SUCCEEDED\",\"attributes\":[{\"@class\":\".AttemptsAttribute\",\"attempts\":0}]}"),
    Pair("completeStage", "{\"@class\":\".CompleteStage\",\"executionType\":\"com.netflix.spinnaker.orca.pipeline.model.Orchestration\",\"executionId\":\"24076bcc-38b8-4b54-b381-7f6c7cab407f\",\"application\":\"spindemo\",\"stageId\":\"e9f442d7-22b0-4c96-adaa-74354a38e002\",\"attributes\":[{\"@class\":\".AttemptsAttribute\",\"attempts\":1}]}"),
    Pair("completeExecution", "{\"@class\":\".CompleteExecution\",\"executionType\":\"com.netflix.spinnaker.orca.pipeline.model.Orchestration\",\"executionId\":\"24076bcc-38b8-4b54-b381-7f6c7cab407f\",\"application\":\"spindemo\",\"attributes\":[{\"@class\":\".AttemptsAttribute\",\"attempts\":0}]}"),
    Pair("startExecution", "{\"@class\":\".StartExecution\",\"executionType\":\"com.netflix.spinnaker.orca.pipeline.model.Orchestration\",\"executionId\":\"a4d585d9-6db5-4213-acdf-24caa5e19c4f\",\"application\":\"spindemo\",\"attributes\":[{\"@class\":\".AttemptsAttribute\",\"attempts\":0}]}"),
    Pair("startStage", "{\"@class\":\".StartStage\",\"executionType\":\"com.netflix.spinnaker.orca.pipeline.model.Orchestration\",\"executionId\":\"a4d585d9-6db5-4213-acdf-24caa5e19c4f\",\"application\":\"spindemo\",\"stageId\":\"601c5595-cf95-476c-b84e-d5cddd8a30de\",\"attributes\":[{\"@class\":\".AttemptsAttribute\",\"attempts\":0}]}"),
    Pair("startTask", "{\"@class\":\".StartTask\",\"executionType\":\"com.netflix.spinnaker.orca.pipeline.model.Orchestration\",\"executionId\":\"a4d585d9-6db5-4213-acdf-24caa5e19c4f\",\"application\":\"spindemo\",\"stageId\":\"601c5595-cf95-476c-b84e-d5cddd8a30de\",\"taskId\":\"1\",\"attributes\":[{\"@class\":\".AttemptsAttribute\",\"attempts\":0}]}"),
    Pair("completeTask", "{\"@class\":\".CompleteTask\",\"executionType\":\"com.netflix.spinnaker.orca.pipeline.model.Pipeline\",\"executionId\":\"9ffc71b9-0a17-4ff7-bd50-cdf248c25f48\",\"application\":\"spindemo\",\"stageId\":\"29a38509-c2bd-42c9-a0e0-bf9c0ec90e35\",\"taskId\":\"10\",\"status\":\"SUCCEEDED\",\"attributes\":[{\"@class\":\".AttemptsAttribute\",\"attempts\":1}]}"),
    Pair("startStage", "{\"@class\":\".StartStage\",\"executionType\":\"com.netflix.spinnaker.orca.pipeline.model.Pipeline\",\"executionId\":\"9ffc71b9-0a17-4ff7-bd50-cdf248c25f48\",\"application\":\"spindemo\",\"stageId\":\"7b37f800-c263-482f-ac3c-443c2eaf4597\",\"attributes\":[{\"@class\":\".AttemptsAttribute\",\"attempts\":0}]}"),
    Pair("completeTask", "{\"@class\":\".CompleteTask\",\"executionType\":\"com.netflix.spinnaker.orca.pipeline.model.Pipeline\",\"executionId\":\"9ffc71b9-0a17-4ff7-bd50-cdf248c25f48\",\"application\":\"spindemo\",\"stageId\":\"5271b8c0-f6c4-475e-b60d-a5c87308518d\",\"taskId\":\"3\",\"status\":\"SUCCEEDED\",\"attributes\":[{\"@class\":\".AttemptsAttribute\",\"attempts\":1}]}"),
    Pair("completeStage", "{\"@class\":\".CompleteStage\",\"executionType\":\"com.netflix.spinnaker.orca.pipeline.model.Pipeline\",\"executionId\":\"a1c645a6-4971-40a6-925f-ac97ccd31052\",\"application\":\"spindemo\",\"stageId\":\"c803d269-0e1d-40a9-9046-760cfa6161a3\",\"attributes\":[{\"@class\":\".AttemptsAttribute\",\"attempts\":0}]}"),
    Pair("startExecution", "{\"@class\":\".StartExecution\",\"executionType\":\"com.netflix.spinnaker.orca.pipeline.model.Orchestration\",\"executionId\":\"7b02e00d-a33d-4442-8749-2febf2b41e02\",\"application\":\"spindemo\",\"attributes\":[{\"@class\":\".AttemptsAttribute\",\"attempts\":1}]}")
  )
}
