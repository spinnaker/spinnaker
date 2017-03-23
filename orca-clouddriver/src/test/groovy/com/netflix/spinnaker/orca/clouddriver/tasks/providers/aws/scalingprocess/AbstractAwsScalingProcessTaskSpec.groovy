/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.scalingprocess

import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Unroll

class AbstractAwsScalingProcessTaskSpec extends Specification {
  def katoService = Mock(KatoService) {
    _ * requestOperations(_, _) >> {
      return rx.Observable.from([new TaskId(id: "1")])
    }
  }

  @Unroll
  def "should only resume/suspend scaling processes that are not already in the target state"() {
    given:
      def stage = new Stage<>(new Pipeline(), null, context)
      def targetServerGroupResolver = Mock(TargetServerGroupResolver) {
        1 * resolve(_) >> {
          return targetServerGroups
        }
      }

      def task = isResume ?
        new ResumeAwsScalingProcessTask(resolver: targetServerGroupResolver, katoService: katoService) :
        new SuspendAwsScalingProcessTask(resolver: targetServerGroupResolver, katoService: katoService)

    when:
      def result = task.execute(stage)
      def outputs = result.stageOutputs
      def globalOutputs = result.globalOutputs

    then:
      outputs.processes == expectedScalingProcesses
      outputs.containsKey("kato.last.task.id") == !expectedScalingProcesses.isEmpty()
      globalOutputs["scalingProcesses.${context.asgName}" as String] == expectedScalingProcesses

    where:
      isResume | context                         | targetServerGroups             || expectedScalingProcesses
      true     | sD("targetAsg", ["Launch"])     | [tSG("targetAsg", ["Launch"])] || ["Launch"]
      true     | sD("targetAsg", [], ["Launch"]) | [tSG("targetAsg", ["Launch"])] || ["Launch"]
      true     | sD("targetAsg", ["Launch"])     | [tSG("targetAsg", [])]         || []
      true     | sD("targetAsg", ["Launch"])     | [tSG("targetAsg", [])]         || []
      false    | sD("targetAsg", ["Launch"])     | [tSG("targetAsg", [])]         || ["Launch"]
      false    | sD("targetAsg", [], ["Launch"]) | [tSG("targetAsg", [])]         || ["Launch"]
      false    | sD("targetAsg", ["Launch"])     | [tSG("targetAsg", ["Launch"])] || []
  }

  private Map<String, Object> sD(String asgName,
                                 List<String> processes,
                                 List<String> globalProcesses = [],
                                 String region = "us-west-1") {
    return [
      asgName: asgName, processes: processes, regions: [region], ("scalingProcesses.${asgName}" as String): globalProcesses
    ]
  }

  private TargetServerGroup tSG(String name, List<String> suspendedProcesses, String region = "us-west-1") {
    return new TargetServerGroup(
      name: name,
      region: region,
      asg : [
        suspendedProcesses: suspendedProcesses.collect {
          [processName: it]
        }
      ]
    )
  }

  def "should get target reference dynamically when stage is dynamic"() {
    given:
      def tsg = tSG("targetAsg", ["Launch"])
      def resolver = GroovySpy(TargetServerGroupResolver, global: true)
      GroovySpy(TargetServerGroup, global: true, constructorArgs: [tsg])

      def stage = new Stage<>(new Pipeline(), null, sD("targetAsg", ["Launch"]))
      def task = new ResumeAwsScalingProcessTask(resolver: resolver, katoService: katoService)

    when:
      task.execute(stage)

    then:
      TargetServerGroup.isDynamicallyBound(stage) >> true
      TargetServerGroupResolver.fromPreviousStage(stage) >> tsg
  }

  def "should send asg name to kato when dynamic references configured"() {
    given:
      def tsg = tSG("targetAsg", ["Launch"])
      GroovySpy(TargetServerGroup, global: true, constructorArgs: [tsg])
      def resolver = GroovySpy(TargetServerGroupResolver, global: true)
      KatoService katoService = Mock(KatoService)

      def ctx = sD("targetAsg", ["Launch"])
      ctx.cloudProvider = "abc"
      def stage = new Stage<>(new Pipeline(), null, ctx)
      def task = new ResumeAwsScalingProcessTask(resolver: resolver, katoService: katoService)

    when:
      task.execute(stage)

    then:
      TargetServerGroup.isDynamicallyBound(stage) >> true
      TargetServerGroupResolver.fromPreviousStage(stage) >> tsg
      katoService.requestOperations("abc", { Map m -> m.resumeAsgProcessesDescription.asgName == "targetAsg" }) >> {
        return rx.Observable.from([new TaskId(id: "1")])
      }
      0 * katoService.requestOperations(_, _)
  }
}
