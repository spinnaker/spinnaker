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
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DeleteAutoScalingGroupRequest
import com.amazonaws.services.autoscaling.model.DeleteLaunchConfigurationRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult
import com.amazonaws.services.autoscaling.model.Instance
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.aws.TestCredential
import com.netflix.spinnaker.kato.aws.deploy.description.DestroyAsgDescription
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import spock.lang.Specification

class DestroyAsgAtomicOperationUnitSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def mockAutoScaling = Mock(AmazonAutoScaling)
  def mockElasticLoadBalancing = Mock(AmazonElasticLoadBalancing)
  def provider = Mock(AmazonClientProvider) {
    getAutoScaling(_, _) >> mockAutoScaling
    getAmazonElasticLoadBalancing(_, _) >> mockElasticLoadBalancing
  }

  void "should not fail delete when ASG does not exist"() {
    setup:
    def op = new DestroyAsgAtomicOperation(
            new DestroyAsgDescription(
                    asgName: "my-stack-v000",
                    regions: ["us-east-1"],
                    credentials: TestCredential.named('baz')))
    op.amazonClientProvider = provider

    when:
    op.operate([])

    then:
    1 * mockAutoScaling.describeAutoScalingGroups(_) >> new DescribeAutoScalingGroupsResult(autoScalingGroups: [])
    0 * mockAutoScaling._
    0 * mockElasticLoadBalancing._
  }

  void "should delete ASG and Launch Config"() {
    setup:
    def op = new DestroyAsgAtomicOperation(
            new DestroyAsgDescription(
                    asgName: "my-stack-v000",
                    regions: ["us-east-1"],
                    credentials: TestCredential.named('baz')))
    op.amazonClientProvider = provider

    when:
    op.operate([])

    then:
    1 * mockAutoScaling.describeAutoScalingGroups(_) >> new DescribeAutoScalingGroupsResult(autoScalingGroups: [
            new AutoScalingGroup(
                    instances: [new Instance(instanceId: "i-123456")],
                    launchConfigurationName: "launchConfig-v000"
            )
    ])
    1 * mockAutoScaling.deleteAutoScalingGroup(
            new DeleteAutoScalingGroupRequest(autoScalingGroupName: "my-stack-v000", forceDelete: true))
    1 * mockAutoScaling.deleteLaunchConfiguration(
            new DeleteLaunchConfigurationRequest(launchConfigurationName: "launchConfig-v000"))
    0 * mockAutoScaling._
    0 * mockElasticLoadBalancing._
  }

  void "should not delete launch config when not available"() {
    setup:
    def op = new DestroyAsgAtomicOperation(
        new DestroyAsgDescription(
            asgName: "my-stack-v000",
            regions: ["us-east-1"],
            credentials: TestCredential.named('baz')))
    op.amazonClientProvider = provider

    when:
    op.operate([])

    then:
    1 * mockAutoScaling.describeAutoScalingGroups(_) >> new DescribeAutoScalingGroupsResult(autoScalingGroups: [
        new AutoScalingGroup(
            instances: [new Instance(instanceId: "i-123456")]
        )
    ])
    1 * mockAutoScaling.deleteAutoScalingGroup(
        new DeleteAutoScalingGroupRequest(autoScalingGroupName: "my-stack-v000", forceDelete: true))
    0 * mockAutoScaling._
    0 * mockElasticLoadBalancing._
  }
}
