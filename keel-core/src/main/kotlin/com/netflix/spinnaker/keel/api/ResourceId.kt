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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import com.netflix.spinnaker.keel.serialization.ResourceIdDeserializer

@JsonSerialize(using = ToStringSerializer::class)
@JsonDeserialize(using = ResourceIdDeserializer::class)
data class ResourceId(val value: String) {
  override fun toString(): String = value

  // Resource names (the last token in a ResourceId) follow the Moniker format (<app>-<stack>-<detail>)
  val application: String
    get() = value.split(":").last().split("-").first()
}
