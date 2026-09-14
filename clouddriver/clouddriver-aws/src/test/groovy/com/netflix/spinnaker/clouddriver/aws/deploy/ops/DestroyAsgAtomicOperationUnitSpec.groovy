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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops
import software.amazon.awssdk.services.autoscaling.AutoScalingClient
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup
import software.amazon.awssdk.services.autoscaling.model.DeleteAutoScalingGroupRequest
import software.amazon.awssdk.services.autoscaling.model.DeleteLaunchConfigurationRequest
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsResponse
import software.amazon.awssdk.services.autoscaling.model.Instance
import software.amazon.awssdk.services.autoscaling.model.LaunchTemplate
import software.amazon.awssdk.services.autoscaling.model.LaunchTemplateSpecification
import software.amazon.awssdk.services.autoscaling.model.MixedInstancesPolicy
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.DeleteLaunchTemplateRequest
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DestroyAsgDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Specification

class DestroyAsgAtomicOperationUnitSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def mockAutoScaling = Mock(AutoScalingClient)
  def mockEC2 = Mock(Ec2Client)
  def provider = Mock(AmazonClientProvider) {
    getAutoScalingV2(_, _) >> mockAutoScaling
    getAmazonEC2V2(_, _) >> mockEC2
  }

  void "should not fail delete when ASG does not exist"() {
    setup:
    def op = new DestroyAsgAtomicOperation(
      new DestroyAsgDescription(
        asgs: [[
          serverGroupName: "my-stack-v000",
          region         : "us-east-1"
        ]],
        credentials: TestCredential.named('baz')))
    op.amazonClientProvider = provider

    when:
    op.operate([])

    then:
    1 * mockAutoScaling.describeAutoScalingGroups(_) >> DescribeAutoScalingGroupsResponse.builder().autoScalingGroups([]).build()
    0 * mockAutoScaling._
  }

  void "should delete ASG and Launch Config and terminate instances"() {
    setup:
    def op = new DestroyAsgAtomicOperation(
      new DestroyAsgDescription(
        asgs: [[
          serverGroupName: "my-stack-v000",
          region         : "us-east-1"
        ]],
        credentials: TestCredential.named('baz')))
    op.amazonClientProvider = provider

    when:
    op.operate([])

    then:
    1 * mockAutoScaling.describeAutoScalingGroups(_) >> DescribeAutoScalingGroupsResponse.builder().autoScalingGroups([
            AutoScalingGroup.builder().instances([Instance.builder().instanceId("i-123456").build()]).launchConfigurationName("launchConfig-v000").build()
    ]).build()
    1 * mockAutoScaling.deleteAutoScalingGroup(
            DeleteAutoScalingGroupRequest.builder().autoScalingGroupName("my-stack-v000").forceDelete(true).build())
    1 * mockAutoScaling.deleteLaunchConfiguration(
            DeleteLaunchConfigurationRequest.builder().launchConfigurationName("launchConfig-v000").build())
    1 * mockEC2.terminateInstances(TerminateInstancesRequest.builder().instanceIds(["i-123456"]).build())
    0 * mockAutoScaling._
  }

  void "should delete ASG and Launch Template and terminate instances"() {
    setup:
    def op = new DestroyAsgAtomicOperation(
      new DestroyAsgDescription(
        asgs: [[
                 serverGroupName: "my-stack-v000",
                 region         : "us-east-1"
               ]],
        credentials: TestCredential.named('baz')))
    op.amazonClientProvider = provider

    when:
    op.operate([])

    then:
    1 * mockAutoScaling.describeAutoScalingGroups(_) >> DescribeAutoScalingGroupsResponse.builder().autoScalingGroups([asg]).build()
    1 * mockAutoScaling.deleteAutoScalingGroup(
      DeleteAutoScalingGroupRequest.builder().autoScalingGroupName("my-stack-v000").forceDelete(true).build())
    1 * mockEC2.deleteLaunchTemplate(
      DeleteLaunchTemplateRequest.builder().launchTemplateId("lt-1").build())
    1 * mockEC2.terminateInstances(TerminateInstancesRequest.builder().instanceIds(["i-123456"]).build())
    0 * mockAutoScaling._

    where:
    asg << [
      AutoScalingGroup.builder().instances([Instance.builder().instanceId("i-123456").build()]).launchTemplate(LaunchTemplateSpecification.builder().launchTemplateId("lt-1").version("1").build()).build(),
      AutoScalingGroup.builder().instances([Instance.builder().instanceId("i-123456").build()]).mixedInstancesPolicy(MixedInstancesPolicy.builder().launchTemplate(LaunchTemplate.builder().launchTemplateSpecification(LaunchTemplateSpecification.builder().launchTemplateId("lt-1").version("1").build()).build()).build()).build()
    ]
  }

  void "should not delete launch config when not available"() {
    setup:
    def op = new DestroyAsgAtomicOperation(
      new DestroyAsgDescription(
        asgs: [[
          serverGroupName: "my-stack-v000",
          region         : "us-east-1"
        ]],
        credentials: TestCredential.named('baz')))
    op.amazonClientProvider = provider

    when:
    op.operate([])

    then:
    1 * mockAutoScaling.describeAutoScalingGroups(_) >> DescribeAutoScalingGroupsResponse.builder().autoScalingGroups([
        AutoScalingGroup.builder().instances([Instance.builder().instanceId("i-123456").build()]).build()
    ]).build()
    1 * mockAutoScaling.deleteAutoScalingGroup(
        DeleteAutoScalingGroupRequest.builder().autoScalingGroupName("my-stack-v000").forceDelete(true).build())
    1 * mockEC2.terminateInstances(TerminateInstancesRequest.builder().instanceIds(["i-123456"]).build())
    0 * mockAutoScaling._
  }

  void "should paginate instance terminations"() {
    setup:
    def op = new DestroyAsgAtomicOperation(
      new DestroyAsgDescription(
        asgs: [[
          serverGroupName: "my-stack-v000",
          region         : "us-east-1"
        ]],
        credentials: TestCredential.named('baz')))
    op.amazonClientProvider = provider
    def instances = (100..315).collect { Instance.builder().instanceId("i-123${it}").build() }
    Set<String> remaining = instances*.instanceId

    when:
    op.operate([])

    then:
    1 * mockAutoScaling.describeAutoScalingGroups(_) >> DescribeAutoScalingGroupsResponse.builder().autoScalingGroups([
      AutoScalingGroup.builder().instances(instances).build()
    ]).build()
    1 * mockAutoScaling.deleteAutoScalingGroup(
      DeleteAutoScalingGroupRequest.builder().autoScalingGroupName("my-stack-v000").forceDelete(true).build())
    3 * mockEC2.terminateInstances(_) >> { TerminateInstancesRequest req ->
      assert req.instanceIds.size() <= DestroyAsgAtomicOperation.MAX_SIMULTANEOUS_TERMINATIONS
      assert remaining.removeAll(req.instanceIds)
    }

    remaining.isEmpty()
    0 * mockAutoScaling._
  }
}
