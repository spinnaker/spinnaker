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

import com.fasterxml.jackson.annotation.JsonBackReference
import com.netflix.spinnaker.orca.PipelineStatus
import static com.netflix.spinnaker.orca.PipelineStatus.NOT_STARTED

/**
 * A _stage_ of an Orca _pipeline_.
 */
class Stage implements Serializable {

  /**
   * @return the type that corresponds to Mayo config.
   */
  String type
  @JsonBackReference Pipeline pipeline
  PipelineStatus status = NOT_STARTED
  Map<String, Serializable> context = [:]

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
}
