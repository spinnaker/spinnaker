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

import com.amazonaws.services.ec2.model.RebootInstancesRequest
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.kato.aws.deploy.description.RebootInstancesDescription
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class RebootInstancesAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "REBOOT_INSTANCES"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final RebootInstancesDescription description

  RebootInstancesAtomicOperation(RebootInstancesDescription description) {
    this.description = description
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Reboot Instances Operation for ${description.instanceIds.join(", ")}..."
    def amazonEC2 = amazonClientProvider.getAmazonEC2(description.credentials, description.region, true)
    amazonEC2.rebootInstances(new RebootInstancesRequest(instanceIds: description.instanceIds))
    task.updateStatus BASE_PHASE, "Done rebooting instances (${description.instanceIds.join(", ")})."
    null
  }
}
