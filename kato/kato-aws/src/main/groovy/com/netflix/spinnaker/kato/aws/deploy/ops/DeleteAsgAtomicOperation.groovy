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

import com.amazonaws.services.autoscaling.model.DeleteAutoScalingGroupRequest
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.aws.deploy.description.DeleteAsgDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class DeleteAsgAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DELETE_ASG"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  final DeleteAsgDescription description

  DeleteAsgAtomicOperation(DeleteAsgDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    String descriptor = description.asgName ?: description.asgs.collect { it.toString() }
    task.updateStatus BASE_PHASE, "Initializing Delete ASG operation for $descriptor..."
    for (region in description.regions) {
      deleteAsg(description.asgName, region)
    }
    for (asg in description.asgs) {
      deleteAsg(asg.asgName, asg.region)
    }
    task.updateStatus BASE_PHASE, "Finished Delete ASG operation for $descriptor."
    null
  }

  private void deleteAsg(String asgName, String region) {
    def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, region, true)

    task.updateStatus BASE_PHASE, "Removing ASG -> ${asgName} in $region"
    def request = new DeleteAutoScalingGroupRequest().withAutoScalingGroupName(asgName)
        .withForceDelete(description.forceDelete)
    autoScaling.deleteAutoScalingGroup(request)
    task.updateStatus BASE_PHASE, "Deleted ASG -> ${asgName} in $region"
  }
}
