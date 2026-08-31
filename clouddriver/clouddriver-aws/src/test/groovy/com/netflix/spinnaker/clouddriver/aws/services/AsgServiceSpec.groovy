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
package com.netflix.spinnaker.clouddriver.aws.services
import software.amazon.awssdk.services.autoscaling.AutoScalingClient
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsResponse
import software.amazon.awssdk.services.autoscaling.model.DescribeLaunchConfigurationsRequest
import software.amazon.awssdk.services.autoscaling.model.DescribeLaunchConfigurationsResponse
import software.amazon.awssdk.services.autoscaling.model.LaunchConfiguration
import software.amazon.awssdk.services.autoscaling.model.ResumeProcessesRequest
import software.amazon.awssdk.services.autoscaling.model.SuspendProcessesRequest
import com.netflix.spinnaker.clouddriver.aws.model.AutoScalingProcessType
import com.netflix.spinnaker.clouddriver.aws.services.AsgService
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class AsgServiceSpec extends Specification {

  def mockAmazonAutoScaling = Mock(AutoScalingClient)
  @Subject def asgService = new AsgService(mockAmazonAutoScaling)

  void 'should get auto scaling groups'() {
    when:
    def result = asgService.getAutoScalingGroups(["asg1", "asg2"])

    then:
    result == ["asg1", "asg2"].collect { AutoScalingGroup.builder().autoScalingGroupName(it).build()}

    and:
    1 * mockAmazonAutoScaling.describeAutoScalingGroups(DescribeAutoScalingGroupsRequest.builder().autoScalingGroupNames(["asg1", "asg2"]).build()) >>
      DescribeAutoScalingGroupsResponse.builder().autoScalingGroups(["asg1", "asg2"].collect { AutoScalingGroup.builder().autoScalingGroupName(it).build()}).build()
    0 * _
  }

  void 'should get single auto scaling group'() {
    when:
    def result = asgService.getAutoScalingGroup("asg1")

    then:
    result == AutoScalingGroup.builder().autoScalingGroupName("asg1").build()

    and:
    1 * mockAmazonAutoScaling.describeAutoScalingGroups(DescribeAutoScalingGroupsRequest.builder().autoScalingGroupNames(["asg1"]).build()) >>
      DescribeAutoScalingGroupsResponse.builder().autoScalingGroups([AutoScalingGroup.builder().autoScalingGroupName("asg1").build()]).build()
    0 * _
  }

  void 'should return null when auto scaling group does not exist'() {
    when:
    def result = asgService.getAutoScalingGroup("asg1")

    then:
    result == null

    and:
    1 * mockAmazonAutoScaling.describeAutoScalingGroups(DescribeAutoScalingGroupsRequest.builder().autoScalingGroupNames(["asg1"]).build()) >>
      DescribeAutoScalingGroupsResponse.builder().autoScalingGroups([]).build()
    0 * _
  }

  void 'should get launch configurations'() {
    when:
    def result = asgService.getLaunchConfigurations(['lc1', 'lc2'])

    then:
    result == ["lc1", "lc2"].collect { LaunchConfiguration.builder().launchConfigurationName(it).build()}

    and:
    1 * mockAmazonAutoScaling.describeLaunchConfigurations(DescribeLaunchConfigurationsRequest.builder().launchConfigurationNames(["lc1", "lc2"]).build()) >>
      DescribeLaunchConfigurationsResponse.builder().launchConfigurations(["lc1", "lc2"].collect { LaunchConfiguration.builder().launchConfigurationName(it).build()}).build()
    0 * _
  }

  void 'should get single launch configuration'() {
    when:
    def result = asgService.getLaunchConfiguration('lc1')

    then:
    result == LaunchConfiguration.builder().launchConfigurationName('lc1').build()

    and:
    1 * mockAmazonAutoScaling.describeLaunchConfigurations(DescribeLaunchConfigurationsRequest.builder().launchConfigurationNames(["lc1"]).build()) >>
      DescribeLaunchConfigurationsResponse.builder().launchConfigurations([LaunchConfiguration.builder().launchConfigurationName('lc1').build()]).build()
    0 * _
  }

  void 'should return null when launch configuration does not exist'() {
    when:
    def result = asgService.getLaunchConfiguration('lc1')

    then:
    result == null

    and:
    1 * mockAmazonAutoScaling.describeLaunchConfigurations(DescribeLaunchConfigurationsRequest.builder().launchConfigurationNames(["lc1"]).build()) >>
      DescribeLaunchConfigurationsResponse.builder().build()

    0 * _
  }

  void 'should suspend processes'() {
    when:
    asgService.suspendProcesses("asg1", AutoScalingProcessType.with { [Launch, Terminate] })

    then:
    1 * mockAmazonAutoScaling.suspendProcesses(SuspendProcessesRequest.builder().autoScalingGroupName("asg1").scalingProcesses(["Launch", "Terminate"]).build())
    0 * _
  }

  void 'should resume processes'() {
    when:
    asgService.resumeProcesses("asg1", AutoScalingProcessType.with { [Launch, Terminate] })

    then:
    1 * mockAmazonAutoScaling.resumeProcesses(ResumeProcessesRequest.builder().autoScalingGroupName("asg1").scalingProcesses(["Launch", "Terminate"]).build())
    0 * _
  }
}
