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
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class CaptureParentInterestingHealthProviderNamesTaskSpec extends Specification {
  @Subject
  def task = new CaptureParentInterestingHealthProviderNamesTask()

  @Unroll
  def "should verify interesting health provider names from parent stage"() {
    given:
    def stage = new Stage<>(new Pipeline(), "", [:])
    def parentStage = new Stage(new Pipeline(), "", context as Map)

    and:
    stage.execution = new Pipeline(stages: [ parentStage ])
    stage.parentStageId = parentStage.id


    when:
    def taskResult = task.execute(stage)

    then:
    taskResult.status == ExecutionStatus.SUCCEEDED
    (taskResult.getContext() as Map) == expectedStageOutputs

    where:
    context                                       || expectedStageOutputs
    [:]                                           || [:]
    [interestingHealthProviderNames: ["Titus"]]   || [interestingHealthProviderNames: ["Titus"]]
    [interestingHealthProviderNames: []]          || [interestingHealthProviderNames: []]
  }

}
