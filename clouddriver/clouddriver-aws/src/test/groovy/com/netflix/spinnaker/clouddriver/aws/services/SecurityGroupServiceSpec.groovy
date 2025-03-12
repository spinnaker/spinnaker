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

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.SecurityGroup
import com.netflix.spinnaker.clouddriver.aws.model.SecurityGroupNotFoundException
import com.netflix.spinnaker.clouddriver.aws.model.SubnetAnalyzer
import org.hamcrest.Matcher
import spock.lang.Specification
import static org.hamcrest.Matchers.*

class SecurityGroupServiceSpec extends Specification {

  def securityGroupService = new SecurityGroupService(Mock(AmazonEC2), Mock(SubnetAnalyzer))

  void "should get Security Group for Application"() {
    when:
    def result = securityGroupService.getSecurityGroupForApplication("myApp")

    then:
    result == "sg-123"

    and:
    1 * securityGroupService.amazonEC2.describeSecurityGroups(matchRequest("myApp")) >> new DescribeSecurityGroupsResult(securityGroups: [
      new SecurityGroup(groupId: "sg-123", groupName: "myApp")
    ])
    0 * _
  }

  void "should not get Security Group for Application if it does not exist"() {
    when:
    String result = securityGroupService.getSecurityGroupForApplication("myApp")

    then:
    result == null

    and:
    1 * securityGroupService.amazonEC2.describeSecurityGroups(_) >> new DescribeSecurityGroupsResult(securityGroups: [])
    0 * _
  }

  void "should get Security Group IDs for Names"() {
    when:
    def result = securityGroupService.getSecurityGroupIds(["myApp", "yourApp"])

    then:
    result == [
      myApp: "sg-123",
      yourApp: "sg-456"
    ]

    and:
    1 * securityGroupService.amazonEC2.describeSecurityGroups(matchRequest("myApp", "yourApp")) >> new DescribeSecurityGroupsResult(securityGroups: [
      new SecurityGroup(groupId: "sg-123", groupName: "myApp"),
      new SecurityGroup(groupId: "sg-456", groupName: "yourApp")
    ])
    0 * _
  }

  void "should fail to get Security Group IDs for Names if it does not exist"() {
    when:
    securityGroupService.getSecurityGroupIds(["myApp", "yourApp"] as Set<String>)

    then:
    SecurityGroupNotFoundException e = thrown()

    and:
    1 * securityGroupService.amazonEC2.describeSecurityGroups(_) >> new DescribeSecurityGroupsResult(securityGroups: [
      new SecurityGroup(groupId: "sg-456", groupName: "yourApp")
    ])
    0 * _
  }

  void "should create Security Group"() {
    when:
    def result = securityGroupService.createSecurityGroup("myApp", "internal")

    then:
    result == "sg-123"

    and:
    1 * securityGroupService.subnetAnalyzer.getVpcIdForSubnetPurpose("internal") >> "vpc-123"
    1 * securityGroupService.amazonEC2.createSecurityGroup(new CreateSecurityGroupRequest(
      groupName: "myApp",
      description: "Security Group for myApp",
      vpcId: "vpc-123"
    )) >> new CreateSecurityGroupResult(groupId: "sg-123")
    0 * _
  }

  void "should not get vpc security groups for an ec2 application"() {
    when:
    def result = securityGroupService.getSecurityGroupForApplication("test", null)

    then:
    1 * securityGroupService.amazonEC2.describeSecurityGroups(_) >> new DescribeSecurityGroupsResult(securityGroups: [
      new SecurityGroup(groupId: "sg-123", groupName: "test", vpcId: "vpc1234"),
      new SecurityGroup(groupId: "sg-456", groupName: "test", vpcId: null)
    ])
    result == "sg-456"
  }

  void "should get vpc security groups for a vpc application"() {
    when:
    def result = securityGroupService.getSecurityGroupForApplication("test", "internal")

    then:
    1 * securityGroupService.subnetAnalyzer.getVpcIdForSubnetPurpose("internal") >> "vpc1234"
    1 * securityGroupService.amazonEC2.describeSecurityGroups(_) >> new DescribeSecurityGroupsResult(securityGroups: [
      new SecurityGroup(groupId: "sg-123", groupName: "test", vpcId: "vpc1234"),
      new SecurityGroup(groupId: "sg-456", groupName: "test", vpcId: null)
    ])
    result == "sg-123"
  }

  void "Resolve security group names from list of security group IDs and names"() {
    when:
    def result = securityGroupService.resolveSecurityGroupNamesByStrategy(["sg-123", "name"]) { List<String> ids ->
      securityGroupService.getSecurityGroupNamesFromIds(ids)
    }

    then:
    1 * securityGroupService.amazonEC2.describeSecurityGroups(_) >> new DescribeSecurityGroupsResult(securityGroups: [
      new SecurityGroup(groupId: "sg-123", groupName: "test", vpcId: "vpc1234")
    ])
    result == ["name", "test"]
  }

  void "Resolve security group IDs from list of security group names and IDs"() {
    when:
    def result = securityGroupService.resolveSecurityGroupIdsByStrategy(["test", "sg-456"]) { List<String> names ->
      securityGroupService.getSecurityGroupIds(names, "vpc1234")
    }

    then:
    1 * securityGroupService.amazonEC2.describeSecurityGroups(_) >> new DescribeSecurityGroupsResult(securityGroups: [
      new SecurityGroup(groupId: "sg-123", groupName: "test", vpcId: "vpc1234")
    ])
    result == ["sg-456", "sg-123"]
  }

  void "should resolve Security Group for Application given security group names and subnet purpose"() {
    def callCount = 0
    def sgNamesInCall = []
    def subnetPurposeInCall = ""
    securityGroupService.metaClass.getSecurityGroupIdsWithSubnetPurpose = { List<String> sgNames, String subnetPurpose ->
      sgNamesInCall.addAll(sgNames)
      subnetPurposeInCall = subnetPurpose
      callCount++
      ["myApp": "sg-123"]
    }

    when:
    def result = securityGroupService.resolveSecurityGroupIdsWithSubnetType(["myApp", "sg-456"], "internal")

    then:
    result == ["sg-456","sg-123"]
    callCount == 1
    sgNamesInCall == ["myApp"]
    subnetPurposeInCall == "internal"
    0 * _
  }

  void "should resolve Security Group for Application given security group names and vpc id"() {
    def callCount = 0
    def sgNamesInCall = []
    def vpcIdInCall = ""
    securityGroupService.metaClass.getSecurityGroupIds = { List<String> sgNames, String vpcId ->
      sgNamesInCall.addAll(sgNames)
      vpcIdInCall = vpcId
      callCount++
      ["myApp": "sg-123"]
    }

    when:
    def result = securityGroupService.resolveSecurityGroupIdsInVpc(["myApp", "sg-456"], "vpc-1234")

    then:
    result == ["sg-456","sg-123"]
    callCount == 1
    sgNamesInCall == ["myApp"]
    vpcIdInCall == "vpc-1234"
    0 * _
  }

  private Matcher<DescribeSecurityGroupsRequest> matchRequest(String... groupNames) {
    hasProperty("filters", contains(new Filter("group-name", groupNames.toList())))
  }
}
