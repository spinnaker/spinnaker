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
package com.netflix.spinnaker.keel.intent.aws.jackson

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.TextNode
import com.netflix.spinnaker.keel.intent.aws.loadbalancer.AvailabilityZoneConfig
import com.netflix.spinnaker.keel.intent.aws.loadbalancer.AvailabilityZoneConfig.Automatic
import com.netflix.spinnaker.keel.intent.aws.loadbalancer.AvailabilityZoneConfig.Manual

class AvailabilityZoneConfigSerializer : JsonSerializer<AvailabilityZoneConfig>() {
  override fun serialize(
    value: AvailabilityZoneConfig,
    gen: JsonGenerator,
    serializers: SerializerProvider
  ) {
    when (value) {
      is Automatic -> gen.writeString(Automatic.javaClass.simpleName.toLowerCase())
      is Manual -> value.availabilityZones.apply {
        gen.writeStartArray()
        forEach {
          gen.writeString(it)
        }
        gen.writeEndArray()
      }
    }
  }
}

class AvailabilityZoneConfigDeserializer : JsonDeserializer<AvailabilityZoneConfig>() {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): AvailabilityZoneConfig {
    val tree: TreeNode = p.readValueAsTree()
    return when (tree) {
      is TextNode -> Automatic // TODO: pretty crude assumption here
      is ArrayNode -> Manual(tree.map { it.textValue() }.toSet())
      else -> throw InvalidFormatException(
        p,
        "Expected text or array but found ${tree.javaClass.simpleName}",
        tree,
        AvailabilityZoneConfig::class.java
      )
    }
  }

}
