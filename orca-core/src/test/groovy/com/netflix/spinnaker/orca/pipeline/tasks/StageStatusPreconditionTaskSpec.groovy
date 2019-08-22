/*
 * Copyright 2019 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipeline.tasks

import spock.lang.Specification
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import static com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class StageStatusPreconditionTaskSpec extends Specification {
  @Unroll
  def "should evaluate stage status precondition against stage context at execution time"() {
    given:
    def task = new StageStatusPreconditionTask()
    def stage = stage {
      name = "Check Stage Status"
      context.context = [
        stageName: stageName,
        stageStatus: stageStatus
      ]
      execution = pipeline {
        stage {
          name = "Stage A"
          status = SUCCEEDED
        }
        stage {
          name = "Stage B"
          status = TERMINAL
        }
      }
    }

    when:
    def taskResult = task.execute(stage)

    then:
    taskResult.status == taskResultStatus

    where:
    stageName | stageStatus || taskResultStatus
    "Stage A" | "SUCCEEDED" || SUCCEEDED
    "Stage B" | "TERMINAL"  || SUCCEEDED
  }

  def "should throw error when input is invalid"() {
    given:
    def task = new StageStatusPreconditionTask()
    def stage = stage {
      name = "Check Stage Status"
      context.context = [
        stageName: stageName,
        stageStatus: stageStatus
      ]
      execution = pipeline {
        stage {
          name = "Stage A"
          status = SUCCEEDED
        }
      }
    }

    when:
    task.execute(stage)

    then:
    thrown(IllegalArgumentException)

    where:
    stageName | stageStatus
    "Invalid" | "SUCCEEDED"
    null      | "SUCCEEDED"
    "Stage A" |  null
  }

  def "should throw error when status assertion is false"() {
    given:
    def task = new StageStatusPreconditionTask()
    def stage = stage {
      name = "Check Stage Status"
      context.context = [
        stageName: stageName,
        stageStatus: stageStatus
      ]
      execution = pipeline {
        stage {
          name = "Stage A"
          status = SUCCEEDED
        }
        stage {
          name = "Stage B"
          status = TERMINAL
        }
      }
    }

    when:
    task.execute(stage)

    then:
    thrown(RuntimeException)

    where:
    stageName | stageStatus
    "Stage A" | "TERMINAL"
    "Stage B" | "SUCCEEDED"
  }
}
