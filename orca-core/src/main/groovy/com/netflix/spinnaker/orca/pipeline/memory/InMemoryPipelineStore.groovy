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

package com.netflix.spinnaker.orca.pipeline.memory

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.pipeline.Pipeline
import com.netflix.spinnaker.orca.pipeline.PipelineStore

/**
 * In-memory implementation of {@link PipelineStore} intended for use in testing
 */
@CompileStatic
class InMemoryPipelineStore implements PipelineStore {

  private final Map<String, Pipeline> pipelines = [:]

  @Override
  void store(Pipeline pipeline) {
    if (!pipeline.id) {
      pipeline.id = UUID.randomUUID().toString()
    }
    pipelines[pipeline.id] = pipeline
  }

  @Override
  Pipeline retrieve(String id) {
    assert pipelines.containsKey(id) // TODO: introduce an exception class here
    pipelines[id]
  }
}
