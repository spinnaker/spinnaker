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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult
import com.amazonaws.services.ec2.model.DescribeVpcsResult
import com.amazonaws.services.ec2.model.SecurityGroup
import com.amazonaws.services.ec2.model.Tag
import com.amazonaws.services.ec2.model.Vpc
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerResult
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult
import com.amazonaws.services.elasticloadbalancing.model.Listener
import com.amazonaws.services.elasticloadbalancing.model.ListenerDescription
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.netflix.spinnaker.clouddriver.aws.AwsConfiguration.DeployDefaults
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.DefaultMigrateLoadBalancerStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateLoadBalancerStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateSecurityGroupStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.LoadBalancerMigrator.LoadBalancerLocation
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.MigrateSecurityGroupReference
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.MigrateSecurityGroupResult
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupLookup
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupUpdater
import com.netflix.spinnaker.clouddriver.aws.model.SubnetAnalyzer
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory.RegionScopedProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class MigrateLoadBalancerStrategySpec extends Specification {

  @Subject
  MigrateLoadBalancerStrategy strategy

  @Shared
  NetflixAmazonCredentials testCredentials = TestCredential.named('test')

  @Shared
  NetflixAmazonCredentials prodCredentials = TestCredential.named('prod')

  MigrateSecurityGroupStrategy securityGroupStrategy = Mock(MigrateSecurityGroupStrategy)

  RegionScopedProviderFactory regionScopedProviderFactory = Mock(RegionScopedProviderFactory)

  DeployDefaults deployDefaults = Mock(DeployDefaults)

  SecurityGroupLookup sourceLookup = Mock(SecurityGroupLookup)

  SecurityGroupLookup targetLookup = Mock(SecurityGroupLookup)

  AmazonClientProvider amazonClientProvider = Mock(AmazonClientProvider)

  def setup() {
    TaskRepository.threadLocalTask.set(Stub(Task))
    strategy = new DefaultMigrateLoadBalancerStrategy(amazonClientProvider, regionScopedProviderFactory, deployDefaults)
    strategy.sourceLookup = sourceLookup
    strategy.targetLookup = targetLookup
    strategy.migrateSecurityGroupStrategy = securityGroupStrategy

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

  void 'getTargetSecurityGroups maps security group IDs to actual security groups'() {
    given:
    LoadBalancerDescription sourceDescription = new LoadBalancerDescription().withSecurityGroups('sg-1', 'sg-2')
    LoadBalancerLocation source = new LoadBalancerLocation(credentials: testCredentials, vpcId: 'vpc-1', region: 'us-east-1')
    LoadBalancerLocation target = new LoadBalancerLocation(credentials: prodCredentials, vpcId: 'vpc-2', region: 'eu-west-1')

    MigrateSecurityGroupReference targetGroup1 = new MigrateSecurityGroupReference(targetName: 'group1')
    MigrateSecurityGroupReference targetGroup2 = new MigrateSecurityGroupReference(targetName: 'group2')

    def sourceGroup1 = new SecurityGroup(groupName: 'group1', groupId: 'sg-1', ownerId: testCredentials.accountId)
    def sourceGroup2 = new SecurityGroup(groupName: 'group2', groupId: 'sg-2', ownerId: testCredentials.accountId)

    def sourceUpdater1 = Stub(SecurityGroupUpdater) {
      getSecurityGroup() >> sourceGroup1
    }
    def sourceUpdater2 = Stub(SecurityGroupUpdater) {
      getSecurityGroup() >> sourceGroup2
    }

    when:
    def targets = strategy.getTargetSecurityGroups(sourceDescription, source, target, new MigrateLoadBalancerResult(), true)

    then:
    targets.target == [targetGroup1, targetGroup2]
    1 * securityGroupStrategy.generateResults({s -> s.name == 'group1'}, _, _, _, _, _) >> new MigrateSecurityGroupResult(target: targetGroup1)
    1 * securityGroupStrategy.generateResults({s -> s.name == 'group2'}, _, _, _, _, _) >> new MigrateSecurityGroupResult(target: targetGroup2)
    sourceLookup.getSecurityGroupById('test', 'sg-1', 'vpc-1') >> Optional.of(sourceUpdater1)
    sourceLookup.getSecurityGroupById('test', 'sg-2', 'vpc-1') >> Optional.of(sourceUpdater2)
  }

  void 'warns when migrating across accounts on secured listeners'() {
    given:
    LoadBalancerDescription sourceDescription = new LoadBalancerDescription(loadBalancerName: 'app-elb',
      listenerDescriptions: [
        new ListenerDescription().withListener(
          new Listener().withLoadBalancerPort(443).withInstancePort(7000).withSSLCertificateId('does:not:matter')),
        new ListenerDescription().withListener(
          new Listener().withLoadBalancerPort(80).withInstancePort(7000))
      ]
    )
    LoadBalancerLocation source = new LoadBalancerLocation(credentials: testCredentials, region: 'us-east-1', name: 'app-elb')
    LoadBalancerLocation target = new LoadBalancerLocation(credentials: prodCredentials, region: 'eu-west-1', availabilityZones: ['eu-west-1a'])

    AmazonEC2 amazonEC2 = Mock(AmazonEC2)
    AmazonElasticLoadBalancing loadBalancing = Mock(AmazonElasticLoadBalancing)
    AmazonElasticLoadBalancing targetLoadBalancing = Mock(AmazonElasticLoadBalancing)
    RegionScopedProvider regionProvider = Mock(RegionScopedProvider)

    when:
    def result = strategy.generateResults(sourceLookup, targetLookup, securityGroupStrategy, source, target, null, 'app', false)

    then:
    result.warnings.size() == 1
    amazonClientProvider.getAmazonEC2(prodCredentials, 'eu-west-1', true) >> amazonEC2
    amazonClientProvider.getAmazonEC2(prodCredentials, 'eu-west-1') >> amazonEC2
    amazonClientProvider.getAmazonElasticLoadBalancing(testCredentials, 'us-east-1', true) >> loadBalancing
    amazonClientProvider.getAmazonElasticLoadBalancing(prodCredentials, 'eu-west-1', true) >> targetLoadBalancing
    regionScopedProviderFactory.forRegion(prodCredentials, 'eu-west-1') >> regionProvider
    1 * loadBalancing.describeLoadBalancers(_) >> new DescribeLoadBalancersResult().withLoadBalancerDescriptions(sourceDescription)
    1 * targetLoadBalancing.describeLoadBalancers(_) >> null
    1 * targetLoadBalancing.createLoadBalancer({ it.listeners.loadBalancerPort == [80]}) >> new CreateLoadBalancerResult().withDNSName('new-elb-dns')
    0 * amazonEC2.authorizeSecurityGroupIngress(_)
  }

  void 'creates elb group and adds classic link ingress when moving from non-VPC to VPC'() {
    given:
    LoadBalancerDescription sourceDescription = new LoadBalancerDescription(loadBalancerName: 'app-elb',
      listenerDescriptions: [ new ListenerDescription().withListener(
        new Listener().withLoadBalancerPort(80).withInstancePort(7000))
      ]
    )
    LoadBalancerLocation source = new LoadBalancerLocation(credentials: testCredentials, region: 'us-east-1', name: 'app-elb')
    LoadBalancerLocation target = new LoadBalancerLocation(credentials: prodCredentials, vpcId: 'vpc-2', region: 'eu-west-1', availabilityZones: ['eu-west-1a'])

    AmazonEC2 amazonEC2 = Mock(AmazonEC2)
    AmazonElasticLoadBalancing loadBalancing = Mock(AmazonElasticLoadBalancing)
    AmazonElasticLoadBalancing targetLoadBalancing = Mock(AmazonElasticLoadBalancing)
    RegionScopedProvider regionProvider = Mock(RegionScopedProvider)
    SubnetAnalyzer subnetAnalyzer = Mock(SubnetAnalyzer)

    def appGroup = new SecurityGroup(groupName: 'app', groupId: 'sg-3', ownerId: prodCredentials.accountId, vpcId: 'vpc-2')
    def elbGroup = new SecurityGroup(groupName: 'app-elb', groupId: 'sg-4', ownerId: prodCredentials.accountId, vpcId: 'vpc-2')

    when:
    strategy.generateResults(sourceLookup, targetLookup, securityGroupStrategy, source, target, 'internal', 'app', false)

    then:
    amazonClientProvider.getAmazonEC2(prodCredentials, 'eu-west-1', true) >> amazonEC2
    amazonClientProvider.getAmazonEC2(prodCredentials, 'eu-west-1') >> amazonEC2
    amazonClientProvider.getAmazonElasticLoadBalancing(testCredentials, 'us-east-1', true) >> loadBalancing
    amazonClientProvider.getAmazonElasticLoadBalancing(prodCredentials, 'eu-west-1', true) >> targetLoadBalancing
    regionScopedProviderFactory.forRegion(prodCredentials, 'eu-west-1') >> regionProvider
    regionProvider.getSubnetAnalyzer() >> subnetAnalyzer
    1 * loadBalancing.describeLoadBalancers(_) >> new DescribeLoadBalancersResult().withLoadBalancerDescriptions(sourceDescription)
    1 * targetLoadBalancing.createLoadBalancer(_) >> new CreateLoadBalancerResult().withDNSName('new-elb-dns')
    1 * amazonEC2.createSecurityGroup(_) >> new CreateSecurityGroupResult().withGroupId('sg-4')
    1 * targetLookup.getSecurityGroupByName(prodCredentials.name, 'classic-link', 'vpc-2') >> Optional.of(new SecurityGroupUpdater(elbGroup, amazonEC2))
    1 * amazonEC2.describeSecurityGroups({r -> r.filters[0].values == ['app', 'app-elb']}) >> new DescribeSecurityGroupsResult().withSecurityGroups([appGroup])
    1 * amazonEC2.describeVpcs(_) >> new DescribeVpcsResult().withVpcs(new Vpc())
    1 * amazonEC2.authorizeSecurityGroupIngress({r -> r.groupId == 'sg-4' &&
      !r.ipPermissions.empty &&
      r.ipPermissions[0].fromPort == 80 &&
      r.ipPermissions[0].toPort == 65535 &&
      r.ipPermissions[0].ipProtocol == 'tcp'})
    1 * amazonEC2.authorizeSecurityGroupIngress({r -> r.groupId == 'sg-4' &&
      r.ipPermissions[0].fromPort == 80 &&
      r.ipPermissions[0].toPort == 80 &&
      r.ipPermissions[0].ipProtocol == 'tcp'})
    1 * amazonEC2.authorizeSecurityGroupIngress({r -> r.groupId == 'sg-3' &&
      r.ipPermissions[0].fromPort == 7000 &&
      r.ipPermissions[0].toPort == 7000 &&
      r.ipPermissions[0].ipProtocol == 'tcp'})
    deployDefaults.getClassicLinkSecurityGroupName() >> 'classic-link'
    deployDefaults.getAddAppGroupToServerGroup() >> true
  }

  @Unroll
  void 'name generator converts #loadBalancerName to #result'() {
    when:
    Vpc sourceVpc = sourceVpcName ? new Vpc().withTags(new Tag('Name', sourceVpcName)) : null
    Vpc targetVpc = targetVpcName ? new Vpc().withTags(new Tag('Name', targetVpcName)) : null

    then:
    strategy.generateLoadBalancerName(loadBalancerName, sourceVpc, targetVpc) == result

    where:
    loadBalancerName                    | sourceVpcName | targetVpcName || result
    '12345678901234567890123456789012'  | null          | null          || '12345678901234567890123456789012'
    '123456789012345678901234567890123' | null          | null          || '12345678901234567890123456789012'
    'abc-vpc0'                          | 'vpc0'        | 'vpc2'        || 'abc-vpc2'
    'abc-vpc'                           | 'vpc0'        | 'vpc2'        || 'abc-vpc2'
    'abc-vpc1'                          | 'vpc0'        | 'vpc2'        || 'abc-vpc1-vpc2'
    '123456789012345678901234567-elb'   | null          | 'vpc2'        || '123456789012345678901234567-vpc2'
    '12345678901234567890123-elb'       | null          | 'vpc2'        || '12345678901234567890123-elb-vpc2'
  }
}
