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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.google.common.collect.ImmutableList
import com.netflix.spinnaker.orca.ExecutionStatus
import groovy.transform.CompileStatic

import static com.netflix.spinnaker.orca.ExecutionStatus.*

@CompileStatic
abstract class Execution<T> implements Serializable {
  String id
  String application
  final Map<String, Object> appConfig = [:]
  List<Stage<T>> stages = []
  boolean canceled
  boolean parallel
  Long buildTime

  /*
   * Used to track Stages/Steps as they're built to prevent unnecessary re-builds in parallel pipelines
   */
  private final Set<Object> builtPipelineObjects = []

  Stage namedStage(String type) {
    stages.find {
      it.type == type
    }
  }

  Long getStartTime() {
    Long startTime = stages ? stages.first().startTime : null
    if (!startTime && stages.find { it.startTime != null }) {
      startTime = stages.findAll { it.startTime != null }.collect { it.startTime }.sort {}.get(0)
    }
    startTime
  }

  Long getEndTime() {
    if (stages && getStartTime()) {
      def reverseStages = stages.reverse()
      def lastStage = reverseStages.first()
      if (lastStage.endTime) {
        return lastStage.endTime
      } else {
        def lastFailed = reverseStages.find {
          it.status == FAILED
        }
        if (lastFailed) {
          return lastFailed.endTime
        }
      }
    }
    null
  }

  ExecutionStatus getStatus() {
    if (canceled) {
      return CANCELED
    }

    if (stages.status.any { it == TERMINAL }) {
      return TERMINAL
    }

    if (stages.status.any { it == FAILED }) {
      return FAILED
    }

    List<Stage<T>> nonEmptyStages = stages.findAll {
      it.tasks.size() > 0
    } as List

    if (!nonEmptyStages) {
      return NOT_STARTED
    }

    if (nonEmptyStages.status.every { it == SUCCEEDED }) {
      return SUCCEEDED
    }

    if (nonEmptyStages.status.any { it == RUNNING }) {
      return RUNNING
    }

    def lastStartedStatus = nonEmptyStages.status.reverse().find {
      it != NOT_STARTED
    }

    if (!lastStartedStatus) {
      NOT_STARTED
    } else if (lastStartedStatus == SUCCEEDED && nonEmptyStages.status.reverse().find { it != SUCCEEDED }) {
      return nonEmptyStages.status.any { it == SUSPENDED } ? SUSPENDED : RUNNING
    } else {
      lastStartedStatus
    }
  }

  Execution<T> asImmutable() {
    def self = this

    new Execution<T>() {
      @Override
      String getId() {
        self.id
      }

      @Override
      void setId(String id) {

      }

      @Override
      List<Stage> getStages() {
        ImmutableList.copyOf(self.stages)
      }

      @Override
      void setStages(List<Stage<T>> stages) {

      }

      @Override
      Stage namedStage(String type) {
        self.namedStage(type).asImmutable()
      }

      @Override
      Execution asImmutable() {
        this
      }
    }
  }

  @JsonIgnore
  Set<Object> getBuiltPipelineObjects() {
    return builtPipelineObjects
  }
}
