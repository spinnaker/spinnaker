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

package com.netflix.spinnaker.orca.pipeline

import groovy.transform.CompileStatic
import com.google.common.collect.ImmutableMap
import com.netflix.spinnaker.orca.PipelineStatus
import static com.netflix.spinnaker.orca.PipelineStatus.NOT_STARTED

@CompileStatic
class PipelineStage implements ConfigurableStage {

  final String type
  final Pipeline pipeline
  PipelineStatus status = NOT_STARTED
  private final Map<String, Serializable> context = [:] as PipelineStageContext

  PipelineStage(Pipeline pipeline, String type, Map<String, Serializable> context) {
    this.pipeline = pipeline
    this.type = type
    this.context.putAll(context)
  }

  PipelineStage(String type, Map<String, Serializable> context = [:]) {
    this(null, type, context)
  }

  /**
   * Gets the last stage preceding this stage that has the specified type.
   */
  @Override
  ImmutableMap<String, Serializable> getContext() {
    ImmutableMap.copyOf(context)
  }

  @Override
  Stage preceding(String type) {
    if (!pipeline) {
      return null
    }
    def i = pipeline.stages.indexOf(this)
    pipeline.stages[i..0].find {
      it.type == type
    }
  }

  @Override
  void addToContext(String key, Serializable value) {
    context.put(key, value)
  }

  void updateContext(Map<String, Serializable> data) {
    context.putAll(data)
  }
}
