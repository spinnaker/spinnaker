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

import com.netflix.spinnaker.orca.clouddriver.utils.TrafficGuard
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import spock.lang.Specification
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class TargetServerGroupLinearStageSupportSpec extends Specification {

  def resolver = Stub(TargetServerGroupResolver)
  def trafficGuard = Stub(TrafficGuard)
  def supportStage = new TestSupport()

  def setup() {
    supportStage.resolver = resolver
    supportStage.trafficGuard = trafficGuard
  }

  @Unroll
  def "should inject determineTargetReferences stage when target is dynamic and the stage is #description"() {
    given:
    def pipeline = pipeline {
      stage {
        type = "test"
        refId = "1"
        context["regions"] = ["us-east-1"]
        context["target"] = "current_asg_dynamic"
        stage {
          type = "test"
          refId = "1<1"
          context["regions"] = ["us-east-1"]
          context["target"] = "current_asg_dynamic"
        }
      }
    }
    def stage = pipeline.stageByRef(ref)

    when:
    def graph = StageGraphBuilder.beforeStages(stage)
    supportStage.beforeStages(stage, graph)

    then:
    graph.build()*.name == stageNamesBefore

    where:
    ref   | description || stageNamesBefore
    "1"   | "top level" || ["determineTargetServerGroup", "testSupport"]
    "1<1" | "synthetic" || ["preDynamic"]
  }

  @Unroll
  def "should inject a stage before for each extra region when the target is dynamically bound"() {
    given:
    def stage = stage {
      type = "test"
      context[(locationType + "s")] = ["us-east-1", "us-west-1", "us-west-2", "eu-west-2"]
      context["target"] = "current_asg_dynamic"
      context["cloudProvider"] = cloudProvider
    }

    when:
    def graph = StageGraphBuilder.beforeStages(stage)
    supportStage.beforeStages(stage, graph)
    def syntheticStages = graph.build().toList()

    then:
    syntheticStages*.name == ["determineTargetServerGroup"] + (["testSupport"] * 4)
    syntheticStages.tail()*.context[locationType].flatten() == ["us-east-1", "us-west-1", "us-west-2", "eu-west-2"]
    syntheticStages.tail()*.context[oppositeLocationType].flatten().every {
      it == null
    }

    where:
    locationType | oppositeLocationType | cloudProvider
    "region"     | "zone"               | null
    "zone"       | "region"             | "gce"
  }

  def "should inject a stage before for each extra target when target is not dynamically bound"() {
    given:
    def stage = stage {
      type = "test"
      context["region"] = "should be overridden"
    }

    and:
    resolver.resolveByParams(_) >> [
      new TargetServerGroup(name: "asg-v001", region: "us-east-1"),
      new TargetServerGroup(name: "asg-v001", region: "us-west-1"),
      new TargetServerGroup(name: "asg-v002", region: "us-west-2"),
      new TargetServerGroup(name: "asg-v003", region: "eu-west-2"),
    ]

    when:
    def graph = StageGraphBuilder.beforeStages(stage)
    supportStage.beforeStages(stage, graph)
    def syntheticStages = graph.build().toList()

    then:
    syntheticStages*.name == ["testSupport"] * 4
    syntheticStages*.context.region.flatten() == ["us-east-1", "us-west-1", "us-west-2", "eu-west-2"]
  }

  @Unroll
  def "#target should inject stages correctly before and after each location stage"() {
    given:
    def pipeline = pipeline {
      stage {
        type = "test"
        refId = "1"
        stage {
          type = "test"
          refId = "1<1"
          context["target"] = target
          context["regions"] = ["us-east-1", "us-west-1"]
        }
      }
    }
    def stage = pipeline.stageByRef("1<1")

    and:
    resolver.resolveByParams(_) >> [
      new TargetServerGroup(name: "asg-v001", region: "us-east-1"),
      new TargetServerGroup(name: "asg-v002", region: "us-west-1"),
    ]

    when:
    def graph = StageGraphBuilder.beforeStages(stage)
    supportStage.beforeStages(stage, graph)

    then:
    graph.build()*.name == beforeNames

    when:
    graph = StageGraphBuilder.afterStages(stage)
    supportStage.afterStages(stage, graph)

    then:
    graph.build()*.name == afterNames

    where:
    target                | beforeNames    | afterNames
    "current_asg"         | ["preStatic"]  | ["postStatic"]
    "current_asg_dynamic" | ["preDynamic"] | ["postDynamic"]
  }

  def "should support 'asgName' and 'serverGroupName' when extracting server group name from context"() {
    expect:
    supportStage.getServerGroupName([:]) == null
    supportStage.getServerGroupName([asgName: "asgName"]) == "asgName"
    supportStage.getServerGroupName([serverGroupName: "serverGroupName"]) == "serverGroupName"

    supportStage.getServerGroupName([asgName: "asgName", serverGroupName: "serverGroupName"]) == "serverGroupName"
  }

  def "should remove 'asgName' and 'serverGroupName' when removing server group name from context"() {
    expect:
    supportStage.removeServerGroupName([:]) == [:]
    supportStage.removeServerGroupName([foo: "bar", asgName: "asgName"]) == [foo: "bar"]
    supportStage.removeServerGroupName([foo: "bar", serverGroupName: "serverGroupName"]) == [foo: "bar"]
    supportStage.removeServerGroupName([foo: "bar", asgName: "asgName", serverGroupName: "serverGroupName"]) == [foo: "bar"]
  }

  class TestSupport extends TargetServerGroupLinearStageSupport {
    @Override
    void preStatic(Map<String, Object> descriptor, StageGraphBuilder graph) {
      graph.add {
        it.type = "whatever"
        it.name = "preStatic"
        it.context = ["abc": 123]
      }
    }

    @Override
    void postStatic(Map<String, Object> descriptor, StageGraphBuilder graph) {
      graph.add {
        it.type = "whatever"
        it.name = "postStatic"
        it.context = ["abc": 123]
      }
    }

    @Override
    void preDynamic(Map<String, Object> context, StageGraphBuilder graph) {
      graph.add {
        it.type = "whatever"
        it.name = "preDynamic"
        it.context = ["abc": 123]
      }
    }

    @Override
    void postDynamic(Map<String, Object> context, StageGraphBuilder graph) {
      graph.add {
        it.type = "whatever"
        it.name = "postDynamic"
        it.context = ["abc": 123]
      }
    }
  }
}
