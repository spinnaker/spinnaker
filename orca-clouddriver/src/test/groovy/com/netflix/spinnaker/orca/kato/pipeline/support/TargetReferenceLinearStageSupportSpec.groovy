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

import com.netflix.spinnaker.orca.kato.pipeline.DetermineTargetReferenceStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import spock.lang.Specification
import spock.lang.Unroll

class TargetReferenceLinearStageSupportSpec extends Specification {

  @Unroll
  void "#description determineTargetReferences stage when target is dynamic and parentStageId is #parentStageId"() {
    given:
    def targetReferenceSupport = Mock(TargetReferenceSupport)
    def supportStage = new TargetReferenceLinearStageSupportStage()
    def stage = new PipelineStage(new Pipeline(), "test", [regions: ["us-east-1"]])
    stage.parentStageId = parentStageId
    supportStage.targetReferenceSupport = targetReferenceSupport

    when:
    def syntheticStages = supportStage.composeTargets(stage)
    def beforeStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_BEFORE }
    def afterStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_AFTER }

    then:
    beforeStages.size() == stageNamesBefore.size()
    afterStages.size() == 0
    beforeStages*.name == stageNamesBefore
    1 * targetReferenceSupport.isDynamicallyBound(stage) >> true

    where:
    parentStageId | stageNamesBefore              | description
    null          | ["determineTargetReferences"] | "should inject"
    "a"           | []                            | "should inject"
  }

  void "should inject a stage for each extra region when the target is dynamically bound"() {
    given:
    def targetReferenceSupport = Mock(TargetReferenceSupport)
    def supportStage = new TargetReferenceLinearStageSupportStage()
    def stage = new PipelineStage(new Pipeline(), "test", [regions: ["us-east-1", "us-west-1", "us-west-2", "eu-west-2"]])
    supportStage.targetReferenceSupport = targetReferenceSupport

    when:
    def syntheticStages = supportStage.composeTargets(stage)
    def beforeStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_BEFORE }
    def afterStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_AFTER }

    then:
    beforeStages.size() == 1
    afterStages.size() == 3
    afterStages*.name == ["targetReferenceLinearStageSupport", "targetReferenceLinearStageSupport", "targetReferenceLinearStageSupport"]
    stage.context.region == "us-east-1"
    afterStages*.context.region.flatten() == ["us-west-1", "us-west-2", "eu-west-2"]
    1 * targetReferenceSupport.isDynamicallyBound(stage) >> true
  }

  void "should inject a stage after for each extra target when target is not dynamically bound"() {
    given:
    def targetReferenceSupport = Mock(TargetReferenceSupport)
    def supportStage = new TargetReferenceLinearStageSupportStage()
    def stage = new PipelineStage(new Pipeline(), "test", [:])
    supportStage.targetReferenceSupport = targetReferenceSupport

    when:
    def syntheticStages = supportStage.composeTargets(stage)
    def beforeStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_BEFORE }
    def afterStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_AFTER }

    then:
    beforeStages.size() == 0
    afterStages.size() == 3
    afterStages*.name == ["targetReferenceLinearStageSupport", "targetReferenceLinearStageSupport", "targetReferenceLinearStageSupport"]
    1 * targetReferenceSupport.isDynamicallyBound(stage) >> false
    1 * targetReferenceSupport.getTargetAsgReferences(stage) >> [
      new TargetReference(region: "us-east-1", asg: [name: "asg-v001"]),
      new TargetReference(region: "us-west-1", asg: [name: "asg-v001"]),
      new TargetReference(region: "us-west-2", asg: [name: "asg-v002"]),
      new TargetReference(region: "eu-west-2", asg: [name: "asg-v003"]),
    ]
  }

  void "should throw a TargetReferenceNotFoundException when no static targets are found"() {
    given:
    def targetReferenceSupport = Mock(TargetReferenceSupport)
    def supportStage = new TargetReferenceLinearStageSupportStage()
    def stage = new PipelineStage(new Pipeline(), "test", [:])
    supportStage.targetReferenceSupport = targetReferenceSupport

    when:
    supportStage.composeTargets(stage)

    then:
    thrown TargetReferenceNotFoundException
    1 * targetReferenceSupport.isDynamicallyBound(stage) >> false
    1 * targetReferenceSupport.getTargetAsgReferences(stage) >> []
  }

  void "should be able to resolve parameters from strategy configuration"() {
    given:
    def targetReferenceSupport = Mock(TargetReferenceSupport)
    def supportStage = new TargetReferenceLinearStageSupportStage()
    def stage = new PipelineStage(new Pipeline(), "test", [:])
    supportStage.targetReferenceSupport = targetReferenceSupport

    stage.execution.trigger.parameters = [
      strategy   : true,
      region     : 'us-west-1',
      credentials: 'test',
      cluster    : 'myappcluster'
    ]

    when:
    supportStage.composeTargets(stage)

    then:
    thrown TargetReferenceNotFoundException
    stage.context.regions == ['us-west-1']
    stage.context.credentials == 'test'
    stage.context.cluster == 'myappcluster'
  }

  class TargetReferenceLinearStageSupportStage extends TargetReferenceLinearStageSupport {
    TargetReferenceLinearStageSupportStage() {
      this.determineTargetReferenceStage = new DetermineTargetReferenceStage()
    }
  }
}
