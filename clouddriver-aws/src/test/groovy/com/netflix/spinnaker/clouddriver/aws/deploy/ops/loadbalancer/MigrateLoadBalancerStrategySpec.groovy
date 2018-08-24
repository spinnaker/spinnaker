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
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult
import com.amazonaws.services.ec2.model.DescribeVpcsResult
import com.amazonaws.services.ec2.model.SecurityGroup
import com.amazonaws.services.ec2.model.Tag
import com.amazonaws.services.ec2.model.Vpc
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerResult
import com.amazonaws.services.elasticloadbalancing.model.CrossZoneLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancerAttributesResult
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancerPoliciesResult
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult
import com.amazonaws.services.elasticloadbalancing.model.HealthCheck
import com.amazonaws.services.elasticloadbalancing.model.Listener
import com.amazonaws.services.elasticloadbalancing.model.ListenerDescription
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerAttributes
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.amazonaws.services.elasticloadbalancing.model.PolicyAttributeDescription
import com.amazonaws.services.elasticloadbalancing.model.PolicyDescription
import com.netflix.spinnaker.config.AwsConfiguration.DeployDefaults
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.DefaultMigrateLoadBalancerStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateLoadBalancerStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateSecurityGroupStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.LoadBalancerMigrator.LoadBalancerLocation
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.LoadBalancerMigrator.TargetLoadBalancerLocation
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.MigrateSecurityGroupReference
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.MigrateSecurityGroupResult
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupLookup
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupUpdater
import com.netflix.spinnaker.clouddriver.aws.model.SubnetAnalyzer
import com.netflix.spinnaker.clouddriver.aws.model.SubnetTarget
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

  void 'throws exception if no availability zones are supplied'() {
    given:
    LoadBalancerLocation source = new LoadBalancerLocation(credentials: testCredentials, region: 'us-east-1', name: 'app-elb')
    TargetLoadBalancerLocation target = new TargetLoadBalancerLocation(credentials: testCredentials, region: 'us-east-1', vpcId: 'vpc-1', useZonesFromSource: false)

    when:
    strategy.generateResults(sourceLookup, sourceLookup, securityGroupStrategy, source, target, 'internal', 'app', true, false)

    then:
    thrown(IllegalStateException)
    0 * _
  }

  void 'resolves zones from source load balancer if requested'() {
    given:
    LoadBalancerLocation source = new LoadBalancerLocation(credentials: testCredentials, region: 'us-east-1', name: 'app-elb')
    TargetLoadBalancerLocation target = new TargetLoadBalancerLocation(credentials: testCredentials, region: 'us-east-1', vpcId: 'vpc-1', useZonesFromSource: true)
    AmazonElasticLoadBalancing loadBalancing = Mock(AmazonElasticLoadBalancing)

    when:
    strategy.generateResults(sourceLookup, sourceLookup, securityGroupStrategy, source, target, 'internal', 'app', true, false)

    then:
    1 * amazonClientProvider.getAmazonElasticLoadBalancing(testCredentials, 'us-east-1', true) >> loadBalancing
    1 * loadBalancing.describeLoadBalancers(_) >> new DescribeLoadBalancersResult().withLoadBalancerDescriptions(
      new LoadBalancerDescription().withLoadBalancerName('app-elb').withAvailabilityZones([])
    )
    thrown(IllegalStateException)
    0 * _
  }

  void 'throws exception when migrating to VPC and load balancer name (not changed) already exists in Classic'() {
    given:
    def loadBalancerName = '12345678901234567890123456789012'
    LoadBalancerDescription sourceDescription = new LoadBalancerDescription().withLoadBalancerName(loadBalancerName)
    LoadBalancerLocation source = new LoadBalancerLocation(credentials: testCredentials, region: 'us-east-1', name: loadBalancerName)
    TargetLoadBalancerLocation target = new TargetLoadBalancerLocation(credentials: testCredentials, region: 'us-east-1', vpcId: 'vpc-1', availabilityZones: ['us-east-1a'])
    strategy.source = source
    strategy.target = target
    strategy.dryRun = false

    AmazonEC2 amazonEC2 = Mock(AmazonEC2)
    AmazonElasticLoadBalancing loadBalancing = Mock(AmazonElasticLoadBalancing)

    when:
    strategy.generateResults(sourceLookup, sourceLookup, securityGroupStrategy, source, target, 'internal', 'app', true, false)

    then:
    thrown(IllegalStateException)

    2 * loadBalancing.describeLoadBalancers(_) >> new DescribeLoadBalancersResult().withLoadBalancerDescriptions(sourceDescription)
    amazonClientProvider.getAmazonEC2(testCredentials, 'us-east-1') >> amazonEC2
    amazonClientProvider.getAmazonElasticLoadBalancing(testCredentials, 'us-east-1', true) >> loadBalancing
    amazonEC2.describeVpcs(_) >> new DescribeVpcsResult().withVpcs(new Vpc().withTags(new Tag("Name", "vpc1")))
  }

  void 'throws exception when migrating to VPC and no subnets found for subnetType'() {
    given:
    def loadBalancerName = 'app-elb'
    def newLoadBalancerName = 'app-elb-vpc1'
    LoadBalancerDescription sourceDescription = new LoadBalancerDescription().withLoadBalancerName(loadBalancerName)
    LoadBalancerLocation source = new LoadBalancerLocation(credentials: testCredentials, region: 'us-east-1', name: 'app-elb')
    TargetLoadBalancerLocation target = new TargetLoadBalancerLocation(credentials: testCredentials, region: 'us-east-1', vpcId: 'vpc-1', availabilityZones: ['us-east-1a'])

    AmazonEC2 amazonEC2 = Mock(AmazonEC2)
    AmazonElasticLoadBalancing loadBalancing = Mock(AmazonElasticLoadBalancing)
    RegionScopedProvider regionProvider = Mock(RegionScopedProvider)
    SubnetAnalyzer subnetAnalyzer = Mock(SubnetAnalyzer)

    when:
    strategy.generateResults(sourceLookup, sourceLookup, securityGroupStrategy, source, target, 'internal', 'app', true, false)

    then:
    1 * loadBalancing.describeLoadBalancers({ it.loadBalancerNames == [loadBalancerName]}) >> new DescribeLoadBalancersResult().withLoadBalancerDescriptions(sourceDescription)
    1 * loadBalancing.describeLoadBalancers({ it.loadBalancerNames == [newLoadBalancerName]}) >> new DescribeLoadBalancersResult()

    1 * amazonEC2.describeSecurityGroups(_) >> new DescribeSecurityGroupsResult().withSecurityGroups([new SecurityGroup(vpcId: 'vpc-1', groupName: 'app-elb'), new SecurityGroup()])

    amazonClientProvider.getAmazonEC2(testCredentials, 'us-east-1') >> amazonEC2
    amazonClientProvider.getAmazonEC2(testCredentials, 'us-east-1', true) >> amazonEC2
    amazonClientProvider.getAmazonElasticLoadBalancing(testCredentials, 'us-east-1', true) >> loadBalancing
    regionScopedProviderFactory.forRegion(testCredentials, 'us-east-1') >> regionProvider
    deployDefaults.addAppGroupToServerGroup >> false
    regionProvider.getSubnetAnalyzer() >> subnetAnalyzer
    amazonEC2.describeVpcs(_) >> new DescribeVpcsResult().withVpcs(new Vpc().withTags(new Tag("Name", "vpc1")))
    1 * subnetAnalyzer.getSubnetIdsForZones(['us-east-1a'], 'internal', SubnetTarget.ELB, 1) >> []
    thrown(IllegalStateException)
  }

  void 'throws exception when migrating to VPC and new load balancer name already exists in Classic'() {
    given:
    def loadBalancerName = 'app-elb'
    def newLoadBalancerName = 'app-elb-vpc1'
    LoadBalancerDescription sourceDescription = new LoadBalancerDescription().withLoadBalancerName(loadBalancerName)
    LoadBalancerDescription targetDescription = new LoadBalancerDescription().withLoadBalancerName(newLoadBalancerName)
    LoadBalancerLocation source = new LoadBalancerLocation(credentials: testCredentials, region: 'us-east-1', name: loadBalancerName)
    TargetLoadBalancerLocation target = new TargetLoadBalancerLocation(credentials: testCredentials, region: 'us-east-1', vpcId: 'vpc-1', name: newLoadBalancerName, availabilityZones: ['us-east-1a'])
    strategy.source = source
    strategy.target = target
    strategy.dryRun = false

    AmazonEC2 amazonEC2 = Mock(AmazonEC2)
    AmazonElasticLoadBalancing loadBalancing = Mock(AmazonElasticLoadBalancing)

    when:
    strategy.generateResults(sourceLookup, sourceLookup, securityGroupStrategy, source, target, 'internal', 'app', true, false)

    then:
    thrown(IllegalStateException)

    1 * loadBalancing.describeLoadBalancers({ it.loadBalancerNames == [loadBalancerName]}) >> new DescribeLoadBalancersResult().withLoadBalancerDescriptions(sourceDescription)
    1 * loadBalancing.describeLoadBalancers({ it.loadBalancerNames == [newLoadBalancerName]}) >> new DescribeLoadBalancersResult().withLoadBalancerDescriptions(targetDescription)
    amazonClientProvider.getAmazonEC2(testCredentials, 'us-east-1') >> amazonEC2
    amazonClientProvider.getAmazonElasticLoadBalancing(testCredentials, 'us-east-1', true) >> loadBalancing
    amazonEC2.describeVpcs(_) >> new DescribeVpcsResult().withVpcs(new Vpc().withTags(new Tag("Name", "vpc1")))
  }

  void 'getTargetSecurityGroups maps security group IDs to actual security groups'() {
    given:
    LoadBalancerDescription sourceDescription = new LoadBalancerDescription().withSecurityGroups('sg-1', 'sg-2')
    LoadBalancerLocation source = new LoadBalancerLocation(credentials: testCredentials, vpcId: 'vpc-1', region: 'us-east-1')
    TargetLoadBalancerLocation target = new TargetLoadBalancerLocation(credentials: prodCredentials, vpcId: 'vpc-2', region: 'eu-west-1')
    strategy.source = source
    strategy.target = target
    strategy.dryRun = true

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
    def targets = strategy.getTargetSecurityGroups(sourceDescription, new MigrateLoadBalancerResult())

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
      healthCheck: new HealthCheck(),
      listenerDescriptions: [
        new ListenerDescription().withListener(
          new Listener().withLoadBalancerPort(443).withInstancePort(7000).withSSLCertificateId('does:not:matter')),
        new ListenerDescription().withListener(
          new Listener().withLoadBalancerPort(80).withInstancePort(7000))
      ]
    )
    LoadBalancerLocation source = new LoadBalancerLocation(credentials: testCredentials, region: 'us-east-1', name: 'app-elb')
    TargetLoadBalancerLocation target = new TargetLoadBalancerLocation(credentials: prodCredentials, region: 'eu-west-1', availabilityZones: ['eu-west-1a'])

    AmazonEC2 amazonEC2 = Mock(AmazonEC2)
    AmazonElasticLoadBalancing loadBalancing = Mock(AmazonElasticLoadBalancing)
    AmazonElasticLoadBalancing targetLoadBalancing = Mock(AmazonElasticLoadBalancing)
    RegionScopedProvider regionProvider = Mock(RegionScopedProvider)

    when:
    def result = strategy.generateResults(sourceLookup, targetLookup, securityGroupStrategy, source, target, null, 'app', false, false)

    then:
    result.warnings.size() == 1
    amazonClientProvider.getAmazonEC2(prodCredentials, 'eu-west-1', true) >> amazonEC2
    amazonClientProvider.getAmazonEC2(prodCredentials, 'eu-west-1') >> amazonEC2
    amazonClientProvider.getAmazonElasticLoadBalancing(testCredentials, 'us-east-1', true) >> loadBalancing
    amazonClientProvider.getAmazonElasticLoadBalancing(prodCredentials, 'eu-west-1', true) >> targetLoadBalancing
    regionScopedProviderFactory.forRegion(prodCredentials, 'eu-west-1') >> regionProvider
    1 * loadBalancing.describeLoadBalancers(_) >> new DescribeLoadBalancersResult().withLoadBalancerDescriptions(sourceDescription)
    1 * loadBalancing.describeLoadBalancerAttributes(_) >> new DescribeLoadBalancerAttributesResult()
    1 * targetLoadBalancing.describeLoadBalancers(_) >> null
    1 * loadBalancing.describeLoadBalancerPolicies(_) >> new DescribeLoadBalancerPoliciesResult()
    1 * targetLoadBalancing.describeLoadBalancerPolicies(_) >> new DescribeLoadBalancerPoliciesResult()
    1 * targetLoadBalancing.createLoadBalancer({ it.listeners.loadBalancerPort == [80]}) >> new CreateLoadBalancerResult().withDNSName('new-elb-dns')
    1 * targetLoadBalancing.configureHealthCheck(_)
    0 * amazonEC2.authorizeSecurityGroupIngress(_)
  }

  void 'skips skipped security groups'() {
    given:
    LoadBalancerDescription sourceDescription = new LoadBalancerDescription(healthCheck: new HealthCheck(),
      listenerDescriptions: []).withSecurityGroups('sg-1', 'sg-2')
    LoadBalancerLocation source = new LoadBalancerLocation(credentials: testCredentials, vpcId: 'vpc-1', region: 'us-east-1')
    TargetLoadBalancerLocation target = new TargetLoadBalancerLocation(credentials: prodCredentials, vpcId: 'vpc-2', region: 'eu-west-1', name: 'new-elb', availabilityZones: ['eu-west-1a'])
    strategy.source = source
    strategy.target = target
    strategy.dryRun = true

    MigrateSecurityGroupReference targetGroup1 = new MigrateSecurityGroupReference(targetName: 'group1', targetId: 'sg-1b')
    MigrateSecurityGroupReference targetGroup2 = new MigrateSecurityGroupReference(targetName: 'group2', targetId: 'sg-2b')

    def sourceGroup1 = new SecurityGroup(groupName: 'group1', groupId: 'sg-1', ownerId: testCredentials.accountId)
    def sourceGroup2 = new SecurityGroup(groupName: 'group2', groupId: 'sg-2', ownerId: testCredentials.accountId)
    def appGroup = new SecurityGroup(groupName: 'app-elb', groupId: 'sg-elb', ownerId: prodCredentials.accountId, vpcId: 'vpc-2')

    def sourceUpdater1 = Stub(SecurityGroupUpdater) {
      getSecurityGroup() >> sourceGroup1
    }
    def sourceUpdater2 = Stub(SecurityGroupUpdater) {
      getSecurityGroup() >> sourceGroup2
    }

    AmazonEC2 amazonEC2 = Mock(AmazonEC2)
    AmazonElasticLoadBalancing loadBalancing = Mock(AmazonElasticLoadBalancing)
    AmazonElasticLoadBalancing targetLoadBalancing = Mock(AmazonElasticLoadBalancing)
    RegionScopedProvider regionProvider = Mock(RegionScopedProvider)
    SubnetAnalyzer subnetAnalyzer = Mock(SubnetAnalyzer)

    when:
    def results = strategy.generateResults(sourceLookup, targetLookup, securityGroupStrategy, source, target, 'internal', 'app', true, false)

    then:
    results.securityGroups.size() == 2
    1 * securityGroupStrategy.generateResults({s -> s.name == 'group1'}, _, _, _, _, _) >> new MigrateSecurityGroupResult(target: targetGroup1, reused: [targetGroup1])
    1 * securityGroupStrategy.generateResults({s -> s.name == 'group2'}, _, _, _, _, _) >> new MigrateSecurityGroupResult(target: targetGroup2, skipped: [targetGroup2])
    sourceLookup.getSecurityGroupById('test', 'sg-1', 'vpc-1') >> Optional.of(sourceUpdater1)
    sourceLookup.getSecurityGroupById('test', 'sg-2', 'vpc-1') >> Optional.of(sourceUpdater2)

    amazonClientProvider.getAmazonEC2(testCredentials, 'us-east-1') >> amazonEC2
    amazonClientProvider.getAmazonEC2(prodCredentials, 'eu-west-1') >> amazonEC2
    amazonClientProvider.getAmazonEC2(prodCredentials, 'eu-west-1', true) >> amazonEC2
    2 * amazonEC2.describeVpcs(_) >> new DescribeVpcsResult().withVpcs(new Vpc())

    amazonClientProvider.getAmazonElasticLoadBalancing(testCredentials, 'us-east-1', true) >> loadBalancing
    1 * loadBalancing.describeLoadBalancers(_) >> new DescribeLoadBalancersResult().withLoadBalancerDescriptions(sourceDescription)

    1 * amazonEC2.describeSecurityGroups(_) >> new DescribeSecurityGroupsResult().withSecurityGroups([appGroup])

    regionScopedProviderFactory.forRegion(prodCredentials, 'eu-west-1') >> regionProvider
    regionProvider.getSubnetAnalyzer() >> subnetAnalyzer
    1 * subnetAnalyzer.getSubnetIdsForZones(['eu-west-1a'], 'internal', _, _) >> ['subnet-1']
    amazonClientProvider.getAmazonElasticLoadBalancing(prodCredentials, 'eu-west-1', true) >> targetLoadBalancing

    1 * targetLoadBalancing.createLoadBalancer({it.securityGroups == ['sg-1b', 'sg-elb']}) >> new CreateLoadBalancerResult().withDNSName('new-elb-dns')
    1 * loadBalancing.describeLoadBalancerAttributes(_) >> new DescribeLoadBalancerAttributesResult()
    1 * loadBalancing.describeLoadBalancerPolicies(_) >> new DescribeLoadBalancerPoliciesResult()
    1 * targetLoadBalancing.describeLoadBalancerPolicies(_) >> new DescribeLoadBalancerPoliciesResult()
  }


  void 'creates app group and elb group and adds classic link ingress when moving from non-VPC to VPC'() {
    given:
    LoadBalancerDescription sourceDescription = new LoadBalancerDescription(loadBalancerName: 'app-elb',
      healthCheck: new HealthCheck(),
      listenerDescriptions: [ new ListenerDescription().withListener(
        new Listener().withLoadBalancerPort(80).withInstancePort(7000))
      ]
    )
    LoadBalancerLocation source = new LoadBalancerLocation(credentials: testCredentials, region: 'us-east-1', name: 'app-elb')
    TargetLoadBalancerLocation target = new TargetLoadBalancerLocation(credentials: prodCredentials, vpcId: 'vpc-2', region: 'eu-west-1', availabilityZones: ['eu-west-1a'])

    AmazonEC2 amazonEC2 = Mock(AmazonEC2)
    AmazonElasticLoadBalancing loadBalancing = Mock(AmazonElasticLoadBalancing)
    AmazonElasticLoadBalancing targetLoadBalancing = Mock(AmazonElasticLoadBalancing)
    RegionScopedProvider regionProvider = Mock(RegionScopedProvider)
    SubnetAnalyzer subnetAnalyzer = Mock(SubnetAnalyzer)

    def appGroup = new SecurityGroup(groupName: 'app', groupId: 'sg-3', ownerId: prodCredentials.accountId, vpcId: 'vpc-2')
    def elbGroup = new SecurityGroup(groupName: 'app-elb', groupId: 'sg-4', ownerId: prodCredentials.accountId, vpcId: 'vpc-2')
    def classicGroup = new SecurityGroup(groupName: 'classic-link', groupId: 'sg-5', ownerId: prodCredentials.accountId, vpcId: 'vpc-2')

    when:
    def results = strategy.generateResults(sourceLookup, targetLookup, securityGroupStrategy, source, target, 'internal', 'app', true, false)

    then:
    results.securityGroups[0].created.targetName.sort() == ['app', 'app-elb']
    results.securityGroups[0].target.targetName == 'app-elb'
    amazonClientProvider.getAmazonEC2(prodCredentials, 'eu-west-1', true) >> amazonEC2
    amazonClientProvider.getAmazonEC2(prodCredentials, 'eu-west-1') >> amazonEC2
    amazonClientProvider.getAmazonElasticLoadBalancing(testCredentials, 'us-east-1', true) >> loadBalancing
    amazonClientProvider.getAmazonElasticLoadBalancing(prodCredentials, 'eu-west-1', true) >> targetLoadBalancing
    regionScopedProviderFactory.forRegion(prodCredentials, 'eu-west-1') >> regionProvider
    regionProvider.getSubnetAnalyzer() >> subnetAnalyzer
    1 * loadBalancing.describeLoadBalancers(_) >> new DescribeLoadBalancersResult().withLoadBalancerDescriptions(sourceDescription)
    1 * targetLoadBalancing.createLoadBalancer(_) >> new CreateLoadBalancerResult().withDNSName('new-elb-dns')
    1 * targetLoadBalancing.configureHealthCheck(_)
    1 * loadBalancing.describeLoadBalancerAttributes(_) >> new DescribeLoadBalancerAttributesResult()
    1 * loadBalancing.describeLoadBalancerPolicies(_) >> new DescribeLoadBalancerPoliciesResult()
    1 * targetLoadBalancing.describeLoadBalancerPolicies(_) >> new DescribeLoadBalancerPoliciesResult()
    2 * targetLookup.getSecurityGroupByName(prodCredentials.name, 'classic-link', 'vpc-2') >> Optional.of(new SecurityGroupUpdater(classicGroup, amazonEC2))
    1 * targetLookup.getSecurityGroupById(prodCredentials.name, 'sg-4', 'vpc-2') >> Optional.of(new SecurityGroupUpdater(elbGroup, amazonEC2))
    1 * targetLookup.getSecurityGroupById(prodCredentials.name, 'sg-3', 'vpc-2') >> Optional.of(new SecurityGroupUpdater(appGroup, amazonEC2))
    1 * targetLookup.createSecurityGroup({ it.name == 'app' && it.vpcId == 'vpc-2' && it.credentials == prodCredentials}) >> new SecurityGroupUpdater(appGroup, amazonEC2)
    1 * targetLookup.createSecurityGroup({ it.name == 'app-elb' && it.vpcId == 'vpc-2' && it.credentials == prodCredentials}) >> new SecurityGroupUpdater(elbGroup, amazonEC2)
    1 * amazonEC2.describeSecurityGroups({r -> r.groupIds == ['sg-3']}) >> new DescribeSecurityGroupsResult().withSecurityGroups([appGroup])
    1 * amazonEC2.describeSecurityGroups({r -> r.filters[0].values == ['app', 'app-elb']}) >> new DescribeSecurityGroupsResult().withSecurityGroups([])
    1 * amazonEC2.describeVpcs(_) >> new DescribeVpcsResult().withVpcs(new Vpc())
    1 * amazonEC2.authorizeSecurityGroupIngress({r -> r.groupId == 'sg-4' &&
      !r.ipPermissions.empty &&
      r.ipPermissions[0].userIdGroupPairs &&
      r.ipPermissions[0].userIdGroupPairs[0].groupId == 'sg-5' &&
      r.ipPermissions[0].fromPort == 80 &&
      r.ipPermissions[0].toPort == 65535 &&
      r.ipPermissions[0].ipProtocol == 'tcp'})
    1 * amazonEC2.authorizeSecurityGroupIngress({r -> r.groupId == 'sg-3' &&
      !r.ipPermissions.empty &&
      r.ipPermissions[0].userIdGroupPairs &&
      r.ipPermissions[0].userIdGroupPairs[0].groupId == 'sg-5' &&
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

  void 'uses supplied target name if present'() {
    LoadBalancerDescription sourceDescription = new LoadBalancerDescription(loadBalancerName: 'app-elb',
      healthCheck: new HealthCheck(),
      listenerDescriptions: []
    )
    LoadBalancerLocation source = new LoadBalancerLocation(credentials: testCredentials, region: 'us-east-1', name: 'app-elb')
    TargetLoadBalancerLocation target = new TargetLoadBalancerLocation(credentials: testCredentials, region: 'us-east-1', availabilityZones: ['us-east-1a'], name: 'newapp-elb')

    AmazonEC2 amazonEC2 = Mock(AmazonEC2)
    AmazonElasticLoadBalancing loadBalancing = Mock(AmazonElasticLoadBalancing)
    RegionScopedProvider regionProvider = Mock(RegionScopedProvider)

    when:
    strategy.generateResults(sourceLookup, targetLookup, securityGroupStrategy, source, target, null, 'app', false, false)

    then:
    amazonClientProvider.getAmazonEC2(testCredentials, 'us-east-1', true) >> amazonEC2
    amazonClientProvider.getAmazonEC2(testCredentials, 'us-east-1') >> amazonEC2
    amazonClientProvider.getAmazonElasticLoadBalancing(testCredentials, 'us-east-1', true) >> loadBalancing
    regionScopedProviderFactory.forRegion(testCredentials, 'us-east-1') >> regionProvider
    1 * loadBalancing.describeLoadBalancers({ it.loadBalancerNames == ['app-elb']}) >> new DescribeLoadBalancersResult().withLoadBalancerDescriptions(sourceDescription)
    1 * loadBalancing.describeLoadBalancers({ it.loadBalancerNames == ['newapp-elb']}) >> new DescribeLoadBalancersResult()
    1 * loadBalancing.createLoadBalancer({ it.loadBalancerName == 'newapp-elb'}) >> new CreateLoadBalancerResult().withDNSName('new-elb-dns')
    1 * loadBalancing.configureHealthCheck(_)
    1 * loadBalancing.describeLoadBalancerAttributes(_) >> new DescribeLoadBalancerAttributesResult()
    2 * loadBalancing.describeLoadBalancerPolicies(_) >> new DescribeLoadBalancerPoliciesResult()
    0 * amazonEC2.authorizeSecurityGroupIngress(_)
  }

  void 'generates target name, removing old suffixes'() {
    LoadBalancerDescription sourceDescription = new LoadBalancerDescription(loadBalancerName: 'app-elb',
      healthCheck: new HealthCheck(),
      listenerDescriptions: []
    )
    LoadBalancerLocation source = new LoadBalancerLocation(credentials: testCredentials, region: 'us-east-1', name: 'app-elb-frontend')
    TargetLoadBalancerLocation target = new TargetLoadBalancerLocation(credentials: testCredentials, region: 'us-east-1', availabilityZones: ['us-east-1a'])

    AmazonEC2 amazonEC2 = Mock(AmazonEC2)
    AmazonElasticLoadBalancing loadBalancing = Mock(AmazonElasticLoadBalancing)
    RegionScopedProvider regionProvider = Mock(RegionScopedProvider)

    when:
    strategy.generateResults(sourceLookup, targetLookup, securityGroupStrategy, source, target, null, 'app', false, false)

    then:
    amazonClientProvider.getAmazonEC2(testCredentials, 'us-east-1', true) >> amazonEC2
    amazonClientProvider.getAmazonEC2(testCredentials, 'us-east-1') >> amazonEC2
    amazonClientProvider.getAmazonElasticLoadBalancing(testCredentials, 'us-east-1', true) >> loadBalancing
    regionScopedProviderFactory.forRegion(testCredentials, 'us-east-1') >> regionProvider
    1 * loadBalancing.describeLoadBalancers({ it.loadBalancerNames == ['app-elb-frontend']}) >> new DescribeLoadBalancersResult().withLoadBalancerDescriptions(sourceDescription)
    1 * loadBalancing.describeLoadBalancers({ it.loadBalancerNames == ['app-elb']}) >> new DescribeLoadBalancersResult()
    1 * loadBalancing.createLoadBalancer({ it.loadBalancerName == 'app-elb'}) >> new CreateLoadBalancerResult().withDNSName('new-elb-dns')
    1 * loadBalancing.configureHealthCheck(_)
    1 * loadBalancing.describeLoadBalancerAttributes(_) >> new DescribeLoadBalancerAttributesResult()
    2 * loadBalancing.describeLoadBalancerPolicies(_) >> new DescribeLoadBalancerPoliciesResult()
    0 * amazonEC2.authorizeSecurityGroupIngress(_)
  }

  void 'applies health check properties to new load balancer'() {
    LoadBalancerDescription sourceDescription = new LoadBalancerDescription(loadBalancerName: 'app-elb',
      healthCheck: new HealthCheck(target: 'the-target', healthyThreshold: 3, unhealthyThreshold: 4, timeout: 5, interval: 6, ),
      listenerDescriptions: []
    )
    LoadBalancerLocation source = new LoadBalancerLocation(credentials: testCredentials, region: 'us-east-1', name: 'app-elb')
    TargetLoadBalancerLocation target = new TargetLoadBalancerLocation(credentials: testCredentials, region: 'us-west-1', availabilityZones: ['us-east-1a'])

    AmazonEC2 amazonEC2 = Mock(AmazonEC2)
    AmazonElasticLoadBalancing loadBalancing = Mock(AmazonElasticLoadBalancing)
    AmazonElasticLoadBalancing targetLoadBalancing = Mock(AmazonElasticLoadBalancing)
    RegionScopedProvider regionProvider = Mock(RegionScopedProvider)

    when:
    strategy.generateResults(sourceLookup, targetLookup, securityGroupStrategy, source, target, null, 'app', false, false)

    then:
    amazonClientProvider.getAmazonEC2(testCredentials, 'us-east-1', true) >> amazonEC2
    amazonClientProvider.getAmazonEC2(testCredentials, 'us-east-1') >> amazonEC2
    amazonClientProvider.getAmazonElasticLoadBalancing(testCredentials, 'us-east-1', true) >> loadBalancing
    amazonClientProvider.getAmazonElasticLoadBalancing(testCredentials, 'us-west-1', true) >> targetLoadBalancing
    regionScopedProviderFactory.forRegion(testCredentials, 'us-east-1') >> regionProvider
    1 * loadBalancing.describeLoadBalancers({ it.loadBalancerNames == ['app-elb']}) >> new DescribeLoadBalancersResult().withLoadBalancerDescriptions(sourceDescription)
    1 * targetLoadBalancing.describeLoadBalancers({ it.loadBalancerNames == ['app-elb']}) >> new DescribeLoadBalancersResult()
    1 * targetLoadBalancing.createLoadBalancer({ it.loadBalancerName == 'app-elb'}) >> new CreateLoadBalancerResult().withDNSName('new-elb-dns')
    1 * targetLoadBalancing.configureHealthCheck({ it.loadBalancerName == 'app-elb' &&
      it.healthCheck.target == 'the-target' &&
      it.healthCheck.interval == 6 &&
      it.healthCheck.healthyThreshold == 3 &&
      it.healthCheck.unhealthyThreshold == 4 &&
      it.healthCheck.timeout == 5
    })
    1 * loadBalancing.describeLoadBalancerAttributes(_) >> new DescribeLoadBalancerAttributesResult()
    1 * loadBalancing.describeLoadBalancerPolicies(_) >> new DescribeLoadBalancerPoliciesResult()
    1 * targetLoadBalancing.describeLoadBalancerPolicies(_) >> new DescribeLoadBalancerPoliciesResult()
  }

  void 'applies load balancer policies to new load balancer'() {
    given:
    LoadBalancerDescription sourceDescription = new LoadBalancerDescription(loadBalancerName: 'app-elb',
      healthCheck: new HealthCheck(),
      listenerDescriptions: [
        new ListenerDescription().withListener(
          new Listener().withLoadBalancerPort(443).withInstancePort(7000)).withPolicyNames("custom-policy")
      ]
    )

    AmazonElasticLoadBalancing loadBalancing = Mock(AmazonElasticLoadBalancing)

    when:
    strategy.applyListenerPolicies(loadBalancing, loadBalancing, sourceDescription, 'app-elb-vpc1')

    then:
    1 * loadBalancing.describeLoadBalancerPolicies({ it.loadBalancerName == 'app-elb'}) >> new DescribeLoadBalancerPoliciesResult()
      .withPolicyDescriptions(new PolicyDescription(policyName: 'custom-policy'))
    1 * loadBalancing.describeLoadBalancerPolicies({ it.loadBalancerName == 'app-elb-vpc1'}) >> new DescribeLoadBalancerPoliciesResult()
    1 * loadBalancing.createLoadBalancerPolicy({ it.loadBalancerName == 'app-elb-vpc1' && it.policyName == 'custom-policy'})
    1 * loadBalancing.setLoadBalancerPoliciesOfListener({
      it.loadBalancerName == 'app-elb-vpc1' && it.loadBalancerPort == 443 && it.policyNames == ['custom-policy']})

  }

  void 'updates load balancer policies on existing load balancer to match source load balancer'() {
    given:
    LoadBalancerDescription sourceDescription = new LoadBalancerDescription(loadBalancerName: 'app-elb',
      healthCheck: new HealthCheck(),
      listenerDescriptions: [
        new ListenerDescription().withListener(
          new Listener().withLoadBalancerPort(443).withInstancePort(7000)).withPolicyNames("custom-policy")
      ]
    )

    AmazonElasticLoadBalancing loadBalancing = Mock(AmazonElasticLoadBalancing)

    when:
    strategy.applyListenerPolicies(loadBalancing, loadBalancing, sourceDescription, 'app-elb-vpc1')

    then:
    1 * loadBalancing.describeLoadBalancerPolicies({ it.loadBalancerName == 'app-elb'}) >> new DescribeLoadBalancerPoliciesResult()
      .withPolicyDescriptions(new PolicyDescription(policyName: 'custom-policy'))
    1 * loadBalancing.describeLoadBalancerPolicies({ it.loadBalancerName == 'app-elb-vpc1'}) >> new DescribeLoadBalancerPoliciesResult()
    1 * loadBalancing.createLoadBalancerPolicy({ it.loadBalancerName == 'app-elb-vpc1' && it.policyName == 'custom-policy'})
    1 * loadBalancing.setLoadBalancerPoliciesOfListener({
      it.loadBalancerName == 'app-elb-vpc1' && it.loadBalancerPort == 443 && it.policyNames == ['custom-policy']})

  }

  void 'does not try to recreate policies on existing load balancer if they already exist'() {
    given:
    LoadBalancerDescription sourceDescription = new LoadBalancerDescription(loadBalancerName: 'app-elb',
      healthCheck: new HealthCheck(),
      listenerDescriptions: [
        new ListenerDescription().withListener(
          new Listener().withLoadBalancerPort(443).withInstancePort(7000)).withPolicyNames("custom-policy")
      ]
    )
    AmazonElasticLoadBalancing loadBalancing = Mock(AmazonElasticLoadBalancing)

    when:
    strategy.applyListenerPolicies(loadBalancing, loadBalancing, sourceDescription, 'app-elb-vpc1')

    then:
    1 * loadBalancing.describeLoadBalancerPolicies({ it.loadBalancerName == 'app-elb'}) >> new DescribeLoadBalancerPoliciesResult()
      .withPolicyDescriptions(new PolicyDescription(policyName: 'custom-policy'))
    1 * loadBalancing.describeLoadBalancerPolicies({ it.loadBalancerName == 'app-elb-vpc1'}) >> new DescribeLoadBalancerPoliciesResult()
      .withPolicyDescriptions(new PolicyDescription(policyName: 'custom-policy'))
    0 * loadBalancing.createLoadBalancerPolicy(_)
    1 * loadBalancing.setLoadBalancerPoliciesOfListener({
      it.loadBalancerName == 'app-elb-vpc1' && it.loadBalancerPort == 443 && it.policyNames == ['custom-policy']})

  }

  @Unroll
  void 'only adds Reference-Security-Policy attribute if present when creating policy'() {
    given:
    LoadBalancerDescription sourceDescription = new LoadBalancerDescription(loadBalancerName: 'app-elb',
      healthCheck: new HealthCheck(),
      listenerDescriptions: [
        new ListenerDescription().withListener(
          new Listener().withLoadBalancerPort(443).withInstancePort(7000)).withPolicyNames("ref-policy")
      ]
    )
    AmazonElasticLoadBalancing loadBalancing = Mock(AmazonElasticLoadBalancing)

    when:
    strategy.applyListenerPolicies(loadBalancing, loadBalancing, sourceDescription, 'app-elb-vpc1')

    then:
    1 * loadBalancing.describeLoadBalancerPolicies({ it.loadBalancerName == 'app-elb'}) >> new DescribeLoadBalancerPoliciesResult()
      .withPolicyDescriptions(new PolicyDescription(policyName: 'ref-policy')
        .withPolicyAttributeDescriptions(
      attributes.findResults { new PolicyAttributeDescription(attributeName: it, attributeValue: it + "-v") }
    ))
    1 * loadBalancing.describeLoadBalancerPolicies({ it.loadBalancerName == 'app-elb-vpc1'}) >> new DescribeLoadBalancerPoliciesResult()
      .withPolicyDescriptions(new PolicyDescription(policyName: 'other-policy'))
    1 * loadBalancing.createLoadBalancerPolicy({ it.policyAttributes.attributeName == requestAttributes})
    1 * loadBalancing.setLoadBalancerPoliciesOfListener({
      it.loadBalancerName == 'app-elb-vpc1' && it.loadBalancerPort == 443 && it.policyNames == ['ref-policy']})

    where:
    attributes                               || requestAttributes
    ["Reference-Security-Policy", "cipher1"] || ["Reference-Security-Policy"]
    ["cipher1", "cipher2"]                   || ["cipher1", "cipher2"]
  }

  @Unroll
  void 'prefixes policy name if reserved'() {
    given:
    LoadBalancerDescription sourceDescription = new LoadBalancerDescription(loadBalancerName: 'app-elb',
      healthCheck: new HealthCheck(),
      listenerDescriptions: [
        new ListenerDescription().withListener(
          new Listener().withLoadBalancerPort(443).withInstancePort(7000)).withPolicyNames(policyName)
      ]
    )
    AmazonElasticLoadBalancing loadBalancing = Mock(AmazonElasticLoadBalancing)

    when:
    strategy.applyListenerPolicies(loadBalancing, loadBalancing, sourceDescription, 'app-elb-vpc1')

    then:
    1 * loadBalancing.describeLoadBalancerPolicies({ it.loadBalancerName == 'app-elb'}) >> new DescribeLoadBalancerPoliciesResult()
      .withPolicyDescriptions(new PolicyDescription(policyName: policyName).withPolicyAttributeDescriptions(
      new PolicyAttributeDescription(attributeName: 'some-cipher', attributeValue: 'some-value')
    ))
    1 * loadBalancing.describeLoadBalancerPolicies({ it.loadBalancerName == 'app-elb-vpc1'}) >> new DescribeLoadBalancerPoliciesResult()
      .withPolicyDescriptions(new PolicyDescription(policyName: policyName))
    1 * loadBalancing.createLoadBalancerPolicy({ it.policyName == newName})
    1 * loadBalancing.setLoadBalancerPoliciesOfListener({
      it.loadBalancerName == 'app-elb-vpc1' && it.loadBalancerPort == 443 && it.policyNames == [newName]})

    where:
    policyName            || newName
    'not-reserved'        || 'not-reserved'
    'ELBSecurityPolicy-1' || 'migrated-ELBSecurityPolicy-1'
    'ELBSample-1'         || 'migrated-ELBSample-1'
  }

  void 'reuses policies when attributes match'() {
    given:
    LoadBalancerDescription sourceDescription = new LoadBalancerDescription(loadBalancerName: 'app-elb',
      healthCheck: new HealthCheck(),
      listenerDescriptions: [
        new ListenerDescription().withListener(
          new Listener().withLoadBalancerPort(443).withInstancePort(7000)).withPolicyNames("custom-policy")
      ]
    )
    AmazonElasticLoadBalancing loadBalancing = Mock(AmazonElasticLoadBalancing)

    when:
    strategy.applyListenerPolicies(loadBalancing, loadBalancing, sourceDescription, 'app-elb-vpc1')

    then:
    1 * loadBalancing.describeLoadBalancerPolicies({ it.loadBalancerName == 'app-elb'}) >> new DescribeLoadBalancerPoliciesResult()
      .withPolicyDescriptions(new PolicyDescription(policyName: 'custom-policy').withPolicyAttributeDescriptions(
      new PolicyAttributeDescription(attributeName: 'some-attr', attributeValue: 'some-val')
    ))
    1 * loadBalancing.describeLoadBalancerPolicies({ it.loadBalancerName == 'app-elb-vpc1'}) >> new DescribeLoadBalancerPoliciesResult()
      .withPolicyDescriptions(new PolicyDescription(policyName: 'custom-policy-vpc').withPolicyAttributeDescriptions(
        new PolicyAttributeDescription(attributeName: 'some-attr', attributeValue: 'some-val')
    ))
    0 * loadBalancing.createLoadBalancerPolicy(_)
    1 * loadBalancing.setLoadBalancerPoliciesOfListener({
      it.loadBalancerName == 'app-elb-vpc1' && it.loadBalancerPort == 443 && it.policyNames == ['custom-policy-vpc']})
  }

  void 'sets cross-zone load balancing flag when legacy listener is present'() {
    given:
    LoadBalancerDescription sourceDescription = new LoadBalancerDescription(loadBalancerName: 'app-elb',
      healthCheck: new HealthCheck(),
      listenerDescriptions: [
        new ListenerDescription().withListener(new Listener().withLoadBalancerPort(0).withInstancePort(0))
      ]
    )
    AmazonElasticLoadBalancing client = Mock(AmazonElasticLoadBalancing)

    when:
    def attributes = strategy.getLoadBalancerAttributes(sourceDescription, client)

    then:
    1 * client.describeLoadBalancerAttributes(_) >> new DescribeLoadBalancerAttributesResult()
      .withLoadBalancerAttributes(new LoadBalancerAttributes()
        .withCrossZoneLoadBalancing(new CrossZoneLoadBalancing().withEnabled(false)))

    attributes.crossZoneLoadBalancing.isEnabled()
  }

  void 'ignores legacy listeners when generating listener lists'() {
    given:
    LoadBalancerDescription sourceDescription = new LoadBalancerDescription(loadBalancerName: 'app-elb',
      healthCheck: new HealthCheck(),
      listenerDescriptions: [
        new ListenerDescription().withListener(new Listener().withLoadBalancerPort(443).withInstancePort(7000)),
        new ListenerDescription().withListener(new Listener().withLoadBalancerPort(0).withInstancePort(0))
      ]
    )
    MigrateLoadBalancerResult result = new MigrateLoadBalancerResult()

    when:
    def listeners = strategy.getListeners(sourceDescription, result)

    then:
    listeners.size() == 1
    listeners.instancePort == [7000]
  }

}
