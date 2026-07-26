/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.warmpool

import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification

class AbstractWarmPoolTaskSpec extends Specification {
  def katoService = Mock(KatoService) {
    _ * requestOperations(_, _) >> {
      return new TaskId(id: "1")
    }
  }

  private TargetServerGroup tSG(String name, String region = "us-west-1") {
    return new TargetServerGroup(name: name, region: region, asg: [:])
  }

  def "should resolve the target server group and submit an ops request for it"() {
    given:
    def context = [
      region   : "us-west-1",
      minSize  : 2,
      poolState: "Stopped"
    ]
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), null, context)
    def targetServerGroupResolver = Mock(TargetServerGroupResolver) {
      1 * resolve(_) >> {
        return [tSG("targetAsg")]
      }
    }
    def task = new UpsertWarmPoolTask(resolver: targetServerGroupResolver, katoService: katoService)

    when:
    def result = task.execute(stage)

    then:
    result.context.asgName == "targetAsg"
    result.context."kato.last.task.id".id == "1"
    result.context."deploy.server.groups" == ["us-west-1": ["targetAsg"]]
  }

  def "should get target reference dynamically when stage is dynamic"() {
    given:
    def tsg = tSG("targetAsg")
    def resolver = GroovySpy(TargetServerGroupResolver, global: true)
    GroovySpy(TargetServerGroup, global: true, constructorArgs: [[:]])

    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), null, [region: "us-west-1"])
    def task = new UpsertWarmPoolTask(resolver: resolver, katoService: katoService)

    when:
    task.execute(stage)

    then:
    TargetServerGroup.isDynamicallyBound(stage) >> true
    TargetServerGroupResolver.fromPreviousStage(stage) >> tsg
  }

  def "should send the resolved asg to kato in the description's asgs field"() {
    given:
    def tsg = tSG("targetAsg")
    GroovySpy(TargetServerGroup, global: true, constructorArgs: [[:]])
    def resolver = GroovySpy(TargetServerGroupResolver, global: true)
    KatoService katoService = Mock(KatoService)

    def ctx = [region: "us-west-1", cloudProvider: "abc", minSize: 2, poolState: "Stopped"]
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), null, ctx)
    def task = new UpsertWarmPoolTask(resolver: resolver, katoService: katoService)

    when:
    task.execute(stage)

    then:
    TargetServerGroup.isDynamicallyBound(stage) >> true
    TargetServerGroupResolver.fromPreviousStage(stage) >> tsg
    katoService.requestOperations("abc", { Map m ->
      m.upsertWarmPoolDescription.asgs == [[serverGroupName: "targetAsg", region: "us-west-1"]] &&
        m.upsertWarmPoolDescription.minSize == 2 &&
        m.upsertWarmPoolDescription.poolState == "Stopped"
    }) >> {
      return new TaskId(id: "1")
    }
    0 * katoService.requestOperations(_, _)
  }
}
