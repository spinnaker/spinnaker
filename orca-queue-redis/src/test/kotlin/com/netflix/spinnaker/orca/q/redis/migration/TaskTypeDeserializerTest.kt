/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.q.redis.migration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResolver
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.assertj.core.api.Assertions
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe

object TaskTypeDeserializerTest : Spek({
  val taskResolver = TaskResolver(listOf(DummyTask()), false)

  val objectMapper = ObjectMapper().apply {
    registerModule(KotlinModule())
    registerModule(
      SimpleModule()
        .addDeserializer(Class::class.java, TaskTypeDeserializer(taskResolver))
    )
  }

  describe("when 'taskType' is deserialized") {
    val canonicalJson = """{ "taskType" : "${DummyTask::class.java.canonicalName}" }"""
    Assertions.assertThat(
      objectMapper.readValue(canonicalJson, Target::class.java).taskType
    ).isEqualTo(DummyTask::class.java)

    val aliasedJson = """{ "taskType" : "anotherTaskAlias" }"""
    Assertions.assertThat(
      objectMapper.readValue(aliasedJson, Target::class.java).taskType
    ).isEqualTo(DummyTask::class.java)

    val notTaskTypeJson = """{ "notTaskType" : "java.lang.String" }"""
    Assertions.assertThat(
      objectMapper.readValue(notTaskTypeJson, Target::class.java).notTaskType
    ).isEqualTo(String::class.java)
  }
})

class Target(val taskType: Class<*>?, val notTaskType: Class<*>?)

@Task.Aliases("anotherTaskAlias")
class DummyTask : Task {
  override fun execute(stage: Stage): TaskResult {
    return TaskResult.SUCCEEDED
  }
}