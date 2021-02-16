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

import com.netflix.spinnaker.config.AwsConfiguration
import com.netflix.spinnaker.clouddriver.aws.services.SecurityGroupService
import org.joda.time.LocalDateTime
import spock.lang.Specification

class AsgConfigHelperSpec extends Specification {
  def securityGroupServiceMock = Mock(SecurityGroupService)
  def deployDefaults = new AwsConfiguration.DeployDefaults()
  def asgConfig = new AutoScalingWorker.AsgConfiguration(
      application: "fooTest",
      stack: "stack")

  void "should return name correctly"() {
    when:
    def actualName = AsgConfigHelper.createName(baseName, suffix)

    then:
    actualName.contains(expectedName)

    where:
    baseName | suffix   || expectedName
    "base"   | "suffix" || "base-suffix"
    "base"   | null     || "base-${new LocalDateTime().toString("MMddYYYYHHmm")}"
    "base"   | ""       || "base-${new LocalDateTime().toString("MMddYYYYHHmm")}"
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
}
