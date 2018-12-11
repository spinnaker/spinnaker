/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.api

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.JsonAdapter
import java.lang.reflect.Type
import java.util.*

@JsonAdapter(AssetMetadataAdapter::class)
data class AssetMetadata(
  val name: AssetName,
  @JsonProperty(defaultValue = "0") val resourceVersion: Long? = null,
  val uid: UUID? = null,
  @get:JsonAnyGetter val data: Map<String, Any?> = emptyMap()
) {
  // Workaround for the inline class AssetName. Jackson can't deserialize it
  // since it's an erased type.
  @JsonCreator
  constructor(data: Map<String, Any?>) : this(
    AssetName(data.getValue("name").toString()),
    data["resourceVersion"]?.toString()?.toLong(),
    data["uid"]?.toString()?.let(UUID::fromString),
    data - "name" - "resourceVersion" - "uid"
  )

  override fun toString(): String =
    (mapOf(
      "name" to name,
      "resourceVersion" to resourceVersion,
      "uid" to uid
    ) + data)
      .toString()
}

internal class AssetMetadataAdapter : JsonDeserializer<AssetMetadata>, JsonSerializer<AssetMetadata> {
  override fun serialize(src: AssetMetadata, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
    return context.serialize(mapOf("name" to src.name.value, "uid" to src.uid, "resourceVersion" to src.resourceVersion) + src.data, Map::class.java)
  }

  override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): AssetMetadata {
    return AssetMetadata(context.deserialize<Map<String, Any?>>(json, Map::class.java))
  }
}
