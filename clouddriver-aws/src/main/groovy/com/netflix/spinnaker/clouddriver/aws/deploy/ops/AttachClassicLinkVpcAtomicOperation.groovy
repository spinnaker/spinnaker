/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.amazonaws.services.ec2.model.AttachClassicLinkVpcRequest
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.aws.deploy.description.AttachClassicLinkVpcDescription
import org.springframework.beans.factory.annotation.Autowired

class AttachClassicLinkVpcAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "ATTACH_CLASSIC_LINK_VPC"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final AttachClassicLinkVpcDescription description

  AttachClassicLinkVpcAtomicOperation(AttachClassicLinkVpcDescription description) {
    this.description = description
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Override
  Void operate(List priorOutputs) {
    def msg = "attach classic link VPC (${description.vpcId}) to ${description.instanceId}."
    task.updateStatus BASE_PHASE, "Initializing $msg"
    def amazonEC2 = amazonClientProvider.getAmazonEC2(description.credentials, description.region, true)
    amazonEC2.attachClassicLinkVpc(new AttachClassicLinkVpcRequest(
      instanceId: description.instanceId,
      vpcId: description.vpcId,
      groups: description.securityGroupIds
    ))
    task.updateStatus BASE_PHASE, "Done executing $msg"
    null
  }
}
