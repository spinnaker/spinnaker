/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.jackson

import groovy.transform.CompileStatic
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.netflix.spinnaker.orca.pipeline.model.Stage

/**
 * Unwraps an immutable stage and properly adds type to serialized JSON
 */
@CompileStatic
class StageSerializer extends JsonSerializer<Stage> {
  public static final String TYPE_IDENTIFIER = "__type__"

  private final ObjectMapper objectMapper

  StageSerializer() {
    this.objectMapper = new ObjectMapper().registerModule(new GuavaModule())
  }

  StageSerializer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper
  }

  @Override
  void serialize(Stage value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
    def unwrapped = value.self
    def stageMap = objectMapper.convertValue(unwrapped, Map)
    stageMap[TYPE_IDENTIFIER] = unwrapped.class
    jgen.writeString(objectMapper.writeValueAsString(stageMap))
  }
}
