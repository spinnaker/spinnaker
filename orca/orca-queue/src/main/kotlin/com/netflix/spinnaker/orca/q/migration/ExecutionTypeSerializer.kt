/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.q.migration

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.ORCHESTRATION
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.deser.std.StdDeserializer
import tools.jackson.databind.exc.InvalidFormatException

internal const val PIPELINE_CLASS_NAME = "com.netflix.spinnaker.orca.pipeline.model.Pipeline"
internal const val ORCHESTRATION_CLASS_NAME = "com.netflix.spinnaker.orca.pipeline.model.Orchestration"

class ExecutionTypeDeserializer : StdDeserializer<ExecutionType>(ExecutionType::class.java) {
  override fun handledType(): Class<*> = ExecutionType::class.java

  override fun deserialize(
    p: JsonParser,
    ctxt: DeserializationContext
  ) = when (p.valueAsString) {
    PIPELINE_CLASS_NAME, PIPELINE.name -> PIPELINE
    ORCHESTRATION_CLASS_NAME, ORCHESTRATION.name -> ORCHESTRATION
    else -> throw InvalidFormatException(
      p,
      "Invalid value for ExecutionType",
      p.valueAsString,
      ExecutionType::class.java
    )
  }
}
