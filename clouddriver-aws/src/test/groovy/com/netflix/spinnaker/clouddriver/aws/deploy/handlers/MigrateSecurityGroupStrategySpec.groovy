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

package com.netflix.spinnaker.clouddriver.aws.deploy.handlers

import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.SecurityGroup
import com.amazonaws.services.ec2.model.UserIdGroupPair
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.MigrateSecurityGroupReference
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupLookup
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupUpdater
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupMigrator.SecurityGroupLocation
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class MigrateSecurityGroupStrategySpec extends Specification {

  @Subject
  MigrateSecurityGroupStrategy strategy

  @Shared
  NetflixAmazonCredentials testCredentials = TestCredential.named('test')

  @Shared
  NetflixAmazonCredentials prodCredentials = TestCredential.named('prod')

  SecurityGroupLookup sourceLookup = Mock(SecurityGroupLookup)

  SecurityGroupLookup targetLookup = Mock(SecurityGroupLookup)

  AmazonClientProvider amazonClientProvider = Mock(AmazonClientProvider)

  def setup() {
    strategy = new DefaultMigrateSecurityGroupStrategy(amazonClientProvider)
    sourceLookup.getCredentialsForId(testCredentials.accountId) >> testCredentials
    targetLookup.getCredentialsForId(testCredentials.accountId) >> testCredentials

    sourceLookup.getAccountNameForId(testCredentials.accountId) >> 'test'
    targetLookup.getAccountNameForId(testCredentials.accountId) >> 'test'

    targetLookup.accountIdExists(testCredentials.accountId) >> true

    sourceLookup.getCredentialsForId(prodCredentials.accountId) >> prodCredentials
    targetLookup.getCredentialsForId(prodCredentials.accountId) >> prodCredentials

    sourceLookup.getAccountNameForId(prodCredentials.accountId) >> 'prod'
    targetLookup.getAccountNameForId(prodCredentials.accountId) >> 'prod'

    targetLookup.accountIdExists(prodCredentials.accountId) >> true

  }

  void 'should create target group if createIfSourceMissing is true and source is not found'() {
    given:
    def source = new SecurityGroupLocation(credentials: testCredentials, region: 'us-east-1', name: 'groupA')
    def target = new SecurityGroupLocation(credentials: prodCredentials, region: 'us-west-1')

    when:
    def results = strategy.generateResults(source, target, sourceLookup, targetLookup, true, true)

    then:
    results.target.targetName == 'groupA'
    results.created[0] == results.target
    1 * sourceLookup.getSecurityGroupByName('test', 'groupA', null) >> null
    1 * targetLookup.getSecurityGroupByName('prod', 'groupA', null) >> null
    0 * _
  }

  void 'should throw exception if createIfSourceMissing is false and source is not found'() {
    given:
    def source = new SecurityGroupLocation(credentials: testCredentials, region: 'us-east-1', name: 'groupA')
    def target = new SecurityGroupLocation(credentials: prodCredentials, region: 'us-west-1')

    when:
    strategy.generateResults(source, target, sourceLookup, targetLookup, false, true)

    then:
    thrown IllegalStateException
    1 * sourceLookup.getSecurityGroupByName('test', 'groupA', null) >> null
    0 * _
  }

  void 'should generate target references, ignoring self'() {
    given:
    def source = new SecurityGroupLocation(credentials: testCredentials, region: 'us-east-1', name: 'group1')
    def target = new SecurityGroupLocation(credentials: prodCredentials, region: 'us-west-1')
    def sourceGroup = new SecurityGroup(groupName: 'group1', groupId: 'sg-1', ownerId: testCredentials.accountId)
    sourceGroup.ipPermissions = [
      new IpPermission().withUserIdGroupPairs(new UserIdGroupPair(userId: testCredentials.accountId, groupId: 'sg-2', groupName: 'group2'), new UserIdGroupPair(userId: prodCredentials.accountId, groupId: 'sg-3', groupName: 'group3')),
      new IpPermission().withUserIdGroupPairs(new UserIdGroupPair(userId: testCredentials.accountId, groupId: 'sg-1', groupName: 'group1'))
    ]
    def sourceUpdater = Stub(SecurityGroupUpdater) {
      getSecurityGroup() >> sourceGroup
    }

    when:
    def results = strategy.generateResults(source, target, sourceLookup, targetLookup, false, true)

    then:
    results.created.size() == 3
    results.created.targetName.sort() == ['group1', 'group2', 'group3']
    1 * sourceLookup.getSecurityGroupByName('test', 'group1', null) >> sourceUpdater
    1 * targetLookup.getSecurityGroupByName('prod', 'group1', null) >> null
    1 * targetLookup.getSecurityGroupByName('test', 'group2', null) >> null
    1 * targetLookup.getSecurityGroupByName('prod', 'group3', null) >> null
  }

  void 'should warn on references in unknown accounts'() {
    given:
    def source = new SecurityGroupLocation(credentials: testCredentials, region: 'us-east-1', name: 'group1')
    def target = new SecurityGroupLocation(credentials: prodCredentials, region: 'us-west-1')
    def mysteryAccount = TestCredential.named('test2')
    def sourceGroup = new SecurityGroup(groupName: 'group1', groupId: 'sg-1', ownerId: testCredentials.accountId)
    sourceGroup.ipPermissions = [
      new IpPermission().withUserIdGroupPairs(new UserIdGroupPair(userId: testCredentials.accountId, groupId: 'sg-2', groupName: 'group2'), new UserIdGroupPair(userId: mysteryAccount.accountId, groupId: 'sg-3', groupName: 'group3')),
      new IpPermission().withUserIdGroupPairs(new UserIdGroupPair(userId: testCredentials.accountId, groupId: 'sg-1', groupName: 'group1'))
    ]
    def sourceUpdater = Stub(SecurityGroupUpdater) {
      getSecurityGroup() >> sourceGroup
    }

    when:
    def results = strategy.generateResults(source, target, sourceLookup, targetLookup, false, true)

    then:
    results.created.size() == 2
    results.created.targetName.sort() == ['group1', 'group2']
    results.warnings.sourceId == ['sg-3']
    results.warnings.accountId == [mysteryAccount.accountId]
    results.warnings.explanation == ["Spinnaker does not manage the account $mysteryAccount.accountId".toString()]
    sourceLookup.getCredentialsForId(mysteryAccount.accountId) >> null
    targetLookup.accountIdExists(mysteryAccount.accountId) >> false
    targetLookup.getAccountNameForId(mysteryAccount.accountId) >> mysteryAccount.accountId
    1 * sourceLookup.getSecurityGroupByName('test', 'group1', null) >> sourceUpdater
    1 * targetLookup.getSecurityGroupByName('prod', 'group1', null) >> null
    1 * targetLookup.getSecurityGroupByName('test', 'group2', null) >> null
  }

  void 'should skip amazon-elb group without warning'() {
    given:
    def source = new SecurityGroupLocation(credentials: testCredentials, region: 'us-east-1', name: 'group1')
    def target = new SecurityGroupLocation(credentials: testCredentials, region: 'us-west-1')
    def sourceGroup = new SecurityGroup(groupName: 'group1', groupId: 'sg-1', ownerId: testCredentials.accountId)
    sourceGroup.ipPermissions = [
      new IpPermission().withUserIdGroupPairs(
        new UserIdGroupPair(userId: 'amazon-elb', groupId: 'sg-2', groupName: 'do-not-copy'),
        new UserIdGroupPair(userId: testCredentials.accountId, groupId: 'sg-3', groupName: 'group3')),
    ]
    def sourceUpdater = Stub(SecurityGroupUpdater) {
      getSecurityGroup() >> sourceGroup
    }

    when:
    def results = strategy.generateResults(source, target, sourceLookup, targetLookup, false, true)

    then:
    results.created.size() == 2
    results.created.targetName.sort() == ['group1', 'group3']
    results.skipped.targetName == ['do-not-copy']
    sourceLookup.getCredentialsForId('amazon-elb') >> null
    targetLookup.accountIdExists('amazon-elb') >> false
    1 * sourceLookup.getSecurityGroupByName('test', 'group1', null) >> sourceUpdater
    1 * targetLookup.getSecurityGroupByName('test', 'group1', null) >> null
    1 * targetLookup.getSecurityGroupByName('test', 'group3', null) >> null
  }

  void 'should include target reference'() {
    given:
    def source = new SecurityGroupLocation(credentials: testCredentials, region: 'us-east-1', name: 'group1')
    def target = new SecurityGroupLocation(credentials: prodCredentials, region: 'us-west-1')
    def sourceGroup = new SecurityGroup(groupName: 'group1', groupId: 'sg-1', ownerId: testCredentials.accountId)
    sourceGroup.ipPermissions = []
    def sourceUpdater = Stub(SecurityGroupUpdater) {
      getSecurityGroup() >> sourceGroup
    }

    when:
    def results = strategy.generateResults(source, target, sourceLookup, targetLookup, false, true)

    then:
    results.target.targetName == 'group1'
    results.created.targetName == ['group1']
    !results.targetExists()
    targetLookup.accountIdExists(_) >> true
    1 * sourceLookup.getSecurityGroupByName('test', 'group1', null) >> sourceUpdater
    1 * targetLookup.getSecurityGroupByName('prod', 'group1', null) >> null
  }

  void 'should flag target as existing if it exists in target location'() {
    given:
    def source = new SecurityGroupLocation(credentials: testCredentials, region: 'us-east-1', name: 'group1')
    def target = new SecurityGroupLocation(credentials: prodCredentials, region: 'us-west-1')
    def sourceGroup = new SecurityGroup(groupName: 'group1', groupId: 'sg-1', ownerId: testCredentials.accountId)
    sourceGroup.ipPermissions = []
    def sourceUpdater = Stub(SecurityGroupUpdater) { getSecurityGroup() >> sourceGroup }
    def targetUpdater = Stub(SecurityGroupUpdater) { getSecurityGroup() >> sourceGroup }

    when:
    def results = strategy.generateResults(source, target, sourceLookup, targetLookup, false, true)

    then:
    results.target.targetName == 'group1'
    results.reused.targetName == ['group1']
    results.created.empty
    results.targetExists()
    1 * sourceLookup.getSecurityGroupByName('test', 'group1', null) >> sourceUpdater
    1 * targetLookup.getSecurityGroupByName('prod', 'group1', null) >> targetUpdater
  }

  void 'should halt if any errors are found'() {
    given:
    strategy = new ErrorfulMigrationStrategy(amazonClientProvider)
    def source = new SecurityGroupLocation(credentials: testCredentials, region: 'us-east-1', name: 'group1')
    def target = new SecurityGroupLocation(credentials: prodCredentials, region: 'us-west-1')
    def sourceGroup = new SecurityGroup(groupName: 'group1', groupId: 'sg-1', ownerId: testCredentials.accountId)
    sourceGroup.ipPermissions = [
      new IpPermission().withUserIdGroupPairs(new UserIdGroupPair(userId: testCredentials.accountId, groupId: 'sg-2', groupName: 'group2'))
    ]
    def sourceUpdater = Stub(SecurityGroupUpdater) { getSecurityGroup() >> sourceGroup }

    when:
    def results = strategy.generateResults(source, target, sourceLookup, targetLookup, false, true)

    then:
    results.errors.sourceName == ['group2']
    results.reused.empty
    results.created.empty
    !results.targetExists()
    1 * sourceLookup.getSecurityGroupByName('test', 'group1', null) >> sourceUpdater
  }

  void 'generates ingress rules based on source'() {
    def source = new SecurityGroupLocation(credentials: testCredentials, region: 'us-east-1', name: 'group1')
    def target = new SecurityGroupLocation(credentials: prodCredentials, region: 'us-west-1')
    def sourceGroup = new SecurityGroup(groupName: 'group1', groupId: 'sg-1', ownerId: testCredentials.accountId)
    def targetGroup1 = new SecurityGroup(groupName: 'group1', groupId: 'sg-5', ownerId: prodCredentials.accountId)
    def targetGroup2 = new SecurityGroup(groupName: 'group2', groupId: 'sg-6', ownerId: prodCredentials.accountId)
    def targetGroup3 = new SecurityGroup(groupName: 'group3', groupId: 'sg-7', ownerId: prodCredentials.accountId)
    sourceGroup.ipPermissions = [
      new IpPermission()
        .withUserIdGroupPairs(
        new UserIdGroupPair(userId: testCredentials.accountId, groupId: 'sg-2', groupName: 'group2'),
        new UserIdGroupPair(userId: prodCredentials.accountId, groupId: 'sg-3', groupName: 'group3'))
        .withFromPort(7001).withToPort(7003),
      new IpPermission()
        .withUserIdGroupPairs(new UserIdGroupPair(userId: testCredentials.accountId, groupId: 'sg-1', groupName: 'group1'))
        .withFromPort(7000).withToPort(7002)
    ]
    def sourceUpdater = Stub(SecurityGroupUpdater) {
      getSecurityGroup() >> sourceGroup
    }
    def targetUpdater1 = Mock(SecurityGroupUpdater) {
      getSecurityGroup() >> targetGroup1
    }
    def targetUpdater2 = Stub(SecurityGroupUpdater) {
      getSecurityGroup() >> targetGroup2
    }
    def targetUpdater3 = Stub(SecurityGroupUpdater) {
      getSecurityGroup() >> targetGroup3
    }


    when:
    def results = strategy.generateResults(source, target, sourceLookup, targetLookup, false, false)

    then:
    results.ingressUpdates.size() == 3
    results.target.targetId == 'sg-5'
    sourceLookup.getSecurityGroupByName('test', 'group1', null) >> sourceUpdater
    targetLookup.getSecurityGroupByName('prod', 'group1', null) >>> [null, targetUpdater1]
    targetLookup.getSecurityGroupByName('test', 'group2', null) >>> [null, targetUpdater2]
    targetLookup.getSecurityGroupByName('prod', 'group3', null) >>> [null, targetUpdater3]
    1 * targetLookup.createSecurityGroup({t -> t.name == 'group1'}) >> targetUpdater1
    1 * targetLookup.createSecurityGroup({t -> t.name == 'group2'}) >> targetUpdater2
    1 * targetLookup.createSecurityGroup({t -> t.name == 'group3'}) >> targetUpdater3
    1 * targetUpdater1.addIngress({t ->
      t.size() == 3 &&
      t[0].fromPort == 7001 && t[0].toPort == 7003 && t[0].userIdGroupPairs.groupId == ['sg-6'] &&
        t[1].fromPort == 7001 && t[1].toPort == 7003 && t[1].userIdGroupPairs.groupId == ['sg-7'] &&
        t[2].fromPort == 7000 && t[2].toPort == 7002 && t[2].userIdGroupPairs.groupId == ['sg-5']
    })
  }

  void 'skips existing ingress rules'() {
    def source = new SecurityGroupLocation(credentials: testCredentials, region: 'us-east-1', name: 'group1')
    def target = new SecurityGroupLocation(credentials: prodCredentials, region: 'us-west-1')
    def sourceGroup = new SecurityGroup(groupName: 'group1', groupId: 'sg-1', ownerId: testCredentials.accountId)
    def targetGroup1 = new SecurityGroup(groupName: 'group1', groupId: 'sg-5', ownerId: prodCredentials.accountId)
    def targetGroup2 = new SecurityGroup(groupName: 'group2', groupId: 'sg-6', ownerId: prodCredentials.accountId)
    sourceGroup.ipPermissions = [
      new IpPermission()
        .withUserIdGroupPairs(
        new UserIdGroupPair(userId: testCredentials.accountId, groupId: 'sg-2', groupName: 'group2'))
        .withFromPort(7001).withToPort(7003),
      new IpPermission()
        .withUserIdGroupPairs(new UserIdGroupPair(userId: testCredentials.accountId, groupId: 'sg-1', groupName: 'group1'))
        .withFromPort(7000).withToPort(7002)
    ]
    targetGroup1.ipPermissions = [
      new IpPermission()
        .withUserIdGroupPairs(new UserIdGroupPair(userId: prodCredentials.accountId, groupId: 'sg-5', groupName: 'group1'))
        .withFromPort(7000).withToPort(7002)
    ]
    def sourceUpdater = Stub(SecurityGroupUpdater) {
      getSecurityGroup() >> sourceGroup
    }
    def targetUpdater1 = Mock(SecurityGroupUpdater) {
      getSecurityGroup() >> targetGroup1
    }
    def targetUpdater2 = Stub(SecurityGroupUpdater) {
      getSecurityGroup() >> targetGroup2
    }

    when:
    def results = strategy.generateResults(source, target, sourceLookup, targetLookup, false, false)

    then:
    results.ingressUpdates.size() == 1
    results.target.targetId == 'sg-5'
    sourceLookup.getSecurityGroupByName('test', 'group1', null) >> sourceUpdater
    targetLookup.getSecurityGroupByName('prod', 'group1', null) >> targetUpdater1
    targetLookup.getSecurityGroupByName('test', 'group2', null) >>> [null, targetUpdater2]
    0 * targetLookup.createSecurityGroup({t -> t.name == 'group1'}) >> targetUpdater1
    1 * targetLookup.createSecurityGroup({t -> t.name == 'group2'}) >> targetUpdater2
    1 * targetUpdater1.addIngress({t -> t.size() == 1
      t[0].fromPort == 7001 && t[0].toPort == 7003 && t[0].userIdGroupPairs.groupId == ['sg-6']
    })
  }

  void 'creates range rules that do not exist in target'() {
    def source = new SecurityGroupLocation(credentials: testCredentials, region: 'us-east-1', name: 'group1')
    def target = new SecurityGroupLocation(credentials: prodCredentials, region: 'us-west-1')
    def sourceGroup = new SecurityGroup(groupName: 'group1', groupId: 'sg-1', ownerId: testCredentials.accountId)
    def targetGroup1 = new SecurityGroup(groupName: 'group1', groupId: 'sg-5', ownerId: prodCredentials.accountId)
    def targetGroup2 = new SecurityGroup(groupName: 'group2', groupId: 'sg-6', ownerId: prodCredentials.accountId)
    sourceGroup.ipPermissions = [
      new IpPermission().withUserIdGroupPairs([]).withIpRanges("1.2.3.4").withFromPort(7001).withToPort(7003)
    ]
    targetGroup1.ipPermissions = [
      new IpPermission().withUserIdGroupPairs([]).withIpRanges("1.2.3.5").withFromPort(7001).withToPort(7003),
      new IpPermission().withUserIdGroupPairs([]).withIpRanges("1.2.3.4").withFromPort(7004).withToPort(7004)
    ]
    def sourceUpdater = Stub(SecurityGroupUpdater) {
      getSecurityGroup() >> sourceGroup
    }
    def targetUpdater1 = Mock(SecurityGroupUpdater) {
      getSecurityGroup() >> targetGroup1
    }
    def targetUpdater2 = Stub(SecurityGroupUpdater) {
      getSecurityGroup() >> targetGroup2
    }

    when:
    def results = strategy.generateResults(source, target, sourceLookup, targetLookup, false, false)

    then:
    results.ingressUpdates.size() == 1
    results.target.targetId == 'sg-5'
    sourceLookup.getSecurityGroupByName('test', 'group1', null) >> sourceUpdater
    targetLookup.getSecurityGroupByName('prod', 'group1', null) >> targetUpdater1
    targetLookup.getSecurityGroupByName('test', 'group2', null) >>> [null, targetUpdater2]
    1 * targetUpdater1.addIngress({t -> t.size() == 1
      t[0].fromPort == 7001 && t[0].toPort == 7003 && t[0].ipRanges == ["1.2.3.4"]
    })
  }

  void 'skips existing range rules'() {
    def source = new SecurityGroupLocation(credentials: testCredentials, region: 'us-east-1', name: 'group1')
    def target = new SecurityGroupLocation(credentials: prodCredentials, region: 'us-west-1')
    def sourceGroup = new SecurityGroup(groupName: 'group1', groupId: 'sg-1', ownerId: testCredentials.accountId)
    def targetGroup1 = new SecurityGroup(groupName: 'group1', groupId: 'sg-5', ownerId: prodCredentials.accountId)
    def targetGroup2 = new SecurityGroup(groupName: 'group2', groupId: 'sg-6', ownerId: prodCredentials.accountId)
    sourceGroup.ipPermissions = [
      new IpPermission().withUserIdGroupPairs([]).withIpRanges("1.2.3.4").withFromPort(7001).withToPort(7003)
    ]
    targetGroup1.ipPermissions = [
      new IpPermission().withUserIdGroupPairs([]).withIpRanges("1.2.3.4").withFromPort(7001).withToPort(7003)
    ]
    def sourceUpdater = Stub(SecurityGroupUpdater) {
      getSecurityGroup() >> sourceGroup
    }
    def targetUpdater1 = Mock(SecurityGroupUpdater) {
      getSecurityGroup() >> targetGroup1
    }
    def targetUpdater2 = Stub(SecurityGroupUpdater) {
      getSecurityGroup() >> targetGroup2
    }

    when:
    def results = strategy.generateResults(source, target, sourceLookup, targetLookup, false, false)

    then:
    results.ingressUpdates.empty
    results.target.targetId == 'sg-5'
    sourceLookup.getSecurityGroupByName('test', 'group1', null) >> sourceUpdater
    targetLookup.getSecurityGroupByName('prod', 'group1', null) >> targetUpdater1
    targetLookup.getSecurityGroupByName('test', 'group2', null) >>> [null, targetUpdater2]
  }

  private static class ErrorfulMigrationStrategy implements MigrateSecurityGroupStrategy {

    private AmazonClientProvider amazonClientProvider

    @Override
    public AmazonClientProvider getAmazonClientProvider() {
      return amazonClientProvider
    }

    public ErrorfulMigrationStrategy(AmazonClientProvider amazonClientProvider) {
      this.amazonClientProvider = amazonClientProvider
    }

    @Override
    Set<MigrateSecurityGroupReference> shouldError(SecurityGroupLookup sourceLookup,
                                                   SecurityGroupLookup targetLookup,
                                                   SecurityGroupLocation target,
                                                   Set<MigrateSecurityGroupReference> references) {
      return references;
    }
  }
}
