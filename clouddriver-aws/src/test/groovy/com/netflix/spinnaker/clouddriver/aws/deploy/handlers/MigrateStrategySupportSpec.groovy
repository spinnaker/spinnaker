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
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupLookup
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupUpdater
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class MigrateStrategySupportSpec extends Specification {

  @Subject
  MigrateStrategySupport strategy = new TestMigrateStrategy()

  @Shared
  NetflixAmazonCredentials testCredentials = TestCredential.named('test')

  SecurityGroupLookup lookup

  void setup() {
    lookup = Mock()
  }

  void 'returns immediately when classicLinkGroupName is null'() {
    when:
    strategy.addClassicLinkIngress(lookup, null, 'sg-1', testCredentials, 'vpc-1')

    then:
    0 * _
  }

  void 'returns without adding an ingress or looking up classic link group if target not found'() {
    when:
    strategy.addClassicLinkIngress(lookup, 'nf-classic-link', 'sg-1', testCredentials, 'vpc-1')

    then:
    1 * lookup.getSecurityGroupById('test', 'sg-1', 'vpc-1') >> Optional.empty()
    0 * _
  }

  void 'returns without adding an ingress when classic link group not found'() {
    given:
    SecurityGroup securityGroup = new SecurityGroup()
    SecurityGroupUpdater updater = Stub() {
      getSecurityGroup() >> securityGroup
    }

    when:
    strategy.addClassicLinkIngress(lookup, 'nf-classic-link', 'sg-1', testCredentials, 'vpc-1')

    then:
    1 * lookup.getSecurityGroupById('test', 'sg-1', 'vpc-1') >> Optional.of(updater)
    1 * lookup.getSecurityGroupByName('test', 'nf-classic-link', 'vpc-1') >> Optional.empty()
    0 * _
  }

  void 'returns without adding an ingress when classic link group already has ingress'() {
    given:
    SecurityGroup securityGroup = new SecurityGroup()
    SecurityGroup classicLinkGroup = new SecurityGroup(groupId: 'sg-c1')
    securityGroup.ipPermissions = [
      new IpPermission().withUserIdGroupPairs(new UserIdGroupPair().withGroupId('sg-c'))
    ]
    SecurityGroupUpdater updater = Stub() {
      getSecurityGroup() >> securityGroup
    }
    SecurityGroupUpdater classicLinkUpdater = Stub() {
      getSecurityGroup() >> classicLinkGroup
    }

    when:
    strategy.addClassicLinkIngress(lookup, 'nf-classic-link', 'sg-1', testCredentials, 'vpc-1')

    then:
    1 * lookup.getSecurityGroupById('test', 'sg-1', 'vpc-1') >> Optional.of(updater)
    1 * lookup.getSecurityGroupByName('test', 'nf-classic-link', 'vpc-1') >> Optional.of(classicLinkUpdater)
    0 * _
  }

  @Unroll
  void 'adds an ingress when one not already present for classic link group'() {
    given:
    SecurityGroup securityGroup = new SecurityGroup()
    SecurityGroup classicLinkGroup = new SecurityGroup(groupId: 'sg-c1')
    securityGroup.ipPermissions = [
      new IpPermission().withUserIdGroupPairs(userIdGroupPairs)
    ]
    SecurityGroupUpdater updater = Mock()
    SecurityGroupUpdater classicLinkUpdater = Stub() {
      getSecurityGroup() >> classicLinkGroup
    }

    when:
    strategy.addClassicLinkIngress(lookup, 'nf-classic-link', 'sg-1', testCredentials, 'vpc-1')

    then:
    1 * lookup.getSecurityGroupById('test', 'sg-1', 'vpc-1') >> Optional.of(updater)
    1 * lookup.getSecurityGroupByName('test', 'nf-classic-link', 'vpc-1') >> Optional.of(classicLinkUpdater)
    1 * updater.getSecurityGroup() >> securityGroup
    1 * updater.addIngress({ rules ->
      def rule = rules[0]
      def pair = rule.userIdGroupPairs[0]
      rule.ipProtocol == 'tcp' && rule.fromPort == 80 && rule.toPort == 65535 &&
        pair.groupId == 'sg-c1' && pair.vpcId == 'vpc-1' && pair.userId == testCredentials.accountId
    })
    0 * _

    where:
    userIdGroupPairs << [
      [],
      [new UserIdGroupPair().withGroupId('sg-d')],
      [new UserIdGroupPair().withGroupId('sg-d'), new UserIdGroupPair().withGroupId('sg-e')]
    ]
  }


  static class TestMigrateStrategy implements MigrateStrategySupport {}
}
