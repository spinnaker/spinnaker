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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

@JsonAdapter(ApiVersionAdapter::class)
@JsonSerialize(using = ToStringSerializer::class)
data class ApiVersion(
  val group: String,
  val version: String
) {
  @JsonCreator
  constructor(value: String) :
    this(value.substringBefore("/"), value.substringAfter("/"))

  override fun toString() = "$group/$version"
}

val SPINNAKER_API_V1 = ApiVersion("spinnaker.netflix.com", "v1")

internal class ApiVersionAdapter : TypeAdapter<ApiVersion>() {
  override fun write(writer: JsonWriter, value: ApiVersion) {
    writer.value(value.toString())
  }

  override fun read(reader: JsonReader): ApiVersion =
    reader.nextString().let(::ApiVersion)
}
