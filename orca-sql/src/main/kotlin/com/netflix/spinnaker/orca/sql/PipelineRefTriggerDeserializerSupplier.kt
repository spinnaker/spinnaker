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

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger
import com.netflix.spinnaker.orca.pipeline.model.support.CustomTriggerDeserializerSupplier
import com.netflix.spinnaker.orca.pipeline.model.support.mapValue
import com.netflix.spinnaker.orca.pipeline.model.support.listValue
import com.netflix.spinnaker.orca.sql.pipeline.persistence.PipelineRefTrigger

class PipelineRefTriggerDeserializerSupplier(
  private val pipelineRefEnabled: Boolean
) : CustomTriggerDeserializerSupplier {

  override val type: String = "pipelineRef"

  override val predicate: (node: JsonNode) -> Boolean
    get() = { node ->
      // We need to deserialize always PipelineRef because
      // 1. There is a single object mapper use for serialize/deserialize in
      //    multiple places (OperationsController, ExecutionLauncher, SqlExecutionRepository)
      // 2. Insert executions with trigger works properly but update an existing execution fails because
      //    SqlExecutionRepository gets execution from database (deserialize) and we convert PipelineRef in its
      //    In-memory representation. This makes the SqlExecutionRepository to try to store again all execution
      //    context. We need to deserialize again to transform into PipelineRef properly.
      if (pipelineRefEnabled) {
        node.looksLikePipeline() || node.isPipelineRefTrigger() //if pipelineRef enabled we deserialize PipelineTrigger as PipelineRefTrigger
      } else {
        node.isPipelineRefTrigger() //if pipelineRef disabled we still are able to deserialize PipelineRefTrigger
      }
    }

  override val deserializer: (node: JsonNode, parser: JsonParser) -> Trigger
    get() = { node, parser ->
          with(node) {
            val parentExecutionId =  if (node.looksLikePipeline()) get("parentExecution").get("id").textValue() else get("parentExecutionId").textValue()
            PipelineRefTrigger(
              correlationId = get("correlationId")?.textValue(),
              user = get("user")?.textValue(),
              parameters = get("parameters")?.mapValue(parser) ?: mutableMapOf(),
              artifacts = get("artifacts")?.listValue(parser) ?: mutableListOf(),
              notifications = get("notifications")?.listValue(parser) ?: mutableListOf(),
              isRebake = get("rebake")?.booleanValue() == true,
              isDryRun = get("dryRun")?.booleanValue() == true,
              isStrategy = get("strategy")?.booleanValue() == true,
              parentExecutionId = parentExecutionId,
              parentPipelineStageId = get("parentPipelineStageId")?.textValue()
            )
          }
    }

  private fun JsonNode.isPipelineRefTrigger() =
    get("type")?.textValue() == type

  private fun JsonNode.looksLikePipeline() =
    hasNonNull("parentExecution")
}
