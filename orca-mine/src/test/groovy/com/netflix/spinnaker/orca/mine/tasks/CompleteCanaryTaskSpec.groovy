/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.mine.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Unroll

class CompleteCanaryTaskSpec extends Specification {

  def task = new CompleteCanaryTask()

  def "Canary canceled should report canceled"() {
    given:
    def stage = new Stage<>(new Pipeline(), "ACATask", "ACATask", [
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
    def stage = new Stage<>(new Pipeline(), "ACATask", "ACATask", [
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

  @Unroll
  def "Canary is marked with continueOnUnhealthy and canary heath is #health then return ExecutionStatus.FAILED_CONTINUE"() {
    given:
    def stage = new Stage<>(new Pipeline(), "ACATask", "ACATask", [
      canary: [
        health: [
            health: health
        ]
      ],
      continueOnUnhealthy: true
    ])

    when:
    def taskResult = task.execute(stage)

    then:
    taskResult.status == ExecutionStatus.FAILED_CONTINUE

    where:
    health      | _
    "UNHEALTHY" | _
    "UNKNOWN"   | _

  }


  @Unroll
  def "Canary is marked with continueOnUnhealthy: #continueOnUnhealthy and canary has a status of #overallResult then return #expectedStatus"() {
    given:
    def stage = new Stage<>(new Pipeline(), "ACATask", "ACATask", [
      canary: [
        canaryResult: [
          overallResult: overallResult
        ]
      ],
      continueOnUnhealthy: continueOnUnhealthy
    ])

    when:
    def taskResult = task.execute(stage)

    then:
    taskResult.status == expectedStatus

    where:
    overallResult | continueOnUnhealthy | expectedStatus
    "FAILURE"     | true                | ExecutionStatus.FAILED_CONTINUE
    "FAILURE"     | false               | ExecutionStatus.TERMINAL

  }

  def "Canaries NOT marked with continueOnUnhealthy and canary IS unhealthy should be TERMINAL"() {
    given:
    def stage = new Stage<>(new Pipeline(), "ACATask", "ACATask", [
      canary: [
        health: [
          health: "UNHEALTHY"
        ]
      ],
      continueOnUnhealthy: false
    ])

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.TERMINAL
  }

  def "Canary with NO continueOnUnhealthy on context and canary IS unhealthy then should be terminal"() {
    given:
    def stage = new Stage<>(new Pipeline(), "ACATask", "ACATask", [
      canary: [
        health: [
          health: "UNHEALTHY"
        ]
      ]
    ])

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.TERMINAL
  }

  def "Canary with is in an unhandeled state then throw and error"() {
    given:
    def stage = new Stage<>(new Pipeline(), "ACATask", "ACATask", [
      canary: [
        status: [status: ""],
        health: [
          health: ""
        ]
      ]
    ])
    when:
    task.execute(stage)

    then:
    def error = thrown(IllegalStateException)
    error.message == "Canary in unhandled state"
  }

}
