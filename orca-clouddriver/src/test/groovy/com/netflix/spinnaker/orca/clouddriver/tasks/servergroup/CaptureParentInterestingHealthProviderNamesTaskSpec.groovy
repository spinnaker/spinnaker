/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class CaptureParentInterestingHealthProviderNamesTaskSpec extends Specification {
  @Subject
  def task = new CaptureParentInterestingHealthProviderNamesTask()

  @Unroll
  def "should verify interesting health provider names from parent stage"() {
    given:
    Stage currentStage
    Stage parentStage
    pipeline {
      parentStage = stage {
        id = "parent"
        context = parentContext
      }
      currentStage = stage {
        parentStageId = "parent"
      }
    }

    when:
    def taskResult = task.execute(currentStage)

    then:
    taskResult.status == ExecutionStatus.SUCCEEDED
    taskResult.context == expectedStageOutputs

    where:
    parentContext                               || expectedStageOutputs
    [:]                                         || [:]
    [interestingHealthProviderNames: ["Titus"]] || [interestingHealthProviderNames: ["Titus"]]
    [interestingHealthProviderNames: []]        || [interestingHealthProviderNames: []]
  }

}
