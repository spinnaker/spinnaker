package com.netflix.spinnaker.kato.aws.deploy.ops

import com.amazonaws.services.ec2.model.AttachClassicLinkVpcRequest
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.kato.aws.deploy.description.AttachClassicLinkVpcDescription
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
