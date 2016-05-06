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
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step
import spock.lang.Specification
import spock.lang.Unroll

class TargetServerGroupLinearStageSupportSpec extends Specification {

  def resolver = Spy(TargetServerGroupResolver)
  def supportStage = new TestSupportStage(resolver: resolver)

  @Unroll
  void "#description determineTargetReferences stage when target is dynamic and parentStageId is #parentStageId"() {
    given:
    def stage = new PipelineStage(new Pipeline(), "test", [regions: ["us-east-1"], target: "current_asg_dynamic"])
    stage.parentStageId = parentStageId

    when:
    supportStage.composeTargets(stage)

    then:
    stage.beforeStages.size() == stageNamesBefore.size()
    stage.afterStages.size() == 0
    stage.beforeStages*.name == stageNamesBefore

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
    supportStage.composeTargets(stage)

    then:
    stage.beforeStages.size() == 1
    stage.afterStages.size() == 3
    stage.afterStages*.name == ["testSupportStage", "testSupportStage", "testSupportStage"]
    stage.context[locationType] == "us-east-1"
    stage.context[oppositeLocationType] == null
    stage.afterStages*.context[locationType].flatten() == ["us-west-1", "us-west-2", "eu-west-2"]

    where:
    locationType | oppositeLocationType | cloudProvider
    "region"     | "zone"               | null
    "zone"       | "region"             | 'gce'
  }

  void "should inject a stage after for each extra target when target is not dynamically bound"() {
    given:
    def stage = new PipelineStage(new Pipeline(), "test", ['region':'should be overridden'])

    when:
    supportStage.composeTargets(stage)

    then:
    1 * resolver.resolveByParams(_) >> [
      new TargetServerGroup(name: "asg-v001", region: "us-east-1"),
      new TargetServerGroup(name: "asg-v001", region: "us-west-1"),
      new TargetServerGroup(name: "asg-v002", region: "us-west-2"),
      new TargetServerGroup(name: "asg-v003", region: "eu-west-2"),
    ]
    stage.beforeStages.size() == 0
    stage.afterStages.size() == 3 // one for each region
    stage.afterStages*.name == ["testSupportStage", "testSupportStage", "testSupportStage"]
    stage.context.region == "us-east-1"
    stage.afterStages*.context.region.flatten() == ["us-west-1", "us-west-2", "eu-west-2"]
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
    supportStage.composeTargets(stage)

    then:
    (shouldResolve ? 1 : 0) * resolver.resolveByParams(_) >> [
        new TargetServerGroup(name: "asg-v001", region: "us-east-1"),
        new TargetServerGroup(name: "asg-v002", region: "us-west-1"),
    ]
    stage.beforeStages*.name == beforeNames
    stage.afterStages*.name == ["testPostInjectable", "testPreInjectable", "testSupportStage", "testPostInjectable"]

    where:
    target                | beforeNames                                         | shouldResolve
    "current_asg"         | ["testPreInjectable"]                               | true
    "current_asg_dynamic" | ["testPreInjectable", "determineTargetServerGroup"] | false
  }

  class TestSupportStage extends TargetServerGroupLinearStageSupport {

    List<TargetServerGroupLinearStageSupport.Injectable> preInjectables
    List<TargetServerGroupLinearStageSupport.Injectable> postInjectables

    TestSupportStage() {
      super("testSupportStage")
    }

    @Override
    public List<Step> buildSteps(Stage stage) {
      []
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
