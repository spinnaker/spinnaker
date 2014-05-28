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

package com.netflix.spinnaker.kato.deploy.aws.ops

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.*
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult
import com.amazonaws.services.ec2.model.SecurityGroup
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.amazoncomponents.security.AmazonCredentials
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.DeploymentResult
import com.netflix.spinnaker.kato.deploy.aws.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.kato.deploy.aws.handlers.BasicAmazonDeployHandler
import spock.lang.Specification

class CopyLastAsgAtomicOperationUnitSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "operation builds description based on ancestor asg"() {
    setup:
    def deployHandler = Mock(BasicAmazonDeployHandler)
    List<BasicAmazonDeployDescription> descriptions = []
    deployHandler.handle(_, _) >> { BasicAmazonDeployDescription desc, _ -> descriptions << desc; new DeploymentResult() }
    def description = new BasicAmazonDeployDescription(application: "asgard", stack: "stack")
    description.availabilityZones = ['us-west-1': []]
    description.credentials = new AmazonCredentials(Mock(AWSCredentials), "baz")
    def mockEC2 = Mock(AmazonEC2)
    def mockAutoScaling = Mock(AmazonAutoScaling)
    def mockProvider = Mock(AmazonClientProvider)
    mockProvider.getAmazonEC2(_, _) >> mockEC2
    mockProvider.getAutoScaling(_, _) >> mockAutoScaling
    def op = new CopyLastAsgAtomicOperation(description)
    op.amazonClientProvider = mockProvider
    op.basicAmazonDeployHandler = deployHandler

    when:
    op.operate([])

    then:
    1 * mockEC2.describeSecurityGroups(_) >> {
      def grp = Mock(SecurityGroup)
      grp.getGroupName() >> "foo"
      grp.getGroupId() >> "sg-12345"
      new DescribeSecurityGroupsResult().withSecurityGroups([grp])
    }
    1 * mockAutoScaling.describeLaunchConfigurations(_) >> { DescribeLaunchConfigurationsRequest request ->
      assert request.launchConfigurationNames == ['foo']
      def mockLaunch = Mock(LaunchConfiguration)
      mockLaunch.getLaunchConfigurationName() >> "foo"
      new DescribeLaunchConfigurationsResult().withLaunchConfigurations([mockLaunch])
    }
    1 * mockAutoScaling.describeAutoScalingGroups(_) >> {
      def mockAsg = Mock(AutoScalingGroup)
      mockAsg.getAutoScalingGroupName() >> "asgard-stack-v000"
      mockAsg.getMinSize() >> 1
      mockAsg.getMaxSize() >> 2
      mockAsg.getDesiredCapacity() >> 5
      mockAsg.getLaunchConfigurationName() >> "foo"
      new DescribeAutoScalingGroupsResult().withAutoScalingGroups([mockAsg])
    }
    descriptions
    descriptions.first().capacity.min == 1
    descriptions.first().capacity.max == 2
    descriptions.first().capacity.desired == 5
    descriptions.first().securityGroups == ['foo']
  }
}
