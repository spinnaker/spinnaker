/*
 * Copyright 2024 Harness Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.sql

import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.PipelineTrigger
import com.netflix.spinnaker.orca.pipeline.model.support.TriggerDeserializer
import com.netflix.spinnaker.orca.sql.pipeline.persistence.PipelineRefTrigger
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode

class PipelineRefTriggerDeserializerSupplierTest : JUnit5Minutests {

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `production mapper preserves flattened reference metadata across stores`(enabled: Boolean) {
    withProductionMapper(enabled) { mapper ->
      val metadata = mapOf("extra1" to "value1", "details" to mapOf("count" to 2))
      var trigger: Trigger = PipelineRefTrigger(
        user = "test-user",
        parameters = mutableMapOf("parameter" to "value"),
        parentExecutionId = "parent-execution-id",
        parentPipelineStageId = "parent-stage-id"
      ).apply { metadata.forEach { (key, value) -> setOther(key, value) } }

      repeat(2) {
        trigger = mapper.convertValue(trigger, Trigger::class.java)
        assertEquals(metadata, trigger.other)
        val stored = mapper.writeValueAsString(trigger)
        assertFalse(mapper.readTree(stored).has("other"))
        trigger = mapper.readValue(stored, Trigger::class.java)
        assertEquals(metadata, trigger.other)
      }

      assertEquals("parent-execution-id", (trigger as PipelineRefTrigger).parentExecutionId)
      assertEquals("parent-stage-id", (trigger as PipelineRefTrigger).parentPipelineStageId)
      assertEquals("test-user", trigger.user)
      assertEquals(mapOf("parameter" to "value"), trigger.parameters)
    }
  }

  @Test
  fun `production mapper preserves metadata when converting a pipeline to a reference`() {
    withProductionMapper(true) { mapper ->
      val parent = PipelineExecutionImpl(ExecutionType.PIPELINE, "test-application")
      val trigger = PipelineTrigger(parentExecution = parent).apply {
        setOther("extra1", "value1")
      }

      val reference = mapper.convertValue(trigger, Trigger::class.java) as PipelineRefTrigger
      assertEquals(parent.id, reference.parentExecutionId)
      assertEquals(mapOf("extra1" to "value1"), reference.other)
      val stored = mapper.writeValueAsString(reference)
      assertFalse(mapper.readTree(stored).has("parentExecution"))
      val loaded = mapper.readValue(stored, Trigger::class.java) as PipelineRefTrigger
      assertEquals(mapOf("extra1" to "value1"), loaded.toPipelineTrigger(parent).other)
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `production mapper merges legacy nested metadata with flattened fields`(enabled: Boolean) {
    withProductionMapper(enabled) { mapper ->
      val node = mapper.valueToTree<ObjectNode>(PipelineRefTrigger(parentExecutionId = "parent"))
      node.putObject("other").put("legacy", "value").put("shared", "nested")
      node.put("shared", "flattened")
      node.putNull("nullExtra")

      val trigger = mapper.treeToValue(node, Trigger::class.java) as PipelineRefTrigger
      assertEquals(mapOf("legacy" to "value", "shared" to "flattened", "nullExtra" to null), trigger.other)
      assertEquals("parent", trigger.parentExecutionId)
    }
  }

  private fun withProductionMapper(enabled: Boolean, action: (ObjectMapper) -> Unit) {
    val supplier = PipelineRefTriggerDeserializerSupplier(enabled)
    TriggerDeserializer.customTriggerSuppliers.add(supplier)
    try {
      action(OrcaObjectMapper.newInstance())
    } finally {
      TriggerDeserializer.customTriggerSuppliers.remove(supplier)
    }
  }

  fun tests() = rootContext {
    context("pipelineRef feature is disabled") {
      val deserializerSupplier = PipelineRefTriggerDeserializerSupplier(pipelineRefEnabled = false)
      val jsonNodeFactory = JsonNodeFactory.instance

      test("predicate is true when the trigger is a pipelineRef") {
        val node = jsonNodeFactory.objectNode().apply {
          put("type", "pipelineRef")
        }
        assertTrue(deserializerSupplier.predicate(node))
      }

      test("predicate is false when the trigger is not a pipelineRef") {
        val node = jsonNodeFactory.objectNode().apply {
          put("type", "manual")
        }
        assertFalse(deserializerSupplier.predicate(node))
      }
    }

    context("pipelineRef feature is enabled") {
      val deserializerSupplier = PipelineRefTriggerDeserializerSupplier(pipelineRefEnabled = true)
      val jsonNodeFactory = JsonNodeFactory.instance

      test("predicate is true when the trigger is a pipelineRef") {
        val node = jsonNodeFactory.objectNode().apply {
          put("type", "pipelineRef")
        }
        assertTrue(deserializerSupplier.predicate(node))
      }

      test("predicate is true when the trigger has parentExecution") {
        val node = jsonNodeFactory.objectNode().apply {
           set("parentExecution", jsonNodeFactory.objectNode().put("id", "execution-id"))
        }
        assertTrue(deserializerSupplier.predicate(node))
      }

      test("predicate is false when the trigger is not a pipelineRef") {
        val node = jsonNodeFactory.objectNode().apply {
          put("type", "manual")
        }
        assertFalse(deserializerSupplier.predicate(node))
      }

    }

    context("deserializing pipelineRef") {
      val deserializerSupplier = PipelineRefTriggerDeserializerSupplier(pipelineRefEnabled = true)
      val jsonNodeFactory = JsonNodeFactory.instance
      val objectMapper = JsonMapper.builder()
        .addModule(
          SimpleModule().addDeserializer(Trigger::class.java, TriggerDeserializer())
        )
        .build()

      test("all fields in pipelineRef are added") {
        val node = jsonNodeFactory.objectNode().apply {
          // Stored pipelineRef triggers carry their type; it is also what routes this node
          // to the custom supplier during full deserialization.
          put("type", "pipelineRef")
          put("correlationId", "correlation-id")
          put("user", "test-user")
           set("parameters", jsonNodeFactory.objectNode().put("key1", "value1"))
           set("artifacts", jsonNodeFactory.arrayNode().add(jsonNodeFactory.objectNode().put("type", "artifact-type")))
          put("rebake", true)
          put("dryRun", false)
          put("strategy", true)
          put("parentExecutionId", "parent-execution-id")
           set("resolvedExpectedArtifacts", jsonNodeFactory.arrayNode().add(jsonNodeFactory.objectNode().put("id", "resolved-artifact-id")))
           set("other", jsonNodeFactory.objectNode().put("extra1", "value1"))
         }

         TriggerDeserializer.customTriggerSuppliers.add(deserializerSupplier)
         val trigger = try {
           objectMapper.readValue(objectMapper.writeValueAsString(node), Trigger::class.java)
             as PipelineRefTrigger
         } finally {
           TriggerDeserializer.customTriggerSuppliers.remove(deserializerSupplier)
         }

        assertEquals("correlation-id", trigger.correlationId)
        assertEquals("test-user", trigger.user)
        assertEquals(mapOf("key1" to "value1"), trigger.parameters)
        assertEquals(1, trigger.artifacts.size)
        assertTrue(trigger.notifications.isEmpty())
        assertTrue(trigger.isRebake)
        assertFalse(trigger.isDryRun)
        assertTrue(trigger.isStrategy)
        assertEquals("parent-execution-id", trigger.parentExecutionId)
        assertEquals(1, trigger.resolvedExpectedArtifacts.size)
        assertEquals(1, trigger.other.size)
      }

      test("pipelineTrigger is deserialized into pipelineRef") {
        val node = jsonNodeFactory.objectNode().apply {
          put("correlationId", "correlation-id")
          put("user", "test-user")
           set("parameters", jsonNodeFactory.objectNode().put("key1", "value1"))
           set("artifacts", jsonNodeFactory.arrayNode().add(jsonNodeFactory.objectNode().put("type", "artifact-type")))
          put("rebake", true)
          put("dryRun", false)
          put("strategy", true)
           set("parentExecution", jsonNodeFactory.objectNode().put("id", "parent-execution-id-from-pipeline-trigger"))
           set("resolvedExpectedArtifacts", jsonNodeFactory.arrayNode().add(jsonNodeFactory.objectNode().put("id", "resolved-artifact-id")))
           set("other", jsonNodeFactory.objectNode().put("extra1", "value1"))
         }

         TriggerDeserializer.customTriggerSuppliers.add(deserializerSupplier)
         val trigger = try {
           objectMapper.readValue(objectMapper.writeValueAsString(node), Trigger::class.java)
             as PipelineRefTrigger
         } finally {
           TriggerDeserializer.customTriggerSuppliers.remove(deserializerSupplier)
         }

        assertEquals("correlation-id", trigger.correlationId)
        assertEquals("test-user", trigger.user)
        assertEquals(mapOf("key1" to "value1"), trigger.parameters)
        assertEquals(1, trigger.artifacts.size)
        assertTrue(trigger.notifications.isEmpty())
        assertTrue(trigger.isRebake)
        assertFalse(trigger.isDryRun)
        assertTrue(trigger.isStrategy)
        assertEquals("parent-execution-id-from-pipeline-trigger", trigger.parentExecutionId)
        assertEquals(1, trigger.resolvedExpectedArtifacts.size)
        assertEquals(1, trigger.other.size)
      }
    }
  }

}
