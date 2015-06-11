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
package com.netflix.spinnaker.kato.aws.model

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

  def 'should get subnet IDs'() {
    expect: subnets.subnetIds == ['subnet-e9b0a3a1', 'subnet-e9b0a3a2', 'subnet-e9b0a3a3', 'subnet-e9b0a3a4',
                                  'subnet-c1e8b2b1', 'subnet-c1e8b2b2', 'subnet-a3770585', 'subnet-a3770586', 'subnet-a3770587']
  }

  def 'should find subnet by ID'() {
    SubnetData expectedSubnet = new SubnetData(subnetId: 'subnet-e9b0a3a1', availabilityZone: 'us-east-1a',
      purpose: 'internal', target: SubnetTarget.EC2, vpcId: 'vpc-abcd')
    expect: expectedSubnet == subnets.findSubnetById('subnet-e9b0a3a1')
  }

  def 'should return null when subnet is not found by ID'() {
    expect: null == subnets.findSubnetById('subnet-acbdabcd')
  }

  def 'should fail when finding subnet by null'() {
    when: subnets.findSubnetById(null)
    then: thrown(NullPointerException)
  }

  def 'should find subnets by VPC ID'() {
    SubnetAnalyzer expectedSubnets = SubnetAnalyzer.from([
      new Subnet(subnetId: 'subnet-a3770585', availabilityZone: 'us-east-1a', vpcId: 'vpc-def'),
      new Subnet(subnetId: 'subnet-a3770586', availabilityZone: 'us-east-1b', vpcId: 'vpc-def'),
      new Subnet(subnetId: 'subnet-a3770587', availabilityZone: 'us-east-1c', vpcId: 'vpc-def'),
    ])

    expect: expectedSubnets == subnets.findSubnetsByVpc('vpc-def')
  }

  def 'should fail when finding subnets by null VPC ID'() {
    when: subnets.findSubnetsByVpc(null)
    then: thrown(NullPointerException)
  }

  def 'should get purpose from VPC zone identifier string when default VPC exists'() {
    expect: purpose == subnets.getPurposeFromVpcZoneIdentifier(vpcZoneIdentifier)

    where:
    vpcZoneIdentifier                 | purpose
    'subnet-e9b0a3a1,subnet-e9b0a3b1' | 'internal'
    'subnet-e9b0a3a4'                 | 'external'
    'subnet-a3770585,subnet-a3770586' | null
  }

  def 'should get purpose from VPC zone identifier string when default VPC does not exist'() {
    expect: purpose == subnetsForEc2Classic.getPurposeFromVpcZoneIdentifier(vpcZoneIdentifier)

    where:
    vpcZoneIdentifier                 | purpose
    'subnet-e9b0a3a1,subnet-e9b0a3b1' | 'internal'
    'subnet-e9b0a3a4'                 | 'external'
    'subnet-a3770585,subnet-a3770586' | null
  }

  def 'should get VPC ID for VPC zone identifier when default VPC exists'() {
    expect: vpcId == subnets.getVpcIdForVpcZoneIdentifier(vpcZoneIdentifier)

    where:
    vpcZoneIdentifier                 | vpcId
    'subnet-e9b0a3a1,subnet-e9b0a3b1' | 'vpc-abcd'
    'subnet-e9b0a3a4'                 | 'vpc-feed'
    'subnet-a3770585,subnet-a3770586' | 'vpc-def'
  }

  def 'should get VPC ID for VPC zone identifier when default VPC does not exist'() {
    expect: vpcId == subnetsForEc2Classic.getVpcIdForVpcZoneIdentifier(vpcZoneIdentifier)

    where:
    vpcZoneIdentifier                 | vpcId
    'subnet-e9b0a3a1,subnet-e9b0a3b1' | 'vpc-abcd'
    'subnet-e9b0a3a4'                 | 'vpc-feed'
    'subnet-a3770585,subnet-a3770586' | null
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

  def 'should fail to return multiple subnets with same purpose and zone'() {
    subnets = new SubnetAnalyzer([
      subnet('subnet-c1e8b2c1', 'us-east-1c', 'internal', SubnetTarget.EC2),
      subnet('subnet-c1e8b2c3', 'us-east-1c', 'internal', SubnetTarget.EC2),
    ])

    when:
    subnets.getSubnetIdsForZones(['us-east-1c'], 'internal', SubnetTarget.EC2)

    then:
    IllegalArgumentException e = thrown(IllegalArgumentException)
    e.message.startsWith 'Multiple entries with same key: '
  }

  def 'should not return subnets without purpose'() {
    subnets = SubnetAnalyzer.from([
      new Subnet(subnetId: 'subnet-e9b0a3a2', availabilityZone: 'us-east-1a'),
    ])
    expect: subnets.getSubnetIdsForZones(['us-east-1a'], '').isEmpty()
  }

  def 'should construct VPC Zone Identifier for zones'() {
    subnets = new SubnetAnalyzer([
      subnet('subnet-e9b0a3a1', 'us-east-1a', 'internal', SubnetTarget.EC2),
      subnet('subnet-e9b0a3a2', 'us-east-1a', 'external', SubnetTarget.EC2),
      subnet('subnet-e9b0a3a3', 'us-east-1a', 'internal', SubnetTarget.ELB),
      subnet('subnet-e9b0a3a4', 'us-east-1a', null, SubnetTarget.EC2),
      subnet('subnet-e9b0a3b1', 'us-east-1b', 'internal', SubnetTarget.EC2),
      subnet('subnet-e9b0a3b2', 'us-east-1b', 'external', SubnetTarget.EC2),
      subnet('subnet-e9b0a3c1', 'us-east-1c', 'internal', SubnetTarget.EC2),
      subnet('subnet-e9b0a3c2', 'us-east-1c', 'external', SubnetTarget.EC2),
    ])
    String existingVpcZoneIdentifier = 'subnet-e9b0a3a1,subnet-e9b0a3b1'
    List<String> zones = ['us-east-1a', 'us-east-1c']
    String expectedVpcZoneIdentifier = 'subnet-e9b0a3a1,subnet-e9b0a3c1'

    expect:
    expectedVpcZoneIdentifier == subnets.constructNewVpcZoneIdentifierForZones(existingVpcZoneIdentifier, zones)
  }

  def 'should construct null VPC Zone Identifier for subnets without purpose'() {
    subnets = new SubnetAnalyzer([
      subnet('subnet-e9b0a3a1', 'us-east-1a', 'internal', SubnetTarget.EC2),
      subnet('subnet-e9b0a3a2', 'us-east-1a', 'external', SubnetTarget.EC2),
      subnet('subnet-e9b0a3a3', 'us-east-1a', 'internal', SubnetTarget.ELB),
      subnet('subnet-e9b0a3a4', 'us-east-1a', null, SubnetTarget.EC2),
      subnet('subnet-e9b0a3b1', 'us-east-1b', 'internal', SubnetTarget.EC2),
      subnet('subnet-e9b0a3b2', 'us-east-1b', 'external', SubnetTarget.EC2),
      subnet('subnet-e9b0a3c1', 'us-east-1c', 'internal', SubnetTarget.EC2),
      subnet('subnet-e9b0a3c2', 'us-east-1c', 'external', SubnetTarget.EC2),
    ])
    String existingVpcZoneIdentifier = 'subnet-e9b0a3a4'
    List<String> zones = ['us-east-1a', 'us-east-1c']

    expect:
    null == subnets.constructNewVpcZoneIdentifierForZones(existingVpcZoneIdentifier, zones)
  }

  def 'should copy VPC Zone Identifier for null zones'() {
    String vpcZoneIdentifier = 'subnet-e9b0a3a1,subnet-e9b0a3a2'

    expect:
    vpcZoneIdentifier == subnets.constructNewVpcZoneIdentifierForZones(vpcZoneIdentifier, null)
  }

  def 'should copy VPC Zone Identifier for empty zones'() {
    String vpcZoneIdentifier = 'subnet-e9b0a3a1,subnet-e9b0a3a2'

    expect:
    vpcZoneIdentifier == subnets.constructNewVpcZoneIdentifierForZones(vpcZoneIdentifier, [])
  }

  def 'should construct null VPC Zone Identifier for null existing VPC Zone Identifier'() {
    List<String> zones = ['us-east-1a', 'us-east-1b']

    expect:
    null == subnets.constructNewVpcZoneIdentifierForZones(null, zones)
  }

  def 'should construct null VPC Zone Identifier for invalid existing VPC Zone Identifier'() {
    String existingVpcZoneIdentifier = 'subnet-deadbeef'
    List<String> zones = ['us-east-1a', 'us-east-1b']

    expect:
    null == subnets.constructNewVpcZoneIdentifierForZones(existingVpcZoneIdentifier, zones)
  }

  def 'should return only purposes without target when not specified'() {
    subnets = new SubnetAnalyzer([
      subnet('subnet-e9b0a3a1', 'us-east-1a', 'internal', SubnetTarget.EC2),
      subnet('subnet-e9b0a3a2', 'us-east-1a', 'external', null),
    ])
    expect: ['external'] as Set == subnets.getPurposesForZones(['us-east-1a'])
  }

  def 'should return union of purposes without a target in addition to targeted ones'() {
    subnets = new SubnetAnalyzer([
      subnet('subnet-e9b0a3a1', 'us-east-1a', 'internal', SubnetTarget.EC2),
      subnet('subnet-e9b0a3a2', 'us-east-1a', 'external', SubnetTarget.ELB),
      subnet('subnet-e9b0a3a3', 'us-east-1a', 'vulnerable', null),
    ])
    Set<String> expectedPurposes = ['external', 'vulnerable'] as Set

    expect:
    expectedPurposes == subnets.getPurposesForZones(['us-east-1a'], SubnetTarget.ELB)
  }

  def 'should return union of purposes for zones'() {
    subnets = new SubnetAnalyzer([
      subnet('subnet-e9b0a3a1', 'us-east-1a', 'internal', SubnetTarget.EC2),
      subnet('subnet-e9b0a3a4', 'us-east-1a', 'external', null),
      subnet('subnet-c1e8b2b1', 'us-east-1b', 'internal', SubnetTarget.EC2),
      subnet('subnet-c1e8b2b3', 'us-east-1b', 'vulnerable', SubnetTarget.EC2),
    ])
    Set<String> expectedPurposes = ['internal', 'external', 'vulnerable'] as Set

    expect:
    expectedPurposes == subnets.getPurposesForZones(['us-east-1a', 'us-east-1b'], SubnetTarget.EC2)
  }

  def 'should return no purposes for null zones'() {
    expect: subnets.getPurposesForZones(null, SubnetTarget.EC2).isEmpty()
  }

  def 'should return all purposes including zones without subnets'() {
    Set<String> expectedPurposes = ['internal', 'external'] as Set
    List<String> zones = ['us-east-1a', 'us-east-1b', 'us-east-1c']

    expect:
    expectedPurposes == subnets.getPurposesForZones(zones, SubnetTarget.EC2)
  }

  def 'should return zones grouped by purpose'() {
    subnets = new SubnetAnalyzer([
      subnet('subnet-e9b0a3a1', 'us-east-1a', 'internal', SubnetTarget.EC2),
      subnet('subnet-e9b0a3a2', 'us-east-1a', 'external', SubnetTarget.EC2),
      subnet('subnet-e9b0a3a3', 'us-east-1a', 'internal', SubnetTarget.ELB),
      subnet('subnet-e9b0a3a5', 'us-east-1b', 'external', SubnetTarget.EC2),
      subnet('subnet-e9b0a3c6', 'us-east-1c', 'internal', SubnetTarget.EC2),
      subnet('subnet-e9b0a3c7', 'us-east-1c', 'external', SubnetTarget.EC2),
      subnet('subnet-e9b0a3c8', 'us-east-1d', 'internal', SubnetTarget.ELB),
    ])

    expect:
    subnets.groupZonesByPurpose(['us-east-1a', 'us-east-1b', 'us-east-1c'], SubnetTarget.EC2) == [
      internal: ['us-east-1a', 'us-east-1c'],
      external: ['us-east-1a', 'us-east-1b', 'us-east-1c'],
      (null): ['us-east-1a', 'us-east-1b', 'us-east-1c'],
    ]
  }

  def 'should return zones grouped by purpose filtered by specified zones'() {
    subnets = new SubnetAnalyzer([
      subnet('subnet-e9b0a3a1', 'us-east-1a', 'internal', SubnetTarget.EC2),
      subnet('subnet-e9b0a3a2', 'us-east-1a', 'external', SubnetTarget.EC2),
      subnet('subnet-e9b0a3a3', 'us-east-1a', 'internal', SubnetTarget.ELB),
      subnet('subnet-e9b0a3a5', 'us-east-1b', 'external', SubnetTarget.EC2),
      subnet('subnet-e9b0a3c6', 'us-east-1c', 'internal', SubnetTarget.EC2),
      subnet('subnet-e9b0a3c7', 'us-east-1c', 'external', SubnetTarget.EC2),
      subnet('subnet-e9b0a3c8', 'us-east-1d', 'internal', SubnetTarget.ELB),
    ])

    expect:
    subnets.groupZonesByPurpose(['us-east-1a', 'us-east-1b'], SubnetTarget.EC2) == [
      internal: ['us-east-1a'],
      external: ['us-east-1a', 'us-east-1b'],
      (null): ['us-east-1a', 'us-east-1b'],
    ]
  }

  def 'should return zones grouped by purpose including extra specified zones'() {
    subnets = new SubnetAnalyzer([
      subnet('subnet-e9b0a3a1', 'us-east-1a', 'internal', SubnetTarget.EC2),
      subnet('subnet-e9b0a3a2', 'us-east-1a', 'external', SubnetTarget.EC2),
      subnet('subnet-e9b0a3a3', 'us-east-1a', 'internal', SubnetTarget.ELB),
      subnet('subnet-e9b0a3a5', 'us-east-1b', 'external', SubnetTarget.EC2),
      subnet('subnet-e9b0a3c6', 'us-east-1c', 'internal', SubnetTarget.EC2),
      subnet('subnet-e9b0a3c7', 'us-east-1c', 'external', SubnetTarget.EC2),
      subnet('subnet-e9b0a3c8', 'us-east-1d', 'internal', SubnetTarget.ELB),
    ])

    expect:
    subnets.groupZonesByPurpose(['us-east-1a', 'us-east-1b', 'us-east-1c', 'us-east-1e'], SubnetTarget.EC2) == [
      internal: ['us-east-1a', 'us-east-1c'],
      external: ['us-east-1a', 'us-east-1b', 'us-east-1c'],
      (null): ['us-east-1a', 'us-east-1b', 'us-east-1c', 'us-east-1e'],
    ]
  }

  def 'should return only zones without a target when not specified'() {
    subnets = new SubnetAnalyzer([
      subnet('subnet-e9b0a3a1', 'us-east-1a', 'internal', SubnetTarget.EC2),
      subnet('subnet-e9b0a3a2', 'us-east-1b', 'internal', null),
    ])

    expect:
    subnets.groupZonesByPurpose(['us-east-1a', 'us-east-1b', 'us-east-1c'], null) == [
      internal: ['us-east-1b'],
      (null): ['us-east-1a', 'us-east-1b', 'us-east-1c'], // target doesn't apply for non-VPC
    ]
  }

  def 'should return zones without a target in addition to targeted ones'() {
    subnets = new SubnetAnalyzer([
      subnet('subnet-e9b0a3a1', 'us-east-1a', 'internal', SubnetTarget.EC2),
      subnet('subnet-e9b0a3a2', 'us-east-1b', 'internal', SubnetTarget.ELB),
      subnet('subnet-e9b0a3a3', 'us-east-1c', 'internal', null),
    ])

    expect:
    subnets.groupZonesByPurpose(['us-east-1a', 'us-east-1b', 'us-east-1c'], SubnetTarget.ELB) == [
      internal: ['us-east-1b', 'us-east-1c'],
      (null): ['us-east-1a', 'us-east-1b', 'us-east-1c'], // target doesn't apply for non-VPC
    ]
  }

  def 'should return subnet for subnet ID'() {
    SubnetData expectedSubnet = subnet('subnet-e9b0a3a2', 'us-east-1a', 'external', SubnetTarget.EC2, 'vpc-feed')

    expect:
    subnets.coerceLoneOrNoneFromIds(['subnet-e9b0a3a2']) == expectedSubnet
  }

  def 'should return subnet for first subnet ID if there are multiple'() {
    SubnetData expectedSubnet = subnet('subnet-e9b0a3a2', 'us-east-1a', 'external', SubnetTarget.EC2, 'vpc-feed')

    expect: subnets.coerceLoneOrNoneFromIds(['subnet-e9b0a3a2', 'subnet-e9b0a3a1']) == expectedSubnet
  }

  def 'should return null if there is no subnet ID'() {
    expect: null == subnets.coerceLoneOrNoneFromIds(null)
  }

  def 'should return null if there is no subnet in cache with ID'() {
    expect: null == subnets.coerceLoneOrNoneFromIds(['subnet-deadbeef'])
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
