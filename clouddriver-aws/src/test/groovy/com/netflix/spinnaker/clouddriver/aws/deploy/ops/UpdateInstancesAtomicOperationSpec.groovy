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

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsResult
import com.amazonaws.services.autoscaling.model.Instance
import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.amazonaws.services.ec2.AmazonEC2
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpdateInstancesDescription
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.services.AsgService
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Specification
import spock.lang.Subject

class UpdateInstancesAtomicOperationSpec extends Specification {
  private static final String ACCOUNT = "test"
  private static final String REGION = "us-east-1"

  NetflixAmazonCredentials creds = Stub(NetflixAmazonCredentials) {
    getName() >> ACCOUNT
  }
  AsgService asgService = Mock(AsgService)
  AmazonAutoScaling amazonAutoScaling = Mock(AmazonAutoScaling)
  AmazonEC2 amazonEC2 = Mock(AmazonEC2)
  RegionScopedProviderFactory.RegionScopedProvider regionScopedProvider = Stub(RegionScopedProviderFactory.RegionScopedProvider) {
    getAsgService() >> asgService
    getAutoScaling() >> amazonAutoScaling
    getAmazonEC2() >> amazonEC2
  }


  RegionScopedProviderFactory regionScopedProviderFactory = Stub(RegionScopedProviderFactory) {
    forRegion(creds, REGION) >> regionScopedProvider
  }

  def description = new UpdateInstancesDescription(
    serverGroupName: "foo-v001",
    region: REGION,
    credentials: creds)

  @Subject
  op = new UpdateInstancesAtomicOperation(description)

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    op.regionScopedProviderFactory = regionScopedProviderFactory
  }

  void "should throw exception when ASG is not in VPC"() {
    when:
    op.operate([])

    then:
    thrown IllegalStateException
    1 * asgService.getAutoScalingGroup("foo-v001") >> new AutoScalingGroup(vPCZoneIdentifier: null)
    0 * _
  }

  void "should throw exception when ASG cannot be found"() {
    when:
    op.operate([])

    then:
    thrown IllegalStateException
    1 * asgService.getAutoScalingGroup("foo-v001") >> null
    0 * _
  }

  void "should update security groups"() {
    setup:
    description.securityGroups = ["sg-1"]

    when:
    op.operate([])

    then:
    1 * asgService.getAutoScalingGroup("foo-v001") >> new AutoScalingGroup(
      vPCZoneIdentifier: "vpc-1234",
      instances: [
        new Instance(instanceId: "i-1"),
        new Instance(instanceId: "i-2")
      ]
    )
    1 * amazonEC2.modifyInstanceAttribute({it.instanceId == "i-1" && it.groups == ["sg-1"]})
    1 * amazonEC2.modifyInstanceAttribute({it.instanceId == "i-2" && it.groups == ["sg-1"]})
    0 * _
  }

  void "should look up existing security groups and append them when flag is set"() {
    setup:
    description.securityGroupsAppendOnly = true
    description.securityGroups = ["sg-1"]

    when:
    op.operate([])

    then:
    1 * asgService.getAutoScalingGroup("foo-v001") >> new AutoScalingGroup(
      vPCZoneIdentifier: "vpc-1234",
      instances: [
            new Instance(instanceId: "i-1"),
            new Instance(instanceId: "i-2")
      ]
    )
    1 * amazonAutoScaling.describeLaunchConfigurations(_) >> new DescribeLaunchConfigurationsResult()
      .withLaunchConfigurations([new LaunchConfiguration(securityGroups: ["sg-2", "sg-3"])])
    1 * amazonEC2.modifyInstanceAttribute({it.instanceId == "i-1" && it.groups == ["sg-1", "sg-2", "sg-3"]})
    1 * amazonEC2.modifyInstanceAttribute({it.instanceId == "i-2" && it.groups == ["sg-1", "sg-2", "sg-3"]})
    0 * _
  }
}
