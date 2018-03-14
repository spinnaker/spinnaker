/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.model.support

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.netflix.spinnaker.orca.pipeline.model.*

internal class TriggerDeserializer
  : StdDeserializer<Trigger>(Trigger::class.java) {
  override fun deserialize(parser: JsonParser, context: DeserializationContext): Trigger =
    parser.codec.readTree<JsonNode>(parser).run {
      return when {
        looksLikeDocker() -> DockerTrigger(
          get("type").textValue(),
          get("correlationId")?.textValue(),
          get("user")?.textValue() ?: "[anonymous]",
          get("parameters")?.mapValue(parser) ?: mutableMapOf(),
          get("artifacts")?.listValue(parser) ?: mutableListOf(),
          get("notifications")?.listValue(parser) ?: mutableListOf(),
          get("rebake")?.booleanValue() == true,
          get("dryRun")?.booleanValue() == true,
          get("strategy")?.booleanValue() == true,
          get("account").textValue(),
          get("repository").textValue(),
          get("tag").textValue()
        )
        looksLikeJenkins() -> JenkinsTrigger(
          get("type").textValue(),
          get("correlationId")?.textValue(),
          get("user")?.textValue() ?: "[anonymous]",
          get("parameters")?.mapValue(parser) ?: mutableMapOf(),
          get("artifacts")?.listValue(parser) ?: mutableListOf(),
          get("notifications")?.listValue(parser) ?: mutableListOf(),
          get("rebake")?.booleanValue() == true,
          get("dryRun")?.booleanValue() == true,
          get("strategy")?.booleanValue() == true,
          get("master").textValue(),
          get("job").textValue(),
          get("buildNumber").intValue(),
          get("propertyFile")?.textValue()
        ).apply {
          buildInfo = get("buildInfo")?.parseValue(parser)
          properties = get("properties")?.mapValue(parser) ?: mutableMapOf()
        }
        looksLikePipeline() -> PipelineTrigger(
          get("type").textValue(),
          get("correlationId")?.textValue(),
          get("user")?.textValue() ?: "[anonymous]",
          get("parameters")?.mapValue(parser) ?: mutableMapOf(),
          get("artifacts")?.listValue(parser) ?: mutableListOf(),
          get("notifications")?.listValue(parser) ?: mutableListOf(),
          get("rebake")?.booleanValue() == true,
          get("dryRun")?.booleanValue() == true,
          get("strategy")?.booleanValue() == true,
          get("parentExecution").parseValue<Execution>(parser),
          get("parentPipelineStageId")?.textValue()
        )
        looksLikeGit() -> GitTrigger(
          get("type").textValue(),
          get("correlationId")?.textValue(),
          get("user")?.textValue() ?: "[anonymous]",
          get("parameters")?.mapValue(parser) ?: mutableMapOf(),
          get("artifacts")?.listValue(parser) ?: mutableListOf(),
          get("notifications")?.listValue(parser) ?: mutableListOf(),
          get("rebake")?.booleanValue() == true,
          get("dryRun")?.booleanValue() == true,
          get("strategy")?.booleanValue() == true,
          get("hash").textValue(),
          get("source").textValue(),
          get("project").textValue(),
          get("branch").textValue(),
          get("slug").textValue()
        )
        else -> DefaultTrigger(
          get("type").textValue(),
          get("correlationId")?.textValue(),
          get("user")?.textValue() ?: "[anonymous]",
          get("parameters")?.mapValue(parser) ?: mutableMapOf(),
          get("artifacts")?.listValue(parser) ?: mutableListOf(),
          get("notifications")?.listValue(parser) ?: mutableListOf(),
          get("rebake")?.booleanValue() == true,
          get("dryRun")?.booleanValue() == true,
          get("strategy")?.booleanValue() == true
        )
      }.apply {
        other = mapValue(parser)
        resolvedExpectedArtifacts = get("resolvedExpectedArtifacts")?.listValue(parser) ?: mutableListOf()
      }
    }

  private fun JsonNode.looksLikeDocker() =
    hasNonNull("account") && hasNonNull("repository") && hasNonNull("tag")

  private fun JsonNode.looksLikeGit() =
    hasNonNull("source") && hasNonNull("project") && hasNonNull("branch") && hasNonNull("slug")

  private fun JsonNode.looksLikeJenkins() =
    hasNonNull("master") && hasNonNull("job") && hasNonNull("buildNumber")

  private fun JsonNode.looksLikePipeline() =
    hasNonNull("parentExecution")

  private fun <E> JsonNode.listValue(parser: JsonParser) =
    parser.codec.treeToValue(this, List::class.java) as List<E>

  private fun <K, V> JsonNode.mapValue(parser: JsonParser) =
    parser.codec.treeToValue(this, Map::class.java) as Map<K, V>

  private inline fun <reified T> JsonNode.parseValue(parser: JsonParser): T =
    parser.codec.treeToValue(this, T::class.java)
}
