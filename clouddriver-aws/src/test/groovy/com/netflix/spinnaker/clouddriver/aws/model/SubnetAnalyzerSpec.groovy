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
package com.netflix.spinnaker.clouddriver.aws.model

import com.amazonaws.services.ec2.model.SecurityGroup
import com.amazonaws.services.ec2.model.Subnet
import com.amazonaws.services.ec2.model.Tag
import com.google.common.collect.ImmutableSet
import spock.lang.Specification

class SubnetAnalyzerSpec extends Specification {

  static SubnetData subnet(String id, String zone, String purpose, SubnetTarget target, String vpcId = 'vpc-1') {
    new SubnetData(subnetId: id, availabilityZone: zone, purpose: purpose, target: target, vpcId: vpcId)
  }

  static SecurityGroup securityGroup(String id, String vpcId = null) {
    new SecurityGroup(groupId: id, groupName: id, vpcId: vpcId)
  }

  SubnetAnalyzer subnets
  SubnetAnalyzer subnetsForEc2Classic

  void setup() {

    List<SubnetData> subnetDatasForEc2Classic = [
      subnet('subnet-e9b0a3a1', 'us-east-1a', 'internal', SubnetTarget.EC2, 'vpc-abcd'),
      subnet('subnet-e9b0a3a2', 'us-east-1a', 'external', SubnetTarget.EC2, 'vpc-feed'),
      subnet('subnet-e9b0a3a3', 'us-east-1a', 'internal', SubnetTarget.ELB, 'vpc-abcd'),
      subnet('subnet-e9b0a3a4', 'us-east-1a', 'external', SubnetTarget.ELB, 'vpc-feed'),
      subnet('subnet-c1e8b2b1', 'us-east-1b', 'internal', SubnetTarget.EC2, 'vpc-abcd'),
      subnet('subnet-c1e8b2b2', 'us-east-1b', 'external', SubnetTarget.EC2, 'vpc-feed'),
    ]
    subnetsForEc2Classic = new SubnetAnalyzer(subnetDatasForEc2Classic)

    List<SubnetData> subnetDatas = subnetDatasForEc2Classic + [
      subnet('subnet-a3770585', 'us-east-1a', null, null, 'vpc-def'),
      subnet('subnet-a3770586', 'us-east-1b', null, null, 'vpc-def'),
      subnet('subnet-a3770587', 'us-east-1c', null, null, 'vpc-def'),
    ]
    subnets = new SubnetAnalyzer(subnetDatas, 'vpc-def')
  }

  def 'should create Subnets from AWS objects'() {
    List<Subnet> awsSubnets = [
      new Subnet(subnetId: 'subnet-e9b0a3a1', state: 'available', vpcId: 'vpc-11112222',
        cidrBlock: '10.10.1.0/21', availableIpAddressCount: 42, availabilityZone: 'us-east-1a',
        tags: [new Tag(key: 'immutable_metadata', value: '{"purpose": "internal", "target": "ec2" }')]),
    ]
    Set<SubnetData> expectedSubnets = ImmutableSet.of(
      new SubnetData(subnetId: 'subnet-e9b0a3a1', state: 'available', vpcId: 'vpc-11112222',
        cidrBlock: '10.10.1.0/21', availableIpAddressCount: 42, availabilityZone: 'us-east-1a',
        purpose: 'internal', target: SubnetTarget.EC2)
    )
    expect: expectedSubnets == SubnetAnalyzer.from(awsSubnets).allSubnets
  }

  def 'should create subnet without target from AWS object with invalid target'() {
    List<Subnet> awsSubnets = [
      new Subnet(subnetId: 'subnet-e9b0a3a1', state: 'available', vpcId: 'vpc-11112222',
        cidrBlock: '10.10.1.0/21', availableIpAddressCount: 42, availabilityZone: 'us-east-1a',
        tags: [new Tag(key: 'immutable_metadata', value: '{"purpose": "internal", "target": "y2k" }')]),
    ]
    Set<SubnetData> expectedSubnets = ImmutableSet.of(
      new SubnetData(subnetId: 'subnet-e9b0a3a1', state: 'available', vpcId: 'vpc-11112222',
        cidrBlock: '10.10.1.0/21', availableIpAddressCount: 42, availabilityZone: 'us-east-1a',
        purpose: 'internal')
    )
    expect: expectedSubnets == SubnetAnalyzer.from(awsSubnets).allSubnets
  }

  def 'should return subnets for zones'() {
    List<String> zones = ['us-east-1a', 'us-east-1b']
    List<String> expectedSubnets = ['subnet-e9b0a3a1', 'subnet-c1e8b2b1']
    expect: expectedSubnets == subnets.getSubnetIdsForZones(zones, 'internal', SubnetTarget.EC2)
  }

  def 'should return no subnet for missing zone'() {
    List<String> zones = ['us-east-1a', 'us-east-1c']
    List<String> expectedSubnets = ['subnet-e9b0a3a1']
    expect: expectedSubnets == subnets.getSubnetIdsForZones(zones, 'internal', SubnetTarget.EC2)
  }

  def 'should return no subnets when given a missing zone'() {
    expect: subnets.getSubnetIdsForZones(['us-east-1z'], 'internal', SubnetTarget.EC2).isEmpty()
  }

  def 'should return no subnets when given no zones'() {
    expect: subnets.getSubnetIdsForZones([], 'internal', SubnetTarget.EC2).isEmpty()
  }

  def 'should return no subnets for null zone'() {
    expect: subnets.getSubnetIdsForZones(null, 'internal', SubnetTarget.EC2).isEmpty()
  }

  def 'should fail to return subnets for null purpose'() {
    when: subnets.getSubnetIdsForZones(['us-east-1a'], null, SubnetTarget.EC2)
    then: thrown(NullPointerException)
  }

  def 'should return subnet for target'() {
    expect: ['subnet-e9b0a3a1'] == subnets.getSubnetIdsForZones(['us-east-1a'], 'internal', SubnetTarget.EC2)
  }

  def 'should return subnet without target if none specified'() {
    subnets = new SubnetAnalyzer([
      subnet('subnet-e9b0a3a1', 'us-east-1a', 'internal', SubnetTarget.EC2),
      subnet('subnet-e9b0a3a2', 'us-east-1a', 'internal', null),
    ])
    expect: ['subnet-e9b0a3a2'] == subnets.getSubnetIdsForZones(['us-east-1a'], 'internal')
  }

  def 'should return subnets without a target in addition to targeted ones'() {
    subnets = new SubnetAnalyzer([
      subnet('subnet-e9b0a3a1', 'us-east-1a', 'internal', SubnetTarget.EC2),
      subnet('subnet-e9b0a3a2', 'us-east-1b', 'internal', SubnetTarget.ELB),
      subnet('subnet-e9b0a3a3', 'us-east-1c', 'internal', null),
    ])
    List<String> expectedSubnets = ['subnet-e9b0a3a2', 'subnet-e9b0a3a3']
    List<String> subnetNames = ['us-east-1a', 'us-east-1b', 'us-east-1c']

    expect:
    expectedSubnets == subnets.getSubnetIdsForZones(subnetNames, 'internal', SubnetTarget.ELB)
  }

  def 'should allow multiple subnets with same purpose and zone'() {
    subnets = new SubnetAnalyzer([
      subnet('subnet-c1e8b2c1', 'us-east-1c', 'internal', SubnetTarget.EC2),
      subnet('subnet-c1e8b2c3', 'us-east-1c', 'internal', SubnetTarget.EC2),
    ])

    when:
    def subnets = subnets.getSubnetIdsForZones(['us-east-1c'], 'internal', SubnetTarget.EC2)

    then:
    subnets.toSet() == ['subnet-c1e8b2c1', 'subnet-c1e8b2c3'].toSet()
  }

  def 'should allow limiting number of selected subnets when multiple subnets are present with the same purpose and zone'() {
    subnets = new SubnetAnalyzer(subnetIds.collect { subnet(it, zone, purpose, target)})

    when:
    def subnets = subnets.getSubnetIdsForZones([zone], purpose, target, subnetLimit)

    then:
    subnets.size() == Math.min(subnetIds.size(), subnetLimit)
    !subnetLimit || subnetIds.containsAll(subnets)

    where:
    subnetIds = ['subnet-c1e8b2c1', 'subnet-c1e8b2c3'].toSet()
    zone = 'us-east-1c'
    purpose = 'internal'
    target = SubnetTarget.EC2
    subnetLimit << [0, 1, 2, 3]
  }

  def 'should not return subnets without purpose'() {
    subnets = SubnetAnalyzer.from([
      new Subnet(subnetId: 'subnet-e9b0a3a2', availabilityZone: 'us-east-1a'),
    ])
    expect: subnets.getSubnetIdsForZones(['us-east-1a'], '').isEmpty()
  }

  def 'with default VPC, should get the VPC ID for a purpose or get the default VPC ID for empty or null purpose'() {
    expect: vpcId == subnets.getVpcIdForSubnetPurpose(purpose)

    where:
    vpcId      | purpose
    'vpc-feed' | 'external'
    'vpc-abcd' | 'internal'
    'vpc-def'  | ''
    'vpc-def'  | null
  }

  def 'in EC2 classic, should either get the VPC ID for a subnet purpose or null'() {
    expect: vpcId == subnetsForEc2Classic.getVpcIdForSubnetPurpose(purpose)

    where:
    vpcId      | purpose
    'vpc-feed' | 'external'
    'vpc-abcd' | 'internal'
    null       | ''
    null       | null
  }

  def 'should map purpose to VPC ID'() {
    subnets = new SubnetAnalyzer([
      subnet('subnet-e9b0a3a1', 'us-east-1a', 'internal', SubnetTarget.EC2),
      subnet('subnet-e9b0a3a2', 'us-east-1a', 'external', SubnetTarget.EC2),
      subnet('subnet-e9b0a3a3', 'us-east-1a', 'internal', SubnetTarget.ELB),
      subnet('subnet-e9b0a3a4', 'us-east-1a', 'external', SubnetTarget.ELB),
      subnet('subnet-e9b0a3a5', 'us-east-1b', 'external', SubnetTarget.EC2),
      subnet('subnet-e9b0a3c6', 'us-east-1c', 'internal', SubnetTarget.EC2),
      subnet('subnet-e9b0a3c7', 'us-east-1c', 'external', SubnetTarget.EC2),
      subnet('subnet-e9b0a3c8', 'us-east-1d', 'internal', SubnetTarget.ELB),
      subnet('subnet-e9b0a3a1', 'us-east-1a', 'alternateVpc', SubnetTarget.EC2, 'vpc-2'),
      subnet('subnet-e9b0a3a1', 'us-east-1a', null, null, 'vpc-2'),
    ])

    expect:
    subnets.mapPurposeToVpcId() == [
      internal: 'vpc-1',
      external: 'vpc-1',
      alternateVpc: 'vpc-2',
    ]
  }

  def 'should omit purposes in multiple VPCs'() {
    subnets = new SubnetAnalyzer([
      subnet('subnet-e9b0a3a1', 'us-east-1a', 'internal', SubnetTarget.EC2),
      subnet('subnet-e9b0a3a2', 'us-east-1a', 'internal', SubnetTarget.ELB),
      subnet('subnet-e9b0a3a3', 'us-east-1a', 'external', SubnetTarget.EC2),
      subnet('subnet-e9b0a3a4', 'us-east-1a', null, null),
      subnet('subnet-e9b0a3a5', 'us-east-1a', 'internal', SubnetTarget.EC2, 'vpc-2'),
      subnet('subnet-e9b0a3a6', 'us-east-1a', null, null, 'vpc-2'),
    ])

    expect:
    subnets.mapPurposeToVpcId() == [
      external: 'vpc-1',
    ]
  }
}
