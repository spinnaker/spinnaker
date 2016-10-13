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

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Stage

/**
 * Deserializes stages as mutable or immutable objects appropriately
 */
@CompileStatic
class StageDeserializer extends JsonDeserializer<Stage> {
  private final JsonSlurper slurper
  private final ObjectMapper objectMapper

  StageDeserializer() {
    slurper = new JsonSlurper()
    objectMapper = new ObjectMapper()
  }

  /**
   * Uses {@link JsonSlurper} to convert the quoted string json to a Map object
   * Uses {@link ObjectMapper} to convert the Map object to its approrpiate stage
   */
  StageDeserializer(JsonSlurper slurper, ObjectMapper objectMapper) {
    this.slurper = slurper
    this.objectMapper = objectMapper
  }

  @Override
  Stage deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
    (Stage) OrcaJackson.deserialize(jp)
  }
}
