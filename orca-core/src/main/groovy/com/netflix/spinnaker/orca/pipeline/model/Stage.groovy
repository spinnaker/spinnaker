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

import com.fasterxml.jackson.annotation.JsonBackReference
import com.google.common.collect.ImmutableMap
import com.netflix.spinnaker.orca.PipelineStatus
import static com.netflix.spinnaker.orca.PipelineStatus.NOT_STARTED
import static java.util.Collections.EMPTY_MAP

/**
 * A _stage_ of an Orca _pipeline_.
 */
class Stage {

  /**
   * The type that corresponds to Mayo config.
   */
  String type
  @JsonBackReference Pipeline pipeline
  PipelineStatus status = NOT_STARTED
  final Map<String, Object> context = [:]

  Stage() {}

  Stage(String type) {
    this(null, type, EMPTY_MAP)
  }

  Stage(String type, Map<String, Object> context) {
    this(null, type, context)
  }

  Stage(Pipeline pipeline, String type) {
    this(pipeline, type, EMPTY_MAP)
  }

  Stage(Pipeline pipeline, String type, Map<String, Object> context) {
    this.pipeline = pipeline
    this.type = type
    this.context.putAll(context)
  }

  /**
   * Gets the last stage preceding this stage that has the specified type.
   */
  Stage preceding(String type) {
    if (!pipeline) {
      return null
    }
    def i = pipeline.stages.indexOf(this)
    pipeline.stages[i..0].find {
      it.type == type
    }
  }

  ImmutableStage asImmutable() {
    def self = this

    new ImmutableStage() {
      @Override
      String getType() {
        self.type
      }

      @Override
      ImmutablePipeline getPipeline() {
        self.pipeline.asImmutable()
      }

      @Override
      PipelineStatus getStatus() {
        self.status
      }

      @Override
      ImmutableMap<String, Object> getContext() {
        ImmutableMap.copyOf(self.context)
      }

      @Override
      ImmutableStage preceding(String type) {
        self.preceding(type)?.asImmutable()
      }
    }
  }
}
