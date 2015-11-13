/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.kato.aws.deploy

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest
import com.netflix.spinnaker.kato.aws.deploy.userdata.UserDataProvider
import com.netflix.spinnaker.kato.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.kato.aws.services.AsgService
import com.netflix.spinnaker.kato.aws.services.SecurityGroupService
import com.netflix.spinnaker.kato.config.KatoAWSConfig
import spock.lang.Specification
import spock.lang.Subject

class DefaultLaunchConfigurationBuilderSpec extends Specification {
  def autoScaling = Mock(AmazonAutoScaling)
  def asgService = Mock(AsgService)
  def securityGroupService = Mock(SecurityGroupService)
  def userDataProvider = Stub(UserDataProvider) {
    getUserData(_ as String, _ as String, _ as String, _ as String) >> 'userdata'
  }

  @Subject
  DefaultLaunchConfigurationBuilder builder = new DefaultLaunchConfigurationBuilder(autoScaling, asgService,
    securityGroupService, [userDataProvider], new KatoAWSConfig.DeployDefaults())

  void "should lookup security groups when provided by name"() {
    when:
    builder.buildLaunchConfiguration(application, subnetType, settings)

    then:
    1 * securityGroupService.getSecurityGroupIdsWithSubnetPurpose(_, _) >> { groups, subnet ->
      assert subnet == subnetType
      groups.collectEntries { String group -> [(group): "sg-$group".toString()] }
    }
    1 * autoScaling.createLaunchConfiguration(_ as CreateLaunchConfigurationRequest) >> { CreateLaunchConfigurationRequest req ->
      assert req.securityGroups.toList().sort() == expectedGroups.toList().sort()
    }
    0 * _

    where:
    securityGroups          | expectedResolve | expectedGroups
    ['named', 'sg-feef000'] | ['named']       | ['sg-feef000', 'sg-named']

    application = 'foo'
    subnetType = null
    account = 'prod'
    settings = new LaunchConfigurationBuilder.LaunchConfigurationSettings(
      account: 'prod',
      region: 'us-east-1',
      baseName: 'fooapp-v001',
      suffix: '20150515',
      securityGroups: securityGroups)
  }

  void "should attach an existing application security group if no security groups provided"() {
    when:
    builder.buildLaunchConfiguration(application, subnetType, settings)

    then:
    1 * securityGroupService.getSecurityGroupForApplication(application, subnetType) >> application
    1 * autoScaling.createLaunchConfiguration(_ as CreateLaunchConfigurationRequest) >> { CreateLaunchConfigurationRequest req ->
      assert req.securityGroups.toList().sort() == expectedGroups.toList().sort()
    }
    0 * _

    where:
    application = 'foo'
    subnetType = null
    account = 'prod'
    securityGroups = []
    expectedGroups = [application]
    settings = new LaunchConfigurationBuilder.LaunchConfigurationSettings(
      account: 'prod',
      region: 'us-east-1',
      baseName: 'fooapp-v001',
      suffix: '20150515',
      securityGroups: securityGroups)
  }

  void "should create an application security group if none exists and no security groups provided"() {
    when:
    builder.buildLaunchConfiguration(application, subnetType, settings)

    then:
    1 * securityGroupService.getSecurityGroupForApplication(application, subnetType) >> null
    1 * securityGroupService.createSecurityGroup(application, subnetType) >> "sg-$application"
    1 * autoScaling.createLaunchConfiguration(_ as CreateLaunchConfigurationRequest) >> { CreateLaunchConfigurationRequest req ->
      assert req.securityGroups.toList().sort() == expectedGroups.toList().sort()
    }
    0 * _

    where:
    application = 'foo'
    subnetType = null
    account = 'prod'
    securityGroups = []
    expectedGroups = ["sg-$application"]
    settings = new LaunchConfigurationBuilder.LaunchConfigurationSettings(
      account: 'prod',
      region: 'us-east-1',
      baseName: 'fooapp-v001',
      suffix: '20150515',
      securityGroups: securityGroups)

  }

  void "should attach classic link security group if vpc is linked"() {
    when:
    builder.buildLaunchConfiguration(application, subnetType, settings)

    then:
    1 * autoScaling.createLaunchConfiguration(_ as CreateLaunchConfigurationRequest) >> { CreateLaunchConfigurationRequest req ->
      assert req.classicLinkVPCId == "vpc-123"
      assert req.classicLinkVPCSecurityGroups == ["sg-123", "sg-456"]
    }
    0 * _

    where:
    application = 'foo'
    subnetType = null
    account = 'prod'
    expectedGroups = [application]
    settings = new LaunchConfigurationBuilder.LaunchConfigurationSettings(
      account: 'prod',
      region: 'us-east-1',
      baseName: 'fooapp-v001',
      suffix: '20150515',
      securityGroups: ["sg-000"],
      classicLinkVpcId: "vpc-123",
      classicLinkVPCSecurityGroups: ["sg-123", "sg-456"])
  }

  void "should try to look up classic link security group if vpc is linked"() {
    when:
    builder.buildLaunchConfiguration(application, subnetType, settings)

    then:
    1 * autoScaling.createLaunchConfiguration(_ as CreateLaunchConfigurationRequest) >> { CreateLaunchConfigurationRequest req ->
      assert req.classicLinkVPCId == "vpc-123"
      assert req.classicLinkVPCSecurityGroups == []
    }
    0 * _

    where:
    application = 'foo'
    subnetType = null
    account = 'prod'
    expectedGroups = [application]
    settings = new LaunchConfigurationBuilder.LaunchConfigurationSettings(
      account: 'prod',
      region: 'us-east-1',
      baseName: 'fooapp-v001',
      suffix: '20150515',
      securityGroups: ["sg-000"],
      classicLinkVpcId: "vpc-123")
  }

  void "should look up and attach classic link security group if vpc is linked"() {
    builder = new DefaultLaunchConfigurationBuilder(autoScaling, asgService, securityGroupService, [userDataProvider],
      new KatoAWSConfig.DeployDefaults(classicLinkSecurityGroupName: "nf-classiclink"))

    when:
    builder.buildLaunchConfiguration(application, subnetType, settings)

    then:
    1 * securityGroupService.getSecurityGroupIds(["nf-classiclink"], "vpc-123") >> ["nf-classiclink": "sg-123"]
    1 * autoScaling.createLaunchConfiguration(_ as CreateLaunchConfigurationRequest) >> { CreateLaunchConfigurationRequest req ->
      assert req.classicLinkVPCId == "vpc-123"
      assert req.classicLinkVPCSecurityGroups == ["sg-123"]
    }
    0 * _

    where:
    application = 'foo'
    subnetType = null
    account = 'prod'
    expectedGroups = [application]
    settings = new LaunchConfigurationBuilder.LaunchConfigurationSettings(
      account: 'prod',
      region: 'us-east-1',
      baseName: 'fooapp-v001',
      suffix: '20150515',
      securityGroups: ["sg-000"],
      classicLinkVpcId: "vpc-123")
  }

  void "handles block device mappings"() {
    when:
    builder.buildLaunchConfiguration(application, subnetType, settings)

    then:
    1 * securityGroupService.getSecurityGroupForApplication(application, subnetType) >> "sg-$application"
    1 * autoScaling.createLaunchConfiguration(_ as CreateLaunchConfigurationRequest) >> { CreateLaunchConfigurationRequest req ->
      assert req.blockDeviceMappings.size() == 2
      req.blockDeviceMappings.first().with {
        assert deviceName == "/dev/sdb"
        assert virtualName == 'ephemeral1'
        assert ebs == null
      }
      req.blockDeviceMappings.last().with {
        assert deviceName == '/dev/sdc'
        assert virtualName == null
        assert ebs.snapshotId == 's-69'
        assert ebs.volumeType == 'io1'
        assert ebs.deleteOnTermination == false
        assert ebs.iops == 100
        assert ebs.volumeSize == 125
      }
    }
    0 * _

    where:
    application = 'foo'
    subnetType = null
    account = 'prod'
    securityGroups = []
    expectedGroups = ["sg-$application"]
    settings = new LaunchConfigurationBuilder.LaunchConfigurationSettings(
      account: 'prod',
      region: 'us-east-1',
      baseName: 'fooapp-v001',
      suffix: '20150515',
      blockDevices: [
        new AmazonBlockDevice(deviceName: '/dev/sdb', virtualName: 'ephemeral1'),
        new AmazonBlockDevice(deviceName: "/dev/sdc", size: 125, iops: 100, deleteOnTermination: false, volumeType: 'io1', snapshotId: 's-69')],
      securityGroups: securityGroups)

  }
}
