/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.pipeline.support

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step
import spock.lang.Specification
import spock.lang.Unroll

class TargetReferenceLinearStageSupportSpec extends Specification {

  @Unroll
  void "#description determineTargetReferences stage when target is dynamic and parentStageId is #parentStageId"() {
    given:
    def targetReferenceSupport = Mock(TargetReferenceSupport)
    def supportStage = new TargetReferenceLinearStageSupportStage()
    def stage = new PipelineStage(new Pipeline(), "test")
    stage.parentStageId = parentStageId
    supportStage.targetReferenceSupport = targetReferenceSupport

    when:
    supportStage.composeTargets(stage)

    then:
    stage.beforeStages.size() == stageNamesBefore.size()
    stage.afterStages.size() == 0
    stage.beforeStages*.name == stageNamesBefore
    1 * targetReferenceSupport.isDynamicallyBound(stage) >> true

    where:
    parentStageId | stageNamesBefore              | description
    null          | ["determineTargetReferences"] | "should inject"
    "a"           | []                            | "should inject"
  }

  void "should inject a stage after for each extra target when target is not dynamically bound"() {
    given:
    def targetReferenceSupport = Mock(TargetReferenceSupport)
    def supportStage = new TargetReferenceLinearStageSupportStage()
    def stage = new PipelineStage(new Pipeline(), "test", [:])
    supportStage.targetReferenceSupport = targetReferenceSupport

    when:
    supportStage.composeTargets(stage)

    then:
    stage.beforeStages.size() == 0
    stage.afterStages.size() == 2
    stage.afterStages*.name == ["targetReferenceLinearStageSupportStage", "targetReferenceLinearStageSupportStage"]
    1 * targetReferenceSupport.isDynamicallyBound(stage) >> false
    1 * targetReferenceSupport.getTargetAsgReferences(stage) >> [
      new TargetReference(region: "us-east-1", asg: [ name: "asg-v001" ]),
      new TargetReference(region: "us-west-1", asg: [ name: "asg-v001" ]),
      new TargetReference(region: "us-west-2", asg: [ name: "asg-v002" ]),
      new TargetReference(region: "eu-west-2", asg: [ name: "asg-v003" ]),
    ]
  }

  class TargetReferenceLinearStageSupportStage extends TargetReferenceLinearStageSupport {

    TargetReferenceLinearStageSupportStage() {
      super("targetReferenceLinearStageSupportStage")
    }

    @Override
    public List<Step> buildSteps(Stage stage) {
      []
    }
  }
}
