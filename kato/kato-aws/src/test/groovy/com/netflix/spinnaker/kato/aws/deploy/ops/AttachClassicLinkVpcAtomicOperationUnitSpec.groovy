package com.netflix.spinnaker.kato.aws.deploy.ops

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.AttachClassicLinkVpcRequest
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.kato.aws.TestCredential
import com.netflix.spinnaker.kato.aws.deploy.description.AttachClassicLinkVpcDescription
import spock.lang.Specification
import spock.lang.Subject

class AttachClassicLinkVpcAtomicOperationUnitSpec extends Specification {
  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def mockAmazonEC2 = Mock(AmazonEC2)
  def mockAmazonClientProvider = Mock(AmazonClientProvider) {
    getAmazonEC2(_, _, true) >> mockAmazonEC2
  }

  void "should attach VPC to instance"() {
    def description = new AttachClassicLinkVpcDescription(region: "us-west-1", instanceId: "i-123", vpcId: "vpc-123",
      securityGroupIds: ["sg-123", "sg-456"])
    description.credentials = TestCredential.named('baz')
    @Subject def operation = new AttachClassicLinkVpcAtomicOperation(description)
    operation.amazonClientProvider = mockAmazonClientProvider

    when:
    operation.operate([])

    then:
    with(mockAmazonEC2) {
      0 * _
      1 * attachClassicLinkVpc(new AttachClassicLinkVpcRequest(instanceId: "i-123", vpcId: "vpc-123",
        groups: ["sg-123", "sg-456"]))
    }
  }
}
