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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.netflix.spinnaker.orca.pipeline.model.Stage


import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES

class OrcaObjectMapper extends ObjectMapper {
  private static final SimpleModule simpleModule = new SimpleModule().addSerializer(Stage, new StageSerializer())
    .addDeserializer(Stage, new StageDeserializer())
  public static final OrcaObjectMapper DEFAULT = new OrcaObjectMapper()

  OrcaObjectMapper() {
    super()
    registerModule(simpleModule)
    registerModule(new GuavaModule())
    configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
  }
}
