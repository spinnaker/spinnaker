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

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.aws.deploy.description.ResizeAsgDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class ResizeAsgAtomicOperation implements AtomicOperation<Void> {
  private static final String PHASE = "RESIZE"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  final ResizeAsgDescription description

  ResizeAsgAtomicOperation(ResizeAsgDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus PHASE, "Initializing Resize Operation for ${description.asgName} in ${description.regions}."

    for (String region : description.regions) {
      task.updateStatus PHASE, "Beginning resize of ${description.asgName} in ${region}."
      def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, region)
      resize autoScaling
      task.updateStatus PHASE, "Completed resize of ${description.asgName} in ${region}."
    }

    task.updateStatus PHASE, "Done resizing ${description.asgName} in ${description.regions}."
    null
  }

  void resize(AmazonAutoScaling autoScaling) {
    def request = new UpdateAutoScalingGroupRequest().withAutoScalingGroupName(description.asgName)
      .withMinSize(description.capacity.min).withMaxSize(description.capacity.max)
      .withDesiredCapacity(description.capacity.desired)
    autoScaling.updateAutoScalingGroup request
  }
}
