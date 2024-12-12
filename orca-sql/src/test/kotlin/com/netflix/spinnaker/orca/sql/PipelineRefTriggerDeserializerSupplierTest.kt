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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.netflix.spinnaker.orca.sql.pipeline.persistence.PipelineRefTrigger
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals

class PipelineRefTriggerDeserializerSupplierTest : JUnit5Minutests {

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
          set<ObjectNode>("parentExecution", jsonNodeFactory.objectNode().put("id", "execution-id"))
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
      val jsonParser =  ObjectMapper().createParser("")

      test("all fields in pipelineRef are added") {
        val node = jsonNodeFactory.objectNode().apply {
          put("correlationId", "correlation-id")
          put("user", "test-user")
          set<ObjectNode>("parameters", jsonNodeFactory.objectNode().put("key1", "value1"))
          set<ObjectNode>("artifacts", jsonNodeFactory.arrayNode().add(jsonNodeFactory.objectNode().put("type", "artifact-type")))
          put("rebake", true)
          put("dryRun", false)
          put("strategy", true)
          put("parentExecutionId", "parent-execution-id")
          set<ObjectNode>("resolvedExpectedArtifacts", jsonNodeFactory.arrayNode().add(jsonNodeFactory.objectNode().put("id", "resolved-artifact-id")))
          set<ObjectNode>("other", jsonNodeFactory.objectNode().put("extra1", "value1"))
        }

        val trigger = deserializerSupplier.deserializer(node, jsonParser) as PipelineRefTrigger

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
          set<ObjectNode>("parameters", jsonNodeFactory.objectNode().put("key1", "value1"))
          set<ObjectNode>("artifacts", jsonNodeFactory.arrayNode().add(jsonNodeFactory.objectNode().put("type", "artifact-type")))
          put("rebake", true)
          put("dryRun", false)
          put("strategy", true)
          set<ObjectNode>("parentExecution", jsonNodeFactory.objectNode().put("id", "parent-execution-id-from-pipeline-trigger"))
          set<ObjectNode>("resolvedExpectedArtifacts", jsonNodeFactory.arrayNode().add(jsonNodeFactory.objectNode().put("id", "resolved-artifact-id")))
          set<ObjectNode>("other", jsonNodeFactory.objectNode().put("extra1", "value1"))
        }

        val trigger = deserializerSupplier.deserializer(node, jsonParser) as PipelineRefTrigger

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
