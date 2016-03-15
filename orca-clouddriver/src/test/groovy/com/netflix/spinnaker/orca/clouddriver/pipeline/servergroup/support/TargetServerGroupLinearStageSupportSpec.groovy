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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ResizeServerGroupStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import spock.lang.Specification
import spock.lang.Unroll

class TargetServerGroupLinearStageSupportSpec extends Specification {

  def resolver = Spy(TargetServerGroupResolver)
  def supportStage = new TestSupportStage(determineTargetServerGroupStage: new DetermineTargetServerGroupStage())

  void setup() {
    supportStage.resolver = resolver
  }

  @Unroll
  void "#description determineTargetReferences stage when target is dynamic and parentStageId is #parentStageId"() {
    given:
    def stage = new PipelineStage(new Pipeline(), "test", [regions: ["us-east-1"], target: "current_asg_dynamic"])
    stage.parentStageId = parentStageId

    when:
    def syntheticStages = supportStage.composeTargets(stage).groupBy { it.syntheticStageOwner }

    then:
    syntheticStages.getOrDefault(SyntheticStageOwner.STAGE_BEFORE, [])*.name == stageNamesBefore
    syntheticStages.getOrDefault(SyntheticStageOwner.STAGE_AFTER, []).isEmpty()

    where:
    parentStageId | stageNamesBefore               | description
    null          | ["determineTargetServerGroup"] | "should inject"
    "a"           | []                             | "should inject"
  }

  @Unroll
  void "should inject a stage for each extra region when the target is dynamically bound"() {
    given:
    def stage = new PipelineStage(new Pipeline(), "test", [
        (locationType + 's'): ["us-east-1", "us-west-1", "us-west-2", "eu-west-2"],
        target              : "current_asg_dynamic",
        cloudProvider       : cloudProvider
    ])

    when:
    def syntheticStages = supportStage.composeTargets(stage).groupBy { it.syntheticStageOwner }

    then:
    syntheticStages[SyntheticStageOwner.STAGE_BEFORE].size() == 1
    syntheticStages[SyntheticStageOwner.STAGE_AFTER].size() == 3
    syntheticStages[SyntheticStageOwner.STAGE_AFTER]*.name == ["testSupportStage", "testSupportStage", "testSupportStage"]
    stage.context[locationType] == "us-east-1"
    stage.context[oppositeLocationType] == null
    syntheticStages[SyntheticStageOwner.STAGE_AFTER]*.context[locationType].flatten() == ["us-west-1", "us-west-2", "eu-west-2"]

    where:
    locationType | oppositeLocationType | cloudProvider
    "region"     | "zone"               | null
    "zone"       | "region"             | 'gce'
  }

  void "should inject a stage after for each extra target when target is not dynamically bound"() {
    given:
    def stage = new PipelineStage(new Pipeline(), "test", ['region':'should be overridden'])

    when:
    def syntheticStages = supportStage.composeTargets(stage).groupBy { it.syntheticStageOwner }

    then:
    1 * resolver.resolveByParams(_) >> [
      new TargetServerGroup(name: "asg-v001", region: "us-east-1"),
      new TargetServerGroup(name: "asg-v001", region: "us-west-1"),
      new TargetServerGroup(name: "asg-v002", region: "us-west-2"),
      new TargetServerGroup(name: "asg-v003", region: "eu-west-2"),
    ]
    syntheticStages[SyntheticStageOwner.STAGE_BEFORE] == null
    syntheticStages[SyntheticStageOwner.STAGE_AFTER]*.name == ["testSupportStage", "testSupportStage", "testSupportStage"]
    syntheticStages[SyntheticStageOwner.STAGE_AFTER]*.context.region.flatten() == ["us-west-1", "us-west-2", "eu-west-2"]
    stage.context.region == "us-east-1"
  }

  @Unroll
  def "#target should inject stages correctly before and after each location stage"() {
    given:
    def stage = new PipelineStage(new Pipeline(), "test", [target: target, regions: ["us-east-1", "us-west-1"]])
    def arbitraryStageBuilder = new ResizeServerGroupStage()
    supportStage.preInjectables = [new TargetServerGroupLinearStageSupport.Injectable(
      name: "testPreInjectable",
      stage: arbitraryStageBuilder,
      context: ["abc": 123]
    )]
    supportStage.postInjectables = [new TargetServerGroupLinearStageSupport.Injectable(
      name: "testPostInjectable",
      stage: arbitraryStageBuilder,
      context: ["abc": 123]
    )]

    when:
    def syntheticStages = supportStage.composeTargets(stage).groupBy { it.syntheticStageOwner }

    then:
    (shouldResolve ? 1 : 0) * resolver.resolveByParams(_) >> [
        new TargetServerGroup(name: "asg-v001", region: "us-east-1"),
        new TargetServerGroup(name: "asg-v002", region: "us-west-1"),
    ]
    syntheticStages[SyntheticStageOwner.STAGE_BEFORE]*.name == beforeNames
    syntheticStages[SyntheticStageOwner.STAGE_AFTER]*.name == ["testPostInjectable", "testPreInjectable", "testSupportStage", "testPostInjectable"]

    where:
    target                | beforeNames                                         | shouldResolve
    "current_asg"         | ["testPreInjectable"]                               | true
    "current_asg_dynamic" | ["testPreInjectable", "determineTargetServerGroup"] | false
  }

  class TestSupportStage extends TargetServerGroupLinearStageSupport {

    List<TargetServerGroupLinearStageSupport.Injectable> preInjectables
    List<TargetServerGroupLinearStageSupport.Injectable> postInjectables

    TestSupportStage() {
      name = "testSupportStage"
    }

    @Override
    List<TargetServerGroupLinearStageSupport.Injectable> preStatic(Map descriptor) {
      preInjectables
    }

    @Override
    List<TargetServerGroupLinearStageSupport.Injectable> postStatic(Map descriptor) {
      postInjectables
    }

    @Override
    List<TargetServerGroupLinearStageSupport.Injectable> preDynamic(Map context) {
      preInjectables
    }

    @Override
    List<TargetServerGroupLinearStageSupport.Injectable> postDynamic(Map context) {
      postInjectables
    }
  }
}
