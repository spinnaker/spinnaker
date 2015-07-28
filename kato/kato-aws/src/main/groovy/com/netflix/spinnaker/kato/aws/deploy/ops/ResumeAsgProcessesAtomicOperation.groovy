/*
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.spinnaker.kato.aws.deploy.ops

import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.aws.deploy.description.ResumeAsgProcessesDescription
import com.netflix.spinnaker.kato.aws.model.AutoScalingProcessType
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import com.netflix.spinnaker.kato.aws.services.RegionScopedProviderFactory
import org.springframework.beans.factory.annotation.Autowired

class ResumeAsgProcessesAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "RESUME_ASG_PROCESSES"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final ResumeAsgProcessesDescription description

  ResumeAsgProcessesAtomicOperation(ResumeAsgProcessesDescription description) {
    this.description = description
  }

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  @Override
  Void operate(List priorOutputs) {
    String descriptor = description.asgName ?: description.asgs.collect { it.toString() }
    task.updateStatus BASE_PHASE, "Initializing Resume ASG Processes operation for $descriptor..."

    for (region in description.regions) {
      resumeProcess(description.asgName, region)
    }
    for (asg in description.asgs) {
      resumeProcess(asg.asgName, asg.region)
    }
    task.updateStatus BASE_PHASE, "Finished Resume ASG Processes operation for $descriptor."
    null
  }

  private void resumeProcess(String asgName, String region) {
    try {
      def processTypes = description.processes.collect { AutoScalingProcessType.parse(it) }
      def regionScopedProvider = regionScopedProviderFactory.forRegion(description.credentials, region)
      def asgService = regionScopedProvider.asgService
      def asg = asgService.getAutoScalingGroup(asgName)
      if (!asg) {
        task.updateStatus BASE_PHASE, "No ASG named '$asgName' found in $region."
        return
      }
      task.updateStatus BASE_PHASE, "Resuming ASG processes (${processTypes*.name().join(", ")}) for $asgName in $region..."
      asgService.resumeProcesses(asgName, processTypes)
    } catch (e) {
      task.updateStatus BASE_PHASE, "Could not resume processes for ASG '$asgName' in region $region! Reason: $e.message"
    }
  }

}
