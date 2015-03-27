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

import com.google.common.collect.ImmutableList
import com.netflix.spinnaker.orca.ExecutionStatus
import groovy.transform.CompileStatic


import static com.netflix.spinnaker.orca.ExecutionStatus.CANCELED
import static com.netflix.spinnaker.orca.ExecutionStatus.FAILED
import static com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import static com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL

@CompileStatic
abstract class Execution<T> implements Serializable {
  String id
  String application
  final Map<String, Object> appConfig = [:]
  List<Stage<T>> stages = []
  boolean canceled

  Stage namedStage(String type) {
    stages.find {
      it.type == type
    }
  }

  Long getStartTime() {
    stages ? stages.first().startTime : null
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

    if(stages.status.any{ it == TERMINAL }) {
      return TERMINAL
    }

    def lastStartedStatus = stages.status.reverse().find {
      it != NOT_STARTED
    }

    if (!lastStartedStatus) {
      NOT_STARTED
    } else if (lastStartedStatus == SUCCEEDED && stages.last().status != SUCCEEDED) {
      RUNNING
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
}
