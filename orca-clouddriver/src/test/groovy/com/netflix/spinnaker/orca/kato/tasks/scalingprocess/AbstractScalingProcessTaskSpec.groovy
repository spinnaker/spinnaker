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

package com.netflix.spinnaker.orca.kato.tasks.scalingprocess

import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReference
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceSupport
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Unroll

class AbstractScalingProcessTaskSpec extends Specification {
  def katoService = Mock(KatoService) {
    _ * requestOperations(_) >> {
      return rx.Observable.from([new TaskId(id: "1")])
    }
  }

  @Unroll
  def "should only resume/suspend scaling processes that are not already in the target state"() {
    given:
    def stage = new Stage(Execution.newPipeline("orca"), null, context)
    def targetReferenceSupport = Mock(TargetReferenceSupport) {
      1 * getTargetAsgReferences(stage) >> {
        return targetReferences
      }
    }

    def task = isResume ?
      new ResumeScalingProcessTask(targetReferenceSupport: targetReferenceSupport, katoService: katoService) :
      new SuspendScalingProcessTask(targetReferenceSupport: targetReferenceSupport, katoService: katoService)

    when:
    def result = task.execute(stage)
    def outputs = result.context
    def globalOutputs = result.outputs

    then:
    outputs.processes == expectedScalingProcesses
    outputs.containsKey("kato.last.task.id") == !expectedScalingProcesses.isEmpty()
    globalOutputs["scalingProcesses.${context.asgName}" as String] == expectedScalingProcesses

    where:
    isResume | context                         | targetReferences              || expectedScalingProcesses
    true     | sD("targetAsg", ["Launch"])     | [tR("targetAsg", ["Launch"])] || ["Launch"]
    true     | sD("targetAsg", [], ["Launch"]) | [tR("targetAsg", ["Launch"])] || ["Launch"]
    true     | sD("targetAsg", ["Launch"])     | [tR("targetAsg", [])]         || []
    true     | sD("targetAsg", ["Launch"])     | [tR("targetAsg", [])]         || []
    false    | sD("targetAsg", ["Launch"])     | [tR("targetAsg", [])]         || ["Launch"]
    false    | sD("targetAsg", [], ["Launch"]) | [tR("targetAsg", [])]         || ["Launch"]
    false    | sD("targetAsg", ["Launch"])     | [tR("targetAsg", ["Launch"])] || []
  }

  private Map<String, Object> sD(String asgName,
                                 List<String> processes,
                                 List<String> globalProcesses = [],
                                 String region = "us-west-1") {
    return [
      asgName: asgName, processes: processes, regions: [region], ("scalingProcesses.${asgName}" as String): globalProcesses
    ]
  }

  private TargetReference tR(String name, List<String> suspendedProcesses, String region = "us-west-1") {
    return new TargetReference(region: region, asg: [
      name: name,
      asg : [
        suspendedProcesses: suspendedProcesses.collect {
          [processName: it]
        }
      ]
    ])
  }

  def "should get target reference dynamically when stage is dynamic"() {
    given:
    TargetReferenceSupport targetReferenceSupport = Mock()

    def stage = new Stage(Execution.newPipeline("orca"), null, sD("targetAsg", ["Launch"]))
    def task = new ResumeScalingProcessTask(targetReferenceSupport: targetReferenceSupport, katoService: katoService)

    when:
    task.execute(stage)

    then:
    1 * targetReferenceSupport.isDynamicallyBound(stage) >> true
    1 * targetReferenceSupport.getDynamicallyBoundTargetAsgReference(stage) >> tR("targetAsg", ["Launch"])
  }

  def "should send asg name to kato when dynamic references configured"() {
    given:
    TargetReferenceSupport targetReferenceSupport = Mock()
    KatoService katoService = Mock(KatoService)

    def stage = new Stage(Execution.newPipeline("orca"), null, sD("targetAsg", ["Launch"]))
    def task = new ResumeScalingProcessTask(targetReferenceSupport: targetReferenceSupport, katoService: katoService)

    when:
    task.execute(stage)

    then:
    1 * targetReferenceSupport.isDynamicallyBound(stage) >> true
    1 * targetReferenceSupport.getDynamicallyBoundTargetAsgReference(stage) >> tR("targetAsg", ["Launch"])
    1 * katoService.requestOperations({ Map m -> m.resumeAsgProcessesDescription.asgName == "targetAsg"}) >> {
      return rx.Observable.from([new TaskId(id: "1")])
    }
    0 * katoService.requestOperations(_)
  }
}
