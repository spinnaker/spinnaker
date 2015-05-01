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

package com.netflix.spinnaker.orca.json

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.ImmutableStageSupport
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Shared
import spock.lang.Specification

class StageDeserializerSpec extends Specification {
  ObjectMapper mapper = new OrcaObjectMapper()

  void "should deserialize immutable stages properly"() {
    setup:
      def pipeline = new Pipeline()
      def stage = new PipelineStage(pipeline, "foo", [key: "val"])
      def json = mapper.writeValueAsString(stage.asImmutable())

    when:
      def deserialized = mapper.readValue(json, Stage)

    then:
      deserialized.isImmutable()
      deserialized instanceof ImmutableStageSupport.ImmutableStage
      deserialized.self instanceof PipelineStage
  }
}
