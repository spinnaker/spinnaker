/*
 * Copyright 2021 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.asg

import software.amazon.awssdk.services.autoscaling.model.BlockDeviceMapping
import software.amazon.awssdk.services.autoscaling.model.Ebs
import software.amazon.awssdk.services.autoscaling.model.LaunchTemplateOverrides
import software.amazon.awssdk.services.ec2.model.LaunchTemplateBlockDeviceMapping
import software.amazon.awssdk.services.ec2.model.LaunchTemplateEbsBlockDevice
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.config.AwsConfiguration
import com.netflix.spinnaker.clouddriver.aws.services.SecurityGroupService
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class AsgConfigHelperSpec extends Specification {
  def securityGroupServiceMock = Mock(SecurityGroupService)
  def deployDefaults = new AwsConfiguration.DeployDefaults()
  def asgConfig = AutoScalingWorker.AsgConfiguration.builder()
      .application("fooTest")
      .stack("stack").build()

  void setupSpec() {
    // test code shouldn't assume it will run in less than one second, so let's control the clock
    // we use setupSpec rather than setup because setup is called after a where block
    AsgConfigHelper.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
  }

  void cleanupSpec() {
    AsgConfigHelper.clock = Clock.systemDefaultZone()
  }

  void "should return name correctly"() {
    when:
    AsgConfigHelper.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
    def actualName = AsgConfigHelper.createName(baseName, suffix)

    //ignore the end precision for tests.
    then:
    actualName.contains(expectedName.substring(0, expectedName.length() - 3))

    where:
    baseName | suffix   || expectedName
    "base"   | "suffix" || "base-suffix"
    "base"   | null     || "base-${AsgConfigHelper.createDefaultSuffix()}"
    "base"   | ""       || "base-${AsgConfigHelper.createDefaultSuffix()}"
  }

  void "should lookup security groups when provided by name"() {
    given:
    asgConfig.subnetType = null
    asgConfig.classicLinkVpcSecurityGroups = null
    asgConfig.securityGroups = securityGroupsInput

    and:
    deployDefaults.addAppGroupToServerGroup = false

    when:
    def actualAsgCfg = AsgConfigHelper.setAppSecurityGroups(asgConfig, securityGroupServiceMock, deployDefaults)

    then:
    1 * securityGroupServiceMock.resolveSecurityGroupIdsWithSubnetType(_, _) >> securityGroupsRet
    0 * _

    and:
    actualAsgCfg.securityGroups == expectedGroups

    where:
    securityGroupsInput        | securityGroupsRet        || expectedGroups
    ['foo']                    | ['sg-12345']             || ['sg-12345']
    ['bar']                    | ['sg-45678']             || ['sg-45678']
    ['foo', 'bar', 'sg-45678'] | ['sg-45678', 'sg-12345'] || ['sg-45678', 'sg-12345']
    ['bar', 'sg-45678']        | ['sg-45678']             || ['sg-45678']
    ['sg-12345']               | ['sg-12345']             || ['sg-12345']
  }

  void "should attach an existing application security group based on deploy defaults"() {
    given:
    asgConfig.application = "foo"
    asgConfig.subnetType = null
    asgConfig.classicLinkVpcSecurityGroups = null
    asgConfig.securityGroups = securityGroupsInput

    and:
    deployDefaults.addAppGroupToServerGroup = appGroupToServerGroup
    deployDefaults.maxSecurityGroups = maxSgs

    when:
    def actualAsgCfg = AsgConfigHelper.setAppSecurityGroups(asgConfig, securityGroupServiceMock, deployDefaults)

    then:
    1 * securityGroupServiceMock.resolveSecurityGroupIdsWithSubnetType(_, _) >> securityGroupsRet
    getNamesCount * securityGroupServiceMock.getSecurityGroupNamesFromIds(_) >> securityGroupNamesFromIds
    0 * _
    actualAsgCfg.securityGroups == expectedGroups

    where:
    securityGroupsInput | securityGroupsRet | appGroupToServerGroup | maxSgs | securityGroupNamesFromIds | getNamesCount || expectedGroups
    ['sg-12345']        | ['sg-12345']      | true                  | 5      | ['foo': 'sg-12345']       | 1             || ['sg-12345']
    ['foo']             | ['sg-12345']      | true                  | 5      | ['foo': 'sg-12345']       | 1             || ['sg-12345']
    ['sg-12345']        | ['sg-12345']      | true                  | 1      | _                         | 0             || ['sg-12345']
    ['sg-12345']        | ['sg-12345']      | false                 | 5      | _                         | 0             || ['sg-12345']
  }

  void "should attach application security group using subnet purpose if no security groups provided or no existing app security group found"() {
    given:
    asgConfig.application = "foo"
    asgConfig.subnetType = null
    asgConfig.classicLinkVpcSecurityGroups = null
    asgConfig.securityGroups = securityGroupsInput

    and:
    deployDefaults.addAppGroupToServerGroup = true
    deployDefaults.maxSecurityGroups = 5

    when:
    def actualAsgCfg = AsgConfigHelper.setAppSecurityGroups(asgConfig, securityGroupServiceMock, deployDefaults)

    then:
    1 * securityGroupServiceMock.resolveSecurityGroupIdsWithSubnetType(_, _) >> securityGroupsRet
    1 * securityGroupServiceMock.getSecurityGroupNamesFromIds(_) >> securityGroupNamesFromIds
    1 * securityGroupServiceMock.getSecurityGroupForApplication(_, _) >> 'sg-12345'
    actualAsgCfg.securityGroups == expectedGroups
    0 * _

    where:
    securityGroupsInput | securityGroupsRet | securityGroupNamesFromIds || expectedGroups
    null                | []                | [:]                       || ['sg-12345']
    []                  | []                | [:]                       || ['sg-12345']
    ['sg-45678']        | ['sg-45678']      | ['bar': 'sg-45678']       || ['sg-45678', 'sg-12345']
    ['bar']             | ['sg-45678']      | ['bar': 'sg-45678']       || ['sg-45678', 'sg-12345']
  }

  void "should create an application security group conditionally"() {
    given:
    asgConfig.application = "foo"
    asgConfig.subnetType = null
    asgConfig.classicLinkVpcSecurityGroups = null
    asgConfig.securityGroups = securityGroupsInput

    and:
    deployDefaults.addAppGroupToServerGroup = true
    deployDefaults.maxSecurityGroups = 5

    when:
    def actualAsgCfg = AsgConfigHelper.setAppSecurityGroups(asgConfig, securityGroupServiceMock, deployDefaults)

    then:
    1 * securityGroupServiceMock.resolveSecurityGroupIdsWithSubnetType(_, _) >> securityGroupsRet
    1 * securityGroupServiceMock.getSecurityGroupNamesFromIds(_) >> securityGroupNamesFromIds
    1 * securityGroupServiceMock.getSecurityGroupForApplication(_, _) >> null
    1 * securityGroupServiceMock.createSecurityGroup(_, _) >> 'sg-000-new'
    actualAsgCfg.securityGroups == expectedGroups
    0 * _

    where:
    securityGroupsInput | securityGroupsRet | securityGroupNamesFromIds || expectedGroups
    null                | []                | [:]                       || ['sg-000-new']
    []                  | []                | [:]                       || ['sg-000-new']
    ['sg-45678']        | ['sg-45678']      | ['bar': 'sg-45678']       || ['sg-45678', 'sg-000-new']
    ['bar']             | ['sg-45678']      | ['bar': 'sg-45678']       || ['sg-45678', 'sg-000-new']
  }

  void "throws exception if asked to attach classic link security group without providing classic link VPC id"() {
    given:
    asgConfig.subnetType = null
    asgConfig.classicLinkVpcSecurityGroups = ["sg-12345"]
    asgConfig.classicLinkVpcId = classicLinkVpcId

    when:
    AsgConfigHelper.setAppSecurityGroups(asgConfig, securityGroupServiceMock, deployDefaults)

    then:
    1 * securityGroupServiceMock.resolveSecurityGroupIdsWithSubnetType(_, _) >> ["sg-12345"]
    0 * _

    and:
    def ex = thrown(IllegalStateException)
    ex.message == "Can't provide classic link security groups without classiclink vpc Id"

    where:
    classicLinkVpcId << ['', null]
  }

  void "should attach classic link security group if vpc is linked"() {
    given:
    asgConfig.subnetType = null
    asgConfig.securityGroups = ["sg-00000"]
    asgConfig.classicLinkVpcId = "vpc-123"
    asgConfig.classicLinkVpcSecurityGroups = classicLinkVpcSecurityGroups

    when:
    def actualAsgCfg = AsgConfigHelper.setAppSecurityGroups(asgConfig, securityGroupServiceMock, deployDefaults)

    then:
    1 * securityGroupServiceMock.resolveSecurityGroupIdsWithSubnetType(_,_) >> ["sg-12345"]
    callCount * securityGroupServiceMock.resolveSecurityGroupIdsInVpc(_,_) >> classicLinkVpcSGsRet
    0 * _
    actualAsgCfg.classicLinkVpcSecurityGroups == expectedClassicLinkVpcSGs

    where:
    classicLinkVpcSecurityGroups | classicLinkVpcSGsRet |  callCount  || expectedClassicLinkVpcSGs
    null                         | _                    |  0          ||  null
    []                           | _                    |  0          ||  []
    ['sg-45678']                 | ['sg-45678']         |  1          ||  ['sg-45678']
    ['bar']                      | ['sg-45678']         |  1          ||  ['sg-45678']
    ["sg-12345"]                 | ['sg-45678']         |  1          ||  ['sg-45678']
  }

  @Unroll
  void "should convert launch configuration's block device mappings to AmazonBlockDevices"() {
    expect:
    AsgConfigHelper.transformBlockDeviceMapping([sourceDevice]) == [targetDevice]

    where:
    sourceDevice                                                                                                          || targetDevice
    BlockDeviceMapping.builder().deviceName("Device1").virtualName("virtualName").build()                                 || new AmazonBlockDevice("Device1", "virtualName", null, null, null, null, null, null, null)
    BlockDeviceMapping.builder().deviceName("Device1").ebs(Ebs.builder().iops(500).build()).build()                       || new AmazonBlockDevice("Device1", null, null, null, null, 500, null, null, null)
    BlockDeviceMapping.builder().deviceName("Device1").ebs(Ebs.builder().throughput(250).build()).build()                 || new AmazonBlockDevice("Device1", null, null, null, null, null, 250, null, null)
    BlockDeviceMapping.builder().deviceName("Device1").ebs(Ebs.builder().deleteOnTermination(true).build()).build()       || new AmazonBlockDevice("Device1", null, null, null, true, null, null, null, null)
    BlockDeviceMapping.builder().deviceName("Device1").ebs(Ebs.builder().volumeSize(1024).build()).build()                || new AmazonBlockDevice("Device1", null, 1024, null, null, null, null, null, null)
    BlockDeviceMapping.builder().deviceName("Device1").ebs(Ebs.builder().volumeType("volumeType").build()).build()        || new AmazonBlockDevice("Device1", null, null, "volumeType", null, null, null, null, null)
    BlockDeviceMapping.builder().deviceName("Device1").ebs(Ebs.builder().snapshotId("snapshotId").build()).build()        || new AmazonBlockDevice("Device1", null, null, null, null, null, null, "snapshotId", null)
    BlockDeviceMapping.builder().deviceName("Device1").ebs(Ebs.builder().build()).build()                                 || new AmazonBlockDevice("Device1", null, null, null, null, null, null, null, null)

    // if snapshot is not provided, we should set encryption correctly
    BlockDeviceMapping.builder().deviceName("Device1").ebs(Ebs.builder().encrypted(null).build()).build()                 || new AmazonBlockDevice("Device1", null, null, null, null, null, null, null, null)
    BlockDeviceMapping.builder().deviceName("Device1").ebs(Ebs.builder().encrypted(true).build()).build()                 || new AmazonBlockDevice("Device1", null, null, null, null, null, null, null, true)
    BlockDeviceMapping.builder().deviceName("Device1").ebs(Ebs.builder().encrypted(false).build()).build()                || new AmazonBlockDevice("Device1", null, null, null, null, null, null, null, false)

    // if snapshot is provided, then we should use the snapshot's encryption value
    BlockDeviceMapping.builder().deviceName("Device1").ebs(Ebs.builder().snapshotId("snap-123").encrypted(null).build()).build()  || new AmazonBlockDevice("Device1", null, null, null, null, null, null, "snap-123", null)
    BlockDeviceMapping.builder().deviceName("Device1").ebs(Ebs.builder().snapshotId("snap-123").encrypted(true).build()).build()  || new AmazonBlockDevice("Device1", null, null, null, null, null, null, "snap-123", null)
    BlockDeviceMapping.builder().deviceName("Device1").ebs(Ebs.builder().snapshotId("snap-123").encrypted(false).build()).build() || new AmazonBlockDevice("Device1", null, null, null, null, null, null, "snap-123", null)
  }

  @Unroll
  void "should convert launch template block device mappings to AmazonBlockDevices"() {
    expect:
    AsgConfigHelper.transformLaunchTemplateBlockDeviceMapping([sourceDevice]) == [targetDevice]

    where:
    sourceDevice                                                                                                                                                 || targetDevice
    LaunchTemplateBlockDeviceMapping.builder().deviceName("Device1").virtualName("virtualName").build()                                                          || new AmazonBlockDevice("Device1", "virtualName", null, null, null, null, null, null, null)
    LaunchTemplateBlockDeviceMapping.builder().deviceName("Device1").ebs(LaunchTemplateEbsBlockDevice.builder().iops(500).build()).build()                       || new AmazonBlockDevice("Device1", null, null, null, null, 500, null, null, null)
    LaunchTemplateBlockDeviceMapping.builder().deviceName("Device1").ebs(LaunchTemplateEbsBlockDevice.builder().throughput(250).build()).build()                 || new AmazonBlockDevice("Device1", null, null, null, null, null, 250, null, null)
    LaunchTemplateBlockDeviceMapping.builder().deviceName("Device1").ebs(LaunchTemplateEbsBlockDevice.builder().deleteOnTermination(true).build()).build()       || new AmazonBlockDevice("Device1", null, null, null, true, null, null, null, null)
    LaunchTemplateBlockDeviceMapping.builder().deviceName("Device1").ebs(LaunchTemplateEbsBlockDevice.builder().volumeSize(1024).build()).build()                || new AmazonBlockDevice("Device1", null, 1024, null, null, null, null, null, null)
    LaunchTemplateBlockDeviceMapping.builder().deviceName("Device1").ebs(LaunchTemplateEbsBlockDevice.builder().volumeType("volumeType").build()).build()        || new AmazonBlockDevice("Device1", null, null, "volumeType", null, null, null, null, null)
    LaunchTemplateBlockDeviceMapping.builder().deviceName("Device1").ebs(LaunchTemplateEbsBlockDevice.builder().snapshotId("snapshotId").build()).build()        || new AmazonBlockDevice("Device1", null, null, null, null, null, null, "snapshotId", null)
    LaunchTemplateBlockDeviceMapping.builder().deviceName("Device1").ebs(LaunchTemplateEbsBlockDevice.builder().build()).build()                                 || new AmazonBlockDevice("Device1", null, null, null, null, null, null, null, null)

    // if snapshot is not provided, we should set encryption correctly
    LaunchTemplateBlockDeviceMapping.builder().deviceName("Device1").ebs(LaunchTemplateEbsBlockDevice.builder().encrypted(null).build()).build()                 || new AmazonBlockDevice("Device1", null, null, null, null, null, null, null, null)
    LaunchTemplateBlockDeviceMapping.builder().deviceName("Device1").ebs(LaunchTemplateEbsBlockDevice.builder().encrypted(true).build()).build()                 || new AmazonBlockDevice("Device1", null, null, null, null, null, null, null, true)
    LaunchTemplateBlockDeviceMapping.builder().deviceName("Device1").ebs(LaunchTemplateEbsBlockDevice.builder().encrypted(false).build()).build()                || new AmazonBlockDevice("Device1", null, null, null, null, null, null, null, false)

    // if snapshot is provided, then we should use the snapshot's encryption value
    LaunchTemplateBlockDeviceMapping.builder().deviceName("Device1").ebs(LaunchTemplateEbsBlockDevice.builder().snapshotId("snap-123").encrypted(null).build()).build()  || new AmazonBlockDevice("Device1", null, null, null, null, null, null, "snap-123", null)
    LaunchTemplateBlockDeviceMapping.builder().deviceName("Device1").ebs(LaunchTemplateEbsBlockDevice.builder().snapshotId("snap-123").encrypted(true).build()).build()  || new AmazonBlockDevice("Device1", null, null, null, null, null, null, "snap-123", null)
    LaunchTemplateBlockDeviceMapping.builder().deviceName("Device1").ebs(LaunchTemplateEbsBlockDevice.builder().snapshotId("snap-123").encrypted(false).build()).build() || new AmazonBlockDevice("Device1", null, null, null, null, null, null, "snap-123", null)
  }

  @Unroll
  void "should transform description overrides to launch template overrides and vice versa with correct priority"() {

    // description overrides to launch template overrides
    expect:
    AsgConfigHelper.getLaunchTemplateOverrides(descOverrides) == expectedLtOverrides

    // launch template overrides to description overrides
    and:
    AsgConfigHelper.getDescriptionOverrides(expectedLtOverrides) == descOverridesAgain

    where:
    descOverrides << [
      // 1
      [new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: "c5.large", weightedCapacity: "1", priority: 1),
       new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: "c4.large", weightedCapacity: "1", priority: 2)],
      // 2
      [new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: "c5.large", weightedCapacity: "1", priority: 2),
       new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: "c5.xlarge", weightedCapacity: "2", priority: 1)],
      // 3
      [new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: "c5.large", weightedCapacity: "1", priority: 1),
       new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: "c4.large", weightedCapacity: "1", priority: 1)], // same priority
      // 4
      [new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: "c5.large", weightedCapacity: "1"),
       new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: "c5.xlarge", weightedCapacity: "2")], // no priority
      // 5
      [new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: "c5.2xlarge", weightedCapacity: "4"),
       new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: "c5.large", weightedCapacity: "1", priority: 1),
       new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: "c5.xlarge", weightedCapacity: "2", priority: 2)], // mixed, some overrides have priority
    ]

    expectedLtOverrides << [
      // 1
      [LaunchTemplateOverrides.builder().instanceType("c5.large").weightedCapacity("1").build(),
       LaunchTemplateOverrides.builder().instanceType("c4.large").weightedCapacity("1").build()],
      // 2
      [LaunchTemplateOverrides.builder().instanceType("c5.xlarge").weightedCapacity("2").build(),
       LaunchTemplateOverrides.builder().instanceType("c5.large").weightedCapacity("1").build()],
      // 3
      [LaunchTemplateOverrides.builder().instanceType("c5.large").weightedCapacity("1").build(),
       LaunchTemplateOverrides.builder().instanceType("c4.large").weightedCapacity("1").build()],
      // 4
      [LaunchTemplateOverrides.builder().instanceType("c5.large").weightedCapacity("1").build(),
       LaunchTemplateOverrides.builder().instanceType("c5.xlarge").weightedCapacity("2").build()],
      // 5
      [LaunchTemplateOverrides.builder().instanceType("c5.large").weightedCapacity("1").build(),
       LaunchTemplateOverrides.builder().instanceType("c5.xlarge").weightedCapacity("2").build(),
       LaunchTemplateOverrides.builder().instanceType("c5.2xlarge").weightedCapacity("4").build()],  // no priority = last priority
    ]

    descOverridesAgain << [
      // 1
      [new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: "c5.large", weightedCapacity: "1", priority: 1),
       new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: "c4.large", weightedCapacity: "1", priority: 2)],
      // 2
      [new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: "c5.xlarge", weightedCapacity: "2", priority: 1),
       new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: "c5.large", weightedCapacity: "1", priority: 2)],
      // 3
      [new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: "c5.large", weightedCapacity: "1", priority: 1),
       new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: "c4.large", weightedCapacity: "1", priority: 2)], // same priority became sequential
      // 4
      [new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: "c5.large", weightedCapacity: "1", priority: 1),
       new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: "c5.xlarge", weightedCapacity: "2", priority: 2)], // no priority originally, now sequential
      // 5
      [new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: "c5.large", weightedCapacity: "1", priority: 1),
       new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: "c5.xlarge", weightedCapacity: "2", priority: 2),
       new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: "c5.2xlarge", weightedCapacity: "4", priority: 3)],
    ]
  }
}
