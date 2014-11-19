/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.pipeline.persistence.memory

import groovy.transform.CompileStatic
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.InvalidPipelineId
import com.netflix.spinnaker.orca.pipeline.persistence.PipelineStore
import org.springframework.beans.factory.annotation.Autowired

/**
 * In-memory implementation of {@link PipelineStore} intended for use in testing
 */
@CompileStatic
class InMemoryPipelineStore implements PipelineStore {

  @Delegate(includes = "clear", interfaces = false)
  private final Map<String, String> pipelines = [:]

  private final ObjectMapper mapper

  @Autowired
  InMemoryPipelineStore(ObjectMapper mapper) {
    this.mapper = mapper
  }

  InMemoryPipelineStore() {
    this(new ObjectMapper())
  }

  @Override
  void store(Pipeline pipeline) {
    if (!pipeline.id) {
      pipeline.id = UUID.randomUUID().toString()
    }
    pipelines[pipeline.id] = mapper.writeValueAsString(pipeline)
  }

  @Override
  Pipeline retrieve(String id) {
    if (!pipelines.containsKey(id)) {
      throw new InvalidPipelineId(id)
    }
    mapper.readValue(pipelines[id], Pipeline)
  }
}
