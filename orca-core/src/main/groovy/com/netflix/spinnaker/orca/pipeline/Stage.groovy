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
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.PipelineStatus
import static com.netflix.spinnaker.orca.PipelineStatus.NOT_STARTED

/**
 * A _stage_ of an Orca _pipeline_.
 */
@CompileStatic
class Stage implements Serializable {

  /**
   * @return the name that corresponds to Mayo config.
   */
  final String type

  /**
   * @return the pipeline that contains this stage.
   */
  final Pipeline pipeline

  /**
   * @return the status of the stage. Effectively this will mean the status of
   * the last {@link com.netflix.spinnaker.orca.Task} to be executed.
   */
  PipelineStatus status = NOT_STARTED

  Stage(Pipeline pipeline, String type, Map<String, Serializable> context) {
    this.pipeline = pipeline
    this.type = type
    this.context.putAll(context)
  }

  @VisibleForTesting
  Stage(String type, Map<String, Serializable> context = [:]) {
    this(null, type, context)
  }

  // TODO: ImmutableMaps?
  final Map<String, Serializable> context = [:]
  final Map<String, Serializable> outputs = [:]

  /**
   * Gets the last stage preceding this stage that has the specified type.
   */
  Stage preceding(String type) {
    def i = pipeline.stages.indexOf(this)
    pipeline.stages[i..0].find {
      it.type == type
    }
  }
}
