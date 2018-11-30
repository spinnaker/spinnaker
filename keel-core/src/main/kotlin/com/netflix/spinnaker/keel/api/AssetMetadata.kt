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
import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken.BOOLEAN
import com.google.gson.stream.JsonToken.END_OBJECT
import com.google.gson.stream.JsonToken.NULL
import com.google.gson.stream.JsonToken.NUMBER
import com.google.gson.stream.JsonToken.STRING
import com.google.gson.stream.JsonWriter
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
    data - "name"
  )

  override fun toString(): String =
    (mapOf(
      "name" to name,
      "resourceVersion" to resourceVersion,
      "uid" to uid
    ) + data)
      .toString()
}

internal class AssetMetadataAdapter : TypeAdapter<AssetMetadata>() {
  override fun write(writer: JsonWriter, value: AssetMetadata) {
    writer.apply {
      beginObject()
      name("name").value(value.name.value)
      if (value.resourceVersion != null) name("resourceVersion").value(value.resourceVersion)
      if (value.uid != null) name("uid").value(value.uid.toString())
      value.data.forEach { k, v ->
        name(k)
        when (v) {
          null -> nullValue()
          is String -> value(v)
          is Number -> value(v)
          is Boolean -> value(v)
          else -> value(v.toString())
        }
      }
      endObject()
    }
  }

  override fun read(reader: JsonReader): AssetMetadata {
    reader.beginObject()
    val data = mutableMapOf<String, Any?>()
    while (reader.peek() != END_OBJECT) {
      data[reader.nextName()] = when (val token = reader.peek()) {
        STRING -> reader.nextString()
        BOOLEAN -> reader.nextBoolean()
        NUMBER -> reader.nextLong()
        NULL -> reader.nextNull()
        else -> throw IllegalStateException("Expected a $STRING, $BOOLEAN, $NUMBER, or $NULL but found $token")
      }
    }
    reader.endObject()
    return AssetMetadata(data)
  }
}
