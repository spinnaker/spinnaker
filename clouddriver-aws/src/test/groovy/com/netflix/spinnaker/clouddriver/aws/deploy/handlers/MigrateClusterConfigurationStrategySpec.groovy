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

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.SecurityGroup
import com.netflix.spinnaker.config.AwsConfiguration.DeployDefaults
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.MigrateLoadBalancerResult
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.MigrateSecurityGroupReference
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.MigrateSecurityGroupResult
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupLookup
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupUpdater
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.servergroup.ClusterConfigurationMigrator.ClusterConfiguration
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.servergroup.ClusterConfigurationMigrator.ClusterConfigurationTarget
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class MigrateClusterConfigurationStrategySpec extends Specification {

  @Subject
  MigrateClusterConfigurationStrategy strategy

  @Shared
  NetflixAmazonCredentials testCredentials = TestCredential.named('test')

  @Shared
  NetflixAmazonCredentials prodCredentials = TestCredential.named('prod')

  AmazonClientProvider amazonClientProvider = Mock(AmazonClientProvider)

  RegionScopedProviderFactory regionScopedProviderFactory = Mock(RegionScopedProviderFactory)

  DeployDefaults deployDefaults = Mock(DeployDefaults)

  MigrateSecurityGroupStrategy migrateSecurityGroupStrategy = Mock(MigrateSecurityGroupStrategy)

  MigrateLoadBalancerStrategy migrateLoadBalancerStrategy = Mock(MigrateLoadBalancerStrategy)

  SecurityGroupLookup sourceLookup = Mock(SecurityGroupLookup)

  SecurityGroupLookup targetLookup = Mock(SecurityGroupLookup)

  AmazonEC2 amazonEC2 = Mock(AmazonEC2)

  void setup() {
    TaskRepository.threadLocalTask.set(Stub(Task))
    strategy = new DefaultMigrateClusterConfigurationStrategy(amazonClientProvider,
      regionScopedProviderFactory,
      deployDefaults)
  }

  void 'sets availability zones, subnetType, iamRole, keyPair on target'() {
    given:
    ClusterConfigurationTarget target = new ClusterConfigurationTarget(credentials: prodCredentials, vpcId: 'vpc-2', region: 'eu-west-1', availabilityZones: ['eu-west-1b'])
    Map cluster = [
      loadBalancers : [],
      securityGroups: [],
      region: 'us-east-1',
      availabilityZones: [ 'us-east-1': ['us-east-1c']]
    ]
    ClusterConfiguration source = new ClusterConfiguration(credentials: testCredentials, cluster: cluster)

    when:
    def results = strategy.generateResults(source, target, sourceLookup, targetLookup,
      migrateLoadBalancerStrategy, migrateSecurityGroupStrategy, 'external', 'external', 'newIamRole', 'newKeyPair', [:], false, true)

    then:
    results.loadBalancerMigrations.empty
    results.securityGroupMigrations.empty
    results.cluster.subnetType == 'external'
    results.cluster.iamRole == 'newIamRole'
    results.cluster.keyPair == 'newKeyPair'
    results.cluster.loadBalancers == []
    results.cluster.securityGroups == []
    results.cluster.availabilityZones == [ 'eu-west-1': ['eu-west-1b']]
    1 * deployDefaults.getAddAppGroupToServerGroup() >> false
    0 * _
  }

  void 'generates load balancers from config'() {
    given:
    ClusterConfigurationTarget target = new ClusterConfigurationTarget(credentials: prodCredentials, vpcId: 'vpc-2', region: 'eu-west-1', availabilityZones: ['eu-west-1b'])
    Map cluster = [
      loadBalancers : ['lb-a', 'lb-b'],
      securityGroups: [],
      region: 'us-east-1',
      availabilityZones: [ 'us-east-1': ['us-east-1c']]
    ]
    ClusterConfiguration source = new ClusterConfiguration(credentials: testCredentials, cluster: cluster)

    when:
    def results = strategy.generateResults(source, target, sourceLookup, targetLookup,
      migrateLoadBalancerStrategy, migrateSecurityGroupStrategy, null, null, null, null, [:], false, true)

    then:
    results.loadBalancerMigrations.size() == 2
    results.cluster.loadBalancers == ['lb-a2', 'lb-b2']
    1 * migrateLoadBalancerStrategy.generateResults(sourceLookup, targetLookup, migrateSecurityGroupStrategy,
      { it.name == 'lb-a' && it.region == 'us-east-1'}, { it.credentials == prodCredentials && it.region == 'eu-west-1'}, null, null, false, true) >> new MigrateLoadBalancerResult(targetName: 'lb-a2')
    1 * migrateLoadBalancerStrategy.generateResults(sourceLookup, targetLookup, migrateSecurityGroupStrategy,
      { it.name == 'lb-b' && it.region == 'us-east-1'}, { it.credentials == prodCredentials && it.region == 'eu-west-1'}, null, null, false, true) >> new MigrateLoadBalancerResult(targetName: 'lb-b2')
    1 * deployDefaults.getAddAppGroupToServerGroup() >> false
    0 * _
  }

  void 'handles missing loadBalancers key'() {
    given:
    ClusterConfigurationTarget target = new ClusterConfigurationTarget(credentials: prodCredentials, vpcId: 'vpc-2', region: 'eu-west-1', availabilityZones: ['eu-west-1b'])
    Map cluster = [
      securityGroups: [],
      region: 'us-east-1',
      availabilityZones: [ 'us-east-1': ['us-east-1c']]
    ]
    ClusterConfiguration source = new ClusterConfiguration(credentials: testCredentials, cluster: cluster)

    when:
    def results = strategy.generateResults(source, target, sourceLookup, targetLookup,
      migrateLoadBalancerStrategy, migrateSecurityGroupStrategy, null, null, null, null, [:], false, true)

    then:
    results.loadBalancerMigrations.size() == 0
    results.cluster.loadBalancers == []
  }

  void 'generates security groups from config, omitting skipped ones'() {
    given:
    ClusterConfigurationTarget target = new ClusterConfigurationTarget(credentials: prodCredentials, vpcId: 'vpc-2', region: 'eu-west-1', availabilityZones: ['eu-west-1b'])
    Map cluster = [
      loadBalancers : [],
      securityGroups: ['sg-1', 'sg-2', 'sg-3'],
      region: 'us-east-1',
      availabilityZones: [ 'us-east-1': ['us-east-1c']]
    ]
    ClusterConfiguration source = new ClusterConfiguration(credentials: testCredentials, cluster: cluster)

    SecurityGroup group1 = new SecurityGroup(groupId: 'sg-1a', groupName: 'group1', vpcId: 'vpc-1')
    SecurityGroup group2 = new SecurityGroup(groupId: 'sg-2a', groupName: 'group2', vpcId: 'vpc-1')
    SecurityGroup skippedGroup = new SecurityGroup(groupId: 'sg-3a', groupName: 'group3', vpcId: 'vpc-1')
    SecurityGroupUpdater updater1 = Stub(SecurityGroupUpdater) {
      getSecurityGroup() >> group1
    }
    SecurityGroupUpdater updater2 = Stub(SecurityGroupUpdater) {
      getSecurityGroup() >> group2
    }
    SecurityGroupUpdater skipper = Stub(SecurityGroupUpdater) {
      getSecurityGroup() >> skippedGroup
    }
    MigrateSecurityGroupReference skippedReference = new MigrateSecurityGroupReference()

    when:
    def results = strategy.generateResults(source, target, sourceLookup, targetLookup,
      migrateLoadBalancerStrategy, migrateSecurityGroupStrategy, null, null, null, null, [:], false, false)

    then:
    results.securityGroupMigrations.size() == 3
    results.cluster.securityGroups == ['sg-1a', 'sg-2a']

    3 * sourceLookup.getSecurityGroupById('test', 'sg-1', null) >> Optional.of(updater1)
    3 * sourceLookup.getSecurityGroupById('test', 'sg-2', null) >> Optional.of(updater2)
    3 * sourceLookup.getSecurityGroupById('test', 'sg-3', null) >> Optional.of(skipper)
    1 * migrateSecurityGroupStrategy.generateResults({it.name == 'group1'}, { it.region == 'eu-west-1' }, sourceLookup, targetLookup, false, false) >> new MigrateSecurityGroupResult(target: new MigrateSecurityGroupReference(targetId: 'sg-1a'))
    1 * migrateSecurityGroupStrategy.generateResults({it.name == 'group2'}, { it.region == 'eu-west-1' }, sourceLookup, targetLookup, false, false) >> new MigrateSecurityGroupResult(target: new MigrateSecurityGroupReference(targetId: 'sg-2a'))
    1 * migrateSecurityGroupStrategy.generateResults({it.name == 'group3'}, { it.region == 'eu-west-1' }, sourceLookup, targetLookup, false, false) >> new MigrateSecurityGroupResult(target: skippedReference, skipped: [skippedReference])
    1 * deployDefaults.getAddAppGroupToServerGroup() >> false
    0 * _
  }

  void 'adds app security group if configured in deployDefaults'() {
    given:
    ClusterConfigurationTarget target = new ClusterConfigurationTarget(credentials: prodCredentials, vpcId: 'vpc-2', region: 'eu-west-1')
    Map cluster = [
      application: 'theapp',
      loadBalancers : [],
      securityGroups: [],
      region: 'us-east-1',
      availabilityZones: [ 'us-east-1': ['us-east-1c']]
    ]
    ClusterConfiguration source = new ClusterConfiguration(credentials: testCredentials, cluster: cluster)

    when:
    def results = strategy.generateResults(source, target, sourceLookup, targetLookup,
      migrateLoadBalancerStrategy, migrateSecurityGroupStrategy, null, null, null, null, [:], false, false)

    then:
    results.cluster.securityGroups == ['sg-1a']
    1 * deployDefaults.getAddAppGroupToServerGroup() >> true
    1 * migrateSecurityGroupStrategy.generateResults(
      {s -> s.name == 'theapp' && s.region == 'us-east-1' && s.credentials == testCredentials},
      {s -> s.region == 'eu-west-1' && s.credentials == prodCredentials},
      sourceLookup, targetLookup, true, false) >> new MigrateSecurityGroupResult(target: new MigrateSecurityGroupReference(targetId: 'sg-1a'))
    0 * _
  }

  void 'replaces app security group if it is already there'() {
    given:
    ClusterConfigurationTarget target = new ClusterConfigurationTarget(credentials: prodCredentials, vpcId: 'vpc-2', region: 'eu-west-1')
    Map cluster = [
      application: 'theapp',
      loadBalancers : [],
      securityGroups: ['sg-1'],
      region: 'us-east-1',
      availabilityZones: [ 'us-east-1': ['us-east-1c']]
    ]
    ClusterConfiguration source = new ClusterConfiguration(credentials: testCredentials, cluster: cluster)

    SecurityGroup appGroup = new SecurityGroup(groupId: 'sg-1', groupName: 'theapp', vpcId: 'vpc-1')
    SecurityGroupUpdater updater = Stub(SecurityGroupUpdater) {
      getSecurityGroup() >> appGroup
    }

    when:
    def results = strategy.generateResults(source, target, sourceLookup, targetLookup,
      migrateLoadBalancerStrategy, migrateSecurityGroupStrategy, null, null, null, null, [:], false, true)

    then:
    results.cluster.securityGroups == ['sg-1a']
    3 * sourceLookup.getSecurityGroupById('test', 'sg-1', null) >> Optional.of(updater)
    1 * deployDefaults.getAddAppGroupToServerGroup() >> true
    1 * migrateSecurityGroupStrategy.generateResults(
      {s -> s.name == 'theapp' && s.region == 'us-east-1' && s.credentials == testCredentials},
      {s -> s.region == 'eu-west-1' && s.credentials == prodCredentials},
      sourceLookup, targetLookup, false, true) >> new MigrateSecurityGroupResult(target: new MigrateSecurityGroupReference(targetId: 'sg-1a', targetName: 'theapp'))
    0 * _
  }
}
