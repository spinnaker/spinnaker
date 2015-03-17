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

package com.netflix.spinnaker.orca.pipeline.model

import groovy.transform.CompileStatic

/**
 * A _stage_ of an Orca _pipeline_.
 */
@CompileStatic
class PipelineStage extends AbstractStage<Pipeline> {
  PipelineStage() {

  }

  PipelineStage(Pipeline pipeline, String type) {
    super(pipeline, type)
  }

  PipelineStage(Pipeline pipeline, String type, String name, Map<String, Object> context) {
    super(pipeline, type, name, context)
  }

  PipelineStage(Pipeline pipeline, String type, Map<String, Object> context) {
    super(pipeline, type, context)
  }
}
