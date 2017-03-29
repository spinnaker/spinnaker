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

package com.netflix.spinnaker.orca.kato.tasks.scalingprocess

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReference
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceSupport
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired

@Deprecated
abstract class AbstractScalingProcessTask implements Task {
  @Autowired
  KatoService katoService

  @Autowired
  TargetReferenceSupport targetReferenceSupport

  abstract String getType()

  /**
   * @param targetReference Current ASG reference
   * @param processes Requested scaling processes to suspend/resume
   * @return Scaling processes that need modification based on current state of ASG (may be empty if scaling processes already exist)
   */
  abstract List<String> filterProcesses(TargetReference targetReference, List<String> processes)

  @Override
  TaskResult execute(Stage stage) {
    def targetReference
    if (targetReferenceSupport.isDynamicallyBound(stage)) {
      targetReference = targetReferenceSupport.getDynamicallyBoundTargetAsgReference(stage)
    } else {
      targetReference = targetReferenceSupport.getTargetAsgReferences(stage).find {
        it.asg.name == stage.context.asgName
      }
    }
    def asgName = targetReference.asg.name

    /*
     * If scaling processes have been explicitly supplied (context.processes), use them.
     *
     * Otherwise, use any scaling processes that were modified by a previous stage (context.scalingProcesses.ASG_NAME)
     */
    def processes = filterProcesses(
      targetReference,
      (stage.context.processes ?: stage.context["scalingProcesses.${asgName}" as String]) as List<String>
    )
    def stageContext = new HashMap(stage.context) + [
      processes: processes,
      asgName: asgName
    ]

    def stageData = stage.mapTo(StageData)
    stageData.asgName = asgName

    def stageOutputs = [
      "notification.type"   : getType().toLowerCase(),
      "deploy.server.groups": stageData.affectedServerGroupMap,
      "processes"           : stageContext.processes,
      "asgName"             : asgName
    ]
    if (stageContext.processes) {
      def taskId = katoService.requestOperations([[(getType()): stageContext]])
        .toBlocking().first()

      stageOutputs."kato.last.task.id" = taskId
    }

    return new TaskResult(ExecutionStatus.SUCCEEDED, stageOutputs, [
      ("scalingProcesses.${asgName}" as String): stageContext.processes
    ])
  }

  static class StageData {
    List<String> regions
    String asgName

    Map<String, List<String>> getAffectedServerGroupMap() {
      regions.collectEntries {
        [(it): [asgName]]
      }
    }
  }
}
