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
package com.netflix.spinnaker.orca.pipeline.model.support

import tools.jackson.core.JsonParser
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.deser.std.StdDeserializer

internal class RequisiteStageRefIdDeserializer : StdDeserializer<Collection<String>?>(Collection::class.java) {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Collection<String> {
    return ctxt.readValue(p, object : TypeReference<Collection<String>?>() {}) ?: return setOf()
  }
}
