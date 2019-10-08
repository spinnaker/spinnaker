/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.keel.model

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import com.netflix.spinnaker.keel.api.RegionSpec

@JsonSerialize(using = ToStringSerializer::class)
@JsonDeserialize(using = SimpleRegionSpecDeserializer::class)
data class SimpleRegionSpec(
  override val region: String
) : RegionSpec {
  override fun toString(): String {
    return region
  }
}

class SimpleRegionSpecDeserializer : StdDeserializer<SimpleRegionSpec>(SimpleRegionSpec::class.java) {
  override fun deserialize(parser: JsonParser, context: DeserializationContext): SimpleRegionSpec =
    SimpleRegionSpec(parser.valueAsString)
}
