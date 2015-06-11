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
package com.netflix.spinnaker.kato.aws.services
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsRequest
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsResult
import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.amazonaws.services.autoscaling.model.ResumeProcessesRequest
import com.amazonaws.services.autoscaling.model.SuspendProcessesRequest
import com.netflix.spinnaker.kato.aws.model.AutoScalingProcessType
import spock.lang.Specification
import spock.lang.Subject

class AsgServiceSpec extends Specification {

  def mockAmazonAutoScaling = Mock(AmazonAutoScaling)
  @Subject def asgService = new AsgService(mockAmazonAutoScaling)

  void 'should get auto scaling groups'() {
    when:
    def result = asgService.getAutoScalingGroups(["asg1", "asg2"])

    then:
    result == ["asg1", "asg2"].collect { new AutoScalingGroup(autoScalingGroupName: it)}

    and:
    1 * mockAmazonAutoScaling.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest(autoScalingGroupNames: ["asg1", "asg2"])) >>
      new DescribeAutoScalingGroupsResult(autoScalingGroups: ["asg1", "asg2"].collect { new AutoScalingGroup(autoScalingGroupName: it)})
    0 * _
  }

  void 'should get single auto scaling group'() {
    when:
    def result = asgService.getAutoScalingGroup("asg1")

    then:
    result == new AutoScalingGroup(autoScalingGroupName: "asg1")

    and:
    1 * mockAmazonAutoScaling.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest(autoScalingGroupNames: ["asg1"])) >>
      new DescribeAutoScalingGroupsResult(autoScalingGroups: [new AutoScalingGroup(autoScalingGroupName: "asg1")])
    0 * _
  }

  void 'should return null when auto scaling group does not exist'() {
    when:
    def result = asgService.getAutoScalingGroup("asg1")

    then:
    result == null

    and:
    1 * mockAmazonAutoScaling.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest(autoScalingGroupNames: ["asg1"])) >>
      new DescribeAutoScalingGroupsResult(autoScalingGroups: [])
    0 * _
  }

  void 'should get launch configurations'() {
    when:
    def result = asgService.getLaunchConfigurations(['lc1', 'lc2'])

    then:
    result == ["lc1", "lc2"].collect { new LaunchConfiguration(launchConfigurationName: it)}

    and:
    1 * mockAmazonAutoScaling.describeLaunchConfigurations(new DescribeLaunchConfigurationsRequest(launchConfigurationNames: ["lc1", "lc2"])) >>
      new DescribeLaunchConfigurationsResult(launchConfigurations:  ["lc1", "lc2"].collect { new LaunchConfiguration(launchConfigurationName: it)})
    0 * _
  }

  void 'should get single launch configuration'() {
    when:
    def result = asgService.getLaunchConfiguration('lc1')

    then:
    result == new LaunchConfiguration(launchConfigurationName: 'lc1')

    and:
    1 * mockAmazonAutoScaling.describeLaunchConfigurations(new DescribeLaunchConfigurationsRequest(launchConfigurationNames: ["lc1"])) >>
      new DescribeLaunchConfigurationsResult(launchConfigurations:  [new LaunchConfiguration(launchConfigurationName: 'lc1')])
    0 * _
  }

  void 'should return null when launch configuration does not exist'() {
    when:
    def result = asgService.getLaunchConfiguration('lc1')

    then:
    result == null

    and:
    1 * mockAmazonAutoScaling.describeLaunchConfigurations(new DescribeLaunchConfigurationsRequest(launchConfigurationNames: ["lc1"])) >>
      new DescribeLaunchConfigurationsResult()

    0 * _
  }

  void 'should suspend processes'() {
    when:
    asgService.suspendProcesses("asg1", AutoScalingProcessType.with { [Launch, Terminate] })

    then:
    1 * mockAmazonAutoScaling.suspendProcesses(new SuspendProcessesRequest(autoScalingGroupName: "asg1", scalingProcesses: ["Launch", "Terminate"]))
    0 * _
  }

  void 'should resume processes'() {
    when:
    asgService.resumeProcesses("asg1", AutoScalingProcessType.with { [Launch, Terminate] })

    then:
    1 * mockAmazonAutoScaling.resumeProcesses(new ResumeProcessesRequest(autoScalingGroupName: "asg1", scalingProcesses: ["Launch", "Terminate"]))
    0 * _
  }

  /*
    Removed the getAncestorAsg() method so this test may not be needed - sthadeshwar

  @Unroll
  void "should consider app, stack, and details in determining ancestor ASG"() {

    when:
    AutoScalingGroup ancestor = asgService.getAncestorAsg('app', stack, freeFormDetails)

    then:
    ancestor?.autoScalingGroupName == expected
    1 * mockAmazonAutoScaling.describeAutoScalingGroups(_) >> new DescribeAutoScalingGroupsResult(
      autoScalingGroups: [
        new AutoScalingGroup(autoScalingGroupName: 'app'),
        new AutoScalingGroup(autoScalingGroupName: 'app-v001'),
        new AutoScalingGroup(autoScalingGroupName: 'app-new'),
        new AutoScalingGroup(autoScalingGroupName: 'app-dev-v005'),
        new AutoScalingGroup(autoScalingGroupName: 'app-test-v010'),
        new AutoScalingGroup(autoScalingGroupName: 'app-dev-detail-v015'),
        new AutoScalingGroup(autoScalingGroupName: 'app-dev-detail2-v020'),
        new AutoScalingGroup(autoScalingGroupName: 'app-dev-detail3-v025'),
        new AutoScalingGroup(autoScalingGroupName: 'app-dev-c0usca-v000')
      ]
    )

    where:
    stack   | freeFormDetails  || expected
    null    | null             || 'app-v001'
    'new'   | null             || 'app-new'
    'dev'   | null             || 'app-dev-v005'
    'test'  | null             || 'app-test-v010'
    'dev'   | 'detail'         || 'app-dev-detail-v015'
    'dev'   | 'detail2'        || 'app-dev-detail2-v020'
    'dev'   | 'c0usca'         || 'app-dev-c0usca-v000'
    'none'  | null             || null
    'dev'   | 'none'           || null
  }
  */
}
