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

package com.netflix.spinnaker.orca.pipeline.model

import com.fasterxml.jackson.annotation.JsonBackReference
import com.netflix.spinnaker.orca.ExecutionStatus
import groovy.transform.CompileStatic


import static ExecutionStatus.NOT_STARTED
import static java.util.Collections.EMPTY_MAP

@CompileStatic
abstract class AbstractStage<T extends Execution> implements Stage<T>, Serializable {
  String type
  String name
  Long startTime
  Long endTime
  ExecutionStatus status = NOT_STARTED
  @JsonBackReference
  Execution execution
  Map<String, Object> context = [:]
  boolean immutable = false
  List<Task> tasks = []

  /**
   * yolo
   */
  AbstractStage() {

  }

  AbstractStage(Execution execution, String type, String name, Map<String, Object> context) {
    this.execution = execution
    this.type = type
    this.name = name
    this.context = context
  }

  AbstractStage(Execution execution, String type, Map<String, Object> context) {
    this.execution = execution
    this.type = type
    this.context = context
  }

  AbstractStage(Execution execution, String type) {
    this(execution, type, EMPTY_MAP)
  }

  @Override
  Stage preceding(String type) {
    def i = execution.stages.indexOf(this)
    execution.stages[i..0].find {
      it.type == type
    }
  }

  Stage<T> asImmutable() {
    ImmutableStageSupport.toImmutable(this)
  }

  Stage<T> getSelf() {
    this
  }
}
