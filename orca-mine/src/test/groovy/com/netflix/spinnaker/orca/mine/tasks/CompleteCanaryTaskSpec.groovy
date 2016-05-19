package com.netflix.spinnaker.orca.mine.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification

import static com.netflix.spinnaker.orca.mine.tasks.CleanupCanaryTask.Action.DISABLE
import static com.netflix.spinnaker.orca.mine.tasks.CleanupCanaryTask.Action.DISABLE
import static com.netflix.spinnaker.orca.mine.tasks.CleanupCanaryTask.Action.TERMINATE
import static com.netflix.spinnaker.orca.mine.tasks.CleanupCanaryTask.Health.UNHEALTHY


/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

class CompleteCanaryTaskSpec extends Specification {

  def task = new CompleteCanaryTask()

  def "Canary canceled should report canceled"() {
    given:
    def stage = new PipelineStage(new Pipeline(), "ACATask", "ACATask", [
      canary: [
        status: [
          status: "CANCELED"
        ]
      ]
    ])

    when:
    def taskResult = task.execute(stage)

    then:
    taskResult.status == ExecutionStatus.CANCELED
  }

  def "Canaries overall result is successful return ExecutionStatus.SUCCEEDED"() {
    given:
    def stage = new PipelineStage(new Pipeline(), "ACATask", "ACATask", [
        canary: [
          canaryResult: [
              overallResult: "SUCCESS"
          ]
        ]
    ])

    when:
    def taskResult = task.execute(stage)

    then:
    taskResult.status == ExecutionStatus.SUCCEEDED
  }

  def "Canaries is marked with continueOnUnhealthy and canary IS unhealthy then return ExecutionStatus.FAILED_CONTINUE"() {
    given:
    def stage = new PipelineStage(new Pipeline(), "ACATask", "ACATask", [
      canary: [
        health: [
            health: "UNHEALTHY"
        ],
        canaryConfig: [
          continueOnUnhealthy: true
        ]
      ]
    ])

    when:
    def taskResult = task.execute(stage)

    then:
    taskResult.status == ExecutionStatus.FAILED_CONTINUE
  }

  def "Canaries NOT marked with continueOnUnhealthy and canary IS unhealthy then return throw exception"() {
    given:
    def stage = new PipelineStage(new Pipeline(), "ACATask", "ACATask", [
      canary: [
        health: [
          health: "UNHEALTHY"
        ],
        canaryConfig: [
          continueOnUnhealthy: false
        ]
      ]
    ])

    when:
    task.execute(stage)

    then:
    def error = thrown(IllegalStateException)
    error.message == "Canary failed"
  }

  def "Canary with NO continueOnUnhealthy on context and canary IS unhealthy then throw exception"() {
    given:
    def stage = new PipelineStage(new Pipeline(), "ACATask", "ACATask", [
      canary: [
        health: [
          health: "UNHEALTHY"
        ]
      ]
    ])

    when:
    task.execute(stage)

    then:
    def error = thrown(IllegalStateException)
    error.message == "Canary failed"
  }


}
