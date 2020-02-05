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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.IpRange
import com.amazonaws.services.ec2.model.SecurityGroup
import com.amazonaws.services.ec2.model.UserIdGroupPair
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertSecurityGroupDescription.IpIngress
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertSecurityGroupDescription.SecurityGroupIngress
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupUpdater
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.model.securitygroups.IpRangeRule
import spock.lang.Specification
import spock.lang.Subject

class UpsertSecurityGroupAtomicOperationUnitSpec extends Specification {
  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def description = new UpsertSecurityGroupDescription(
    credentials: Stub(NetflixAmazonCredentials) {
      getName() >> "test"
    },
    vpcId: "vpc-123",
    name: "foo",
    description: "desc",
    securityGroupIngress: []
  )


  final securityGroupLookup = Mock(SecurityGroupLookupFactory.SecurityGroupLookup)

  final securityGroupLookupFactory = Stub(SecurityGroupLookupFactory) {
    getInstance(_) >> securityGroupLookup
  }

  @Subject
    op = new UpsertSecurityGroupAtomicOperation(description)


  def setup() {
    op.securityGroupLookupFactory = securityGroupLookupFactory
  }

  void "non-existent security group should be created"() {
    when:
    op.operate([])

    then:
    1 * securityGroupLookup.getSecurityGroupByName("test", "foo", "vpc-123") >> Optional.empty()

    then:
    1 * securityGroupLookup.createSecurityGroup(description) >> new SecurityGroupUpdater(null, null)
    0 * _
  }

  void "non-existent security group should be created with ingress"() {
    final createdSecurityGroup = Mock(SecurityGroupUpdater)
    description.securityGroupIngress = [
      new SecurityGroupIngress(name: "bar", startPort: 111, endPort: 112, ipProtocol: "tcp")
    ]

    when:
    op.operate([])

    then:
    1 * securityGroupLookup.getAccountIdForName("test") >> "accountId1"
    1 * securityGroupLookup.getSecurityGroupByName("test", "bar", "vpc-123") >> Optional.of(new SecurityGroupUpdater(
      new SecurityGroup(groupId: "id-bar"),
      null
    ))

    then:
    1 * securityGroupLookup.getSecurityGroupByName("test", "foo", "vpc-123") >> Optional.empty()

    then:
    1 * securityGroupLookup.createSecurityGroup(description) >> createdSecurityGroup
    1 * createdSecurityGroup.getSecurityGroup()

    then:
    1 * createdSecurityGroup.addIngress([
      new IpPermission(ipProtocol: "tcp", fromPort: 111, toPort: 112, userIdGroupPairs: [
        new UserIdGroupPair(userId: "accountId1", groupId: "id-bar")
      ])
    ])
    1 * createdSecurityGroup.updateTags(description)
  }

  void "non-existent security group that is found on create should be updated"() {
    final existingSecurityGroup = Mock(SecurityGroupUpdater)
    description.securityGroupIngress = [
      new SecurityGroupIngress(name: "bar", startPort: 111, endPort: 112, ipProtocol: "tcp"),
      new SecurityGroupIngress(name: "bar", startPort: 211, endPort: 212, ipProtocol: "tcp")
    ]

    when:
    op.operate([])

    then:
    2 * securityGroupLookup.getAccountIdForName("test") >> "accountId1"
    2 * securityGroupLookup.getSecurityGroupByName("test", "bar", "vpc-123") >> Optional.of(new SecurityGroupUpdater(
      new SecurityGroup(groupId: "id-bar"),
      null
    ))

    then:
    1 * securityGroupLookup.getSecurityGroupByName("test", "foo", "vpc-123") >> Optional.empty()

    then:
    1 * securityGroupLookup.createSecurityGroup(description) >> {
      throw new AmazonServiceException("").with {
        it.errorCode = "InvalidGroup.Duplicate"
        it
      }
    }
    1 * securityGroupLookup.getSecurityGroupByName("test", "foo", "vpc-123") >> Optional.of(existingSecurityGroup)
    1 * existingSecurityGroup.getSecurityGroup() >> new SecurityGroup(ipPermissions: [
            new IpPermission(fromPort: 211, toPort: 212, ipProtocol: "tcp", userIdGroupPairs: [
                    new UserIdGroupPair(userId: "accountId1", groupId: "id-bar")
            ])
    ])

    then:
    1 * existingSecurityGroup.addIngress([
      new IpPermission(ipProtocol: "tcp", fromPort: 111, toPort: 112, userIdGroupPairs: [
        new UserIdGroupPair(userId: "accountId1", groupId: "id-bar")
      ])
    ])
    1 * existingSecurityGroup.updateTags(description)
  }

  void "existing security group should be unchanged"() {
    when:
    op.operate([])

    then:
    1 * securityGroupLookup.getSecurityGroupByName("test", "foo", "vpc-123") >>
      Optional.of(new SecurityGroupUpdater(new SecurityGroup(groupId: "id-bar"), null))
    0 * _
  }

  void "existing security group should be updated with ingress by name"() {
    final existingSecurityGroup = Mock(SecurityGroupUpdater)
    description.securityGroupIngress = [
      new SecurityGroupIngress(name: "bar", startPort: 111, endPort: 112, ipProtocol: "tcp")
    ]

    when:
    op.operate([])

    then:
    1 * securityGroupLookup.getAccountIdForName("test") >> "accountId1"
    1 * securityGroupLookup.getSecurityGroupByName("test", "bar", "vpc-123") >>
      Optional.of(new SecurityGroupUpdater(new SecurityGroup(groupId: "id-bar"), null))

    then:
    1 * securityGroupLookup.getSecurityGroupByName("test", "foo", "vpc-123") >> Optional.of(existingSecurityGroup)
    1 * existingSecurityGroup.getSecurityGroup() >> new SecurityGroup(groupId: "123", ipPermissions: [])

    then:
    1 * existingSecurityGroup.addIngress([
      new IpPermission(ipProtocol: "tcp", fromPort: 111, toPort: 112, userIdGroupPairs: [
        new UserIdGroupPair(userId: "accountId1", groupId: "id-bar")
      ])
    ])
    1 * existingSecurityGroup.updateTags(description)
  }

  void "existing security group should be updated with ingress by id"() {
    final existingSecurityGroup = Mock(SecurityGroupUpdater)
    description.securityGroupIngress = [
      new SecurityGroupIngress(id: "id-bar", startPort: 111, endPort: 112, ipProtocol: "tcp")
    ]

    when:
    op.operate([])

    then:
    1 * securityGroupLookup.getAccountIdForName("test") >> "accountId1"

    then:
    1 * securityGroupLookup.getSecurityGroupByName("test", "foo", "vpc-123") >> Optional.of(existingSecurityGroup)
    1 * existingSecurityGroup.getSecurityGroup() >> new SecurityGroup(groupId: "123", ipPermissions: [])

    then:
    1 * existingSecurityGroup.addIngress([
      new IpPermission(ipProtocol: "tcp", fromPort: 111, toPort: 112, userIdGroupPairs: [
        new UserIdGroupPair(userId: "accountId1", groupId: "id-bar")
      ])
    ])
    1 * existingSecurityGroup.updateTags(description)
  }

  void "existing security group should be updated with ingress from another account"() {
    final existingSecurityGroup = Mock(SecurityGroupUpdater)
    description.securityGroupIngress = [
      new SecurityGroupIngress(accountName: "prod", name: "bar", startPort: 111, endPort: 112, ipProtocol: "tcp")
    ]

    when:
    op.operate([])

    then:
    1 * securityGroupLookup.getAccountIdForName("prod") >> "accountId2"
    1 * securityGroupLookup.getSecurityGroupByName("prod", "bar", "vpc-123") >>
      Optional.of(new SecurityGroupUpdater(new SecurityGroup(groupId: "id-bar"), null))

    then:
    1 * securityGroupLookup.getSecurityGroupByName("test", "foo", "vpc-123") >> Optional.of(existingSecurityGroup)
    1 * existingSecurityGroup.getSecurityGroup() >> new SecurityGroup(groupId: "123", ipPermissions: [])

    then:
    1 * existingSecurityGroup.addIngress([
      new IpPermission(ipProtocol: "tcp", fromPort: 111, toPort: 112, userIdGroupPairs: [
        new UserIdGroupPair(userId: "accountId2", groupId: "id-bar")
      ])
    ])
    1 * existingSecurityGroup.updateTags(description)
  }

  void "existing permissions should not be re-created when a security group is modified"() {
    final existingSecurityGroup = Mock(SecurityGroupUpdater)

    description.securityGroupIngress = [
      new SecurityGroupIngress(name: "bar", startPort: 111, endPort: 112, ipProtocol: "tcp"),
      new SecurityGroupIngress(name: "bar", startPort: 25, endPort: 25, ipProtocol: "tcp"),
      new SecurityGroupIngress(name: "bar", startPort: 80, endPort: 81, ipProtocol: "tcp")
    ]
    description.ipIngress = [
      new IpIngress(cidr: "10.0.0.1/32", startPort: 80, endPort: 81, ipProtocol: "tcp")
    ]

    when:
    op.operate([])

    then:
    3 * securityGroupLookup.getAccountIdForName("test") >> "accountId1"
    3 * securityGroupLookup.getSecurityGroupByName("test", "bar", "vpc-123") >> Optional.of(new SecurityGroupUpdater(
      new SecurityGroup(groupId: "id-bar"),
      null
    ))

    then:
    1 * securityGroupLookup.getSecurityGroupByName("test", "foo", "vpc-123") >> Optional.of(existingSecurityGroup)
    1 * existingSecurityGroup.getSecurityGroup() >> new SecurityGroup(groupName: "foo", groupId: "123", ipPermissions: [
        new IpPermission(fromPort: 80, toPort: 81,
          userIdGroupPairs: [
            new UserIdGroupPair(userId: "accountId1", groupId: "grp"),
            new UserIdGroupPair(userId: "accountId1", groupId: "id-bar")
          ],
          ipRanges: ["10.0.0.1/32"], ipProtocol: "tcp"
        ),
        new IpPermission(fromPort: 25, toPort: 25,
          userIdGroupPairs: [new UserIdGroupPair(userId: "accountId1", groupId: "id-bar")], ipProtocol: "tcp"),
      ])

    then:
    1 * existingSecurityGroup.addIngress([
      new IpPermission(ipProtocol: "tcp", fromPort: 111, toPort: 112, userIdGroupPairs: [
        new UserIdGroupPair(userId: "accountId1", groupId: "id-bar")
      ])
    ])
    1 * existingSecurityGroup.removeIngress([
      new IpPermission(ipProtocol: "tcp", fromPort: 80, toPort: 81, userIdGroupPairs: [
        new UserIdGroupPair(userId: "accountId1", groupId: "grp")
      ])
    ])
    2 * existingSecurityGroup.updateTags(description)
  }

  void "should only append security group ingress"() {
    final existingSecurityGroup = Mock(SecurityGroupUpdater)

    description.securityGroupIngress = [
      new SecurityGroupIngress(name: "bar", startPort: 111, endPort: 112, ipProtocol: "tcp"),
      new SecurityGroupIngress(name: "bar", startPort: 25, endPort: 25, ipProtocol: "tcp"),
      new SecurityGroupIngress(name: "bar", startPort: 80, endPort: 81, ipProtocol: "tcp")
    ]
    description.ingressAppendOnly = true

    when:
    op.operate([])

    then:
    3 * securityGroupLookup.getAccountIdForName("test") >> "accountId1"
    3 * securityGroupLookup.getSecurityGroupByName("test", "bar", "vpc-123") >> Optional.of(new SecurityGroupUpdater(
      new SecurityGroup(groupId: "id-bar"),
      null
    ))

    then:
    1 * securityGroupLookup.getSecurityGroupByName("test", "foo", "vpc-123") >> Optional.of(existingSecurityGroup)
    1 * existingSecurityGroup.getSecurityGroup() >> new SecurityGroup(groupName: "foo", groupId: "123", ipPermissions: [
      new IpPermission(fromPort: 80, toPort: 81,
        userIdGroupPairs: [
          new UserIdGroupPair(userId: "accountId1", groupId: "grp"),
          new UserIdGroupPair(userId: "accountId1", groupId: "id-bar")
        ],
        ipRanges: ["10.0.0.1/32"], ipProtocol: "tcp"
      ),
      new IpPermission(fromPort: 25, toPort: 25,
        userIdGroupPairs: [new UserIdGroupPair(userId: "accountId1", groupId: "id-bar")], ipProtocol: "tcp"),
    ])

    then:
    1 * existingSecurityGroup.addIngress([
      new IpPermission(ipProtocol: "tcp", fromPort: 111, toPort: 112, userIdGroupPairs: [
        new UserIdGroupPair(userId: "accountId1", groupId: "id-bar")
      ])
    ])
    1 * existingSecurityGroup.updateTags(description)
  }

  void "should fail for missing ingress security group in vpc"() {
    description.securityGroupIngress = [
      new SecurityGroupIngress(name: "bar", startPort: 111, endPort: 112, ipProtocol: "tcp")
    ]

    when:
    op.operate([])

    then:
    1 * securityGroupLookup.getAccountIdForName("test") >> "accountId1"
    1 * securityGroupLookup.getSecurityGroupByName("test", "bar", "vpc-123") >> Optional.empty()
    0 * _

    then:
    IllegalStateException ex = thrown()
    ex.message == "The following security groups do not exist: 'bar' in 'test' vpc-123"
  }

  void "should two-phase create self-referential security group in vpc"() {
    final createdSecurityGroup = Mock(SecurityGroupUpdater)
    description.securityGroupIngress = [
      new SecurityGroupIngress(name: "foo", accountName: "test", startPort: 111, endPort: 112, ipProtocol: "tcp")
    ]

    when:
    op.operate([])

    then:
    1 * securityGroupLookup.getAccountIdForName("test") >> "accountId1"
    2 * securityGroupLookup.getSecurityGroupByName("test", "foo", "vpc-123") >> Optional.empty()
    1 * securityGroupLookup.createSecurityGroup(description) >> createdSecurityGroup

    and:
    1 * securityGroupLookup.getAccountIdForName("test") >> "accountId1"
    1 * securityGroupLookup.getSecurityGroupByName("test", "foo", "vpc-123") >> Optional.of(createdSecurityGroup)
    2 * createdSecurityGroup.getSecurityGroup() >> new SecurityGroup(groupId: "id-foo")

    and:
    1 * createdSecurityGroup.addIngress([
      new IpPermission(ipProtocol: "tcp", fromPort: 111, toPort: 112, userIdGroupPairs: [
        new UserIdGroupPair(userId: "accountId1", groupId: "id-foo")
      ])
    ])
  }

  void "should add ingress by name for missing ingress security group in EC2 classic"() {
    final existingSecurityGroup = Mock(SecurityGroupUpdater)
    description.securityGroupIngress = [
      new SecurityGroupIngress(name: "bar", startPort: 111, endPort: 112, ipProtocol: "tcp")
    ]
    description.vpcId = null

    when:
    op.operate([])

    then:
    1 * securityGroupLookup.getAccountIdForName("test") >> "accountId1"
    1 * securityGroupLookup.getSecurityGroupByName("test", "bar", null) >> Optional.empty()

    then:
    1 * securityGroupLookup.getSecurityGroupByName("test", "foo", null) >> Optional.of(existingSecurityGroup)
    1 * existingSecurityGroup.getSecurityGroup() >> new SecurityGroup(groupName: "foo", groupId: "123", ipPermissions: [])

    then:
    1 * existingSecurityGroup.addIngress([
      new IpPermission(ipProtocol: "tcp", fromPort: 111, toPort: 112, userIdGroupPairs: [
        new UserIdGroupPair(userId: "accountId1", groupName: "bar")
      ])
    ])
    1 * existingSecurityGroup.updateTags(description)

  }

  void "should ignore name, peering status, vpcPeeringConnectionId when comparing ingress rules"() {
    final existingSecurityGroup = Mock(SecurityGroupUpdater)
    final ingressSecurityGroup = Mock(SecurityGroupUpdater)
    description.securityGroupIngress = [
      new SecurityGroupIngress(name: "bar", startPort: 111, endPort: 112, vpcId: "vpc-123", ipProtocol: "tcp", accountName: "test")
    ]
    description.vpcId = null

    when:
    op.operate([])

    then:
    1 * securityGroupLookup.getAccountIdForName("test") >> "accountId1"
    1 * securityGroupLookup.getSecurityGroupByName("test", "bar", "vpc-123") >> Optional.of(ingressSecurityGroup)

    then:
    1 * securityGroupLookup.getSecurityGroupByName("test", "foo", null) >> Optional.of(existingSecurityGroup)
    1 * ingressSecurityGroup.getSecurityGroup() >> new SecurityGroup(groupName: "bar", groupId: "124", vpcId: "vpc-123")
    1 * existingSecurityGroup.getSecurityGroup() >> new SecurityGroup(groupName: "foo", groupId: "123", vpcId: "vpc-123", ipPermissions: [
      new IpPermission(ipProtocol: "tcp", fromPort: 111, toPort: 112, userIdGroupPairs: [
        new UserIdGroupPair(userId: "accountId1", groupName: "baz", groupId: "124", vpcId: "vpc-123", vpcPeeringConnectionId: "pca", peeringStatus: "active")])
    ])
    0 * _

  }

  void "should update ingress and add by name for missing ingress security group in EC2 classic"() {
    final existingSecurityGroup = Mock(SecurityGroupUpdater)
    final ingressSecurityGroup = Mock(SecurityGroupUpdater)
    description.securityGroupIngress = [
      new SecurityGroupIngress(name: "bar", startPort: 111, endPort: 112, vpcId: "vpc-123", ipProtocol: "tcp", accountName: "test")
    ]
    description.vpcId = null
    description.ipIngress = [
      new IpIngress(cidr: "123.23.45.6/12", startPort: 7002, endPort: 7004, description: "foo", ipProtocol: "tcp")
    ]

    when:
    op.operate([])

    then:
    1 * securityGroupLookup.getAccountIdForName("test") >> "accountId1"
    1 * securityGroupLookup.getSecurityGroupByName("test", "bar", "vpc-123") >> Optional.of(ingressSecurityGroup)

    then:
    1 * securityGroupLookup.getSecurityGroupByName("test", "foo", null) >> Optional.of(existingSecurityGroup)
    1 * ingressSecurityGroup.getSecurityGroup() >> new SecurityGroup(groupName: "bar", groupId: "124", vpcId: "vpc-123")
    1 * existingSecurityGroup.getSecurityGroup() >> new SecurityGroup(groupName: "foo", groupId: "123", ipPermissions: [
      new IpPermission(ipProtocol: "tcp", fromPort: 7002, toPort: 7004, ipv4Ranges: [new IpRange(description: "foo", cidrIp:"123.23.45.6/12")])
    ])

    then:
    1 * existingSecurityGroup.addIngress(_)
    1 * existingSecurityGroup.updateIngress(_)
    2 * existingSecurityGroup.updateTags(description)
  }

  void "should only update ingress of existing ingress when description is not null in the input"() {
    final existingSecurityGroup = Mock(SecurityGroupUpdater)
    final ingressSecurityGroup = Mock(SecurityGroupUpdater)
    description.vpcId = null
    description.ipIngress = [
      new IpIngress(cidr: "123.23.45.6/12", startPort: 7002, endPort: 7004, description: "foo", ipProtocol: "tcp")
    ]

    when:
    op.operate([])

    then:
    1 * securityGroupLookup.getSecurityGroupByName("test", "foo", null) >> Optional.of(existingSecurityGroup)
    1 * existingSecurityGroup.getSecurityGroup() >> new SecurityGroup(groupName: "foo", groupId: "123", ipPermissions: [
      new IpPermission(ipProtocol: "tcp", fromPort: 7002, toPort: 7004, ipv4Ranges: [new IpRange(description: "foo", cidrIp:"123.23.45.6/12")])
    ])

    then:
    1 * existingSecurityGroup.updateIngress(_)
    1 * existingSecurityGroup.updateTags(description)
  }

  void "should update existing ingress with description when description is null for existing rule"() {
    final existingSecurityGroup = Mock(SecurityGroupUpdater)
    final ingressSecurityGroup = Mock(SecurityGroupUpdater)
    description.vpcId = null
    description.ipIngress = [
      new IpIngress(cidr: "123.23.45.6/12", startPort: 7002, endPort: 7004, description: "foo", ipProtocol: "tcp")
    ]

    when:
    op.operate([])

    then:
    1 * securityGroupLookup.getSecurityGroupByName("test", "foo", null) >> Optional.of(existingSecurityGroup)
    1 * existingSecurityGroup.getSecurityGroup() >> new SecurityGroup(groupName: "foo", groupId: "123", ipPermissions: [
      new IpPermission(ipProtocol: "tcp", fromPort: 7002, toPort: 7004, ipv4Ranges: [new IpRange(description: null, cidrIp:"123.23.45.6/12")])
    ])

    then:
    1 * existingSecurityGroup.updateIngress(_)
    1 * existingSecurityGroup.updateTags(description)
  }

  void "should not update ingress existing ingress with description for the same rule"() {
    final existingSecurityGroup = Mock(SecurityGroupUpdater)
    final ingressSecurityGroup = Mock(SecurityGroupUpdater)
    description.vpcId = null
    description.ipIngress = [
      new IpIngress(cidr: "123.23.45.6/12", startPort: 7002, endPort: 7004, description: null, ipProtocol: "tcp")
    ]

    when:
    op.operate([])

    then:
    1 * securityGroupLookup.getSecurityGroupByName("test", "foo", null) >> Optional.of(existingSecurityGroup)
    1 * existingSecurityGroup.getSecurityGroup() >> new SecurityGroup(groupName: "foo", groupId: "123", ipPermissions: [
      new IpPermission(ipProtocol: "tcp", fromPort: 7002, toPort: 7004, ipv4Ranges: [new IpRange(description: "foo", cidrIp:"123.23.45.6/12")])
    ])

    then:
    0 * existingSecurityGroup.updateIngress(_)
    0 * existingSecurityGroup.updateTags(description)
  }
  void "should add, remove and update security group ingress rules"() {
    final existingSecurityGroup = Mock(SecurityGroupUpdater)
    final ingressSecurityGroup = Mock(SecurityGroupUpdater)
    description.securityGroupIngress = [
      new SecurityGroupIngress(name: "bar", startPort: 111, endPort: 112, vpcId: "vpc-123", ipProtocol: "tcp", accountName: "test")
    ]
    description.vpcId = null
    description.ipIngress = [
      new IpIngress(cidr: "123.23.45.6/12", startPort: 7002, endPort: 7004, description: "foo", ipProtocol: "tcp")
    ]

    when:
    op.operate([])

    then:
    1 * securityGroupLookup.getAccountIdForName("test") >> "accountId1"
    1 * securityGroupLookup.getSecurityGroupByName("test", "bar", "vpc-123") >> Optional.of(ingressSecurityGroup)

    then:
    1 * securityGroupLookup.getSecurityGroupByName("test", "foo", null) >> Optional.of(existingSecurityGroup)
    1 * ingressSecurityGroup.getSecurityGroup() >> new SecurityGroup(groupName: "bar", groupId: "124", vpcId: "vpc-123")
    1 * existingSecurityGroup.getSecurityGroup() >> new SecurityGroup(groupName: "foo", groupId: "123", ipPermissions: [
      new IpPermission(ipProtocol: "tcp", fromPort: 7002, toPort: 7004, ipv4Ranges: [new IpRange(description: "foo", cidrIp:"123.23.45.6/12")]),
      new IpPermission(ipProtocol: "tcp", fromPort: 7002, toPort: 7004, ipv4Ranges: [new IpRange(description: "baz", cidrIp:"103.23.45.6/12")])
    ])

    then:
    1 * existingSecurityGroup.addIngress(_)
    1 * existingSecurityGroup.updateIngress(_)
    3 * existingSecurityGroup.updateTags(description)
    1 * existingSecurityGroup.removeIngress(_)
  }

}
