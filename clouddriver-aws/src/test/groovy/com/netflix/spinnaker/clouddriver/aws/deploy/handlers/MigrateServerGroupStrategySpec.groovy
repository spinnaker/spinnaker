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

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult
import com.amazonaws.services.autoscaling.model.InstanceMonitoring
import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.amazonaws.services.autoscaling.model.SuspendedProcess
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.SecurityGroup
import com.netflix.spinnaker.config.AwsConfiguration.DeployDefaults
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.AWSServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.aws.deploy.converters.AllowLaunchAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.AllowLaunchAtomicOperation
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.MigrateLoadBalancerResult
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.MigrateSecurityGroupReference
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.MigrateSecurityGroupResult
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupLookup
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupUpdater
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.servergroup.ServerGroupMigrator.ServerGroupLocation
import com.netflix.spinnaker.clouddriver.aws.deploy.validators.BasicAmazonDeployDescriptionValidator
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.services.AsgService
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory.RegionScopedProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class MigrateServerGroupStrategySpec extends Specification {

  @Subject
  MigrateServerGroupStrategy strategy

  @Shared
  NetflixAmazonCredentials testCredentials = TestCredential.named('test')

  @Shared
  NetflixAmazonCredentials prodCredentials = TestCredential.named('prod')

  AmazonClientProvider amazonClientProvider = Mock(AmazonClientProvider)

  RegionScopedProviderFactory regionScopedProviderFactory = Mock(RegionScopedProviderFactory)

  DeployDefaults deployDefaults = Mock(DeployDefaults)

  MigrateSecurityGroupStrategy migrateSecurityGroupStrategy = Mock(MigrateSecurityGroupStrategy)

  MigrateLoadBalancerStrategy migrateLoadBalancerStrategy = Mock(MigrateLoadBalancerStrategy)

  BasicAmazonDeployHandler basicAmazonDeployHandler = Mock(BasicAmazonDeployHandler)

  SecurityGroupLookup sourceLookup = Mock(SecurityGroupLookup)

  SecurityGroupLookup targetLookup = Mock(SecurityGroupLookup)

  AmazonEC2 amazonEC2 = Mock(AmazonEC2)

  AmazonAutoScaling amazonAutoScaling = Mock(AmazonAutoScaling)

  AsgService asgService = Mock(AsgService)

  BasicAmazonDeployDescriptionValidator validator = Stub(BasicAmazonDeployDescriptionValidator)

  AllowLaunchAtomicOperationConverter allowLaunchAtomicOperationConverter = Mock(AllowLaunchAtomicOperationConverter)

  AllowLaunchAtomicOperation allowLaunchOperation = Mock(AllowLaunchAtomicOperation)

  void setup() {
    TaskRepository.threadLocalTask.set(Stub(Task))
    strategy = new DefaultMigrateServerGroupStrategy(amazonClientProvider, basicAmazonDeployHandler,
      regionScopedProviderFactory, validator, allowLaunchAtomicOperationConverter, deployDefaults)
  }

  void 'generates load balancers from launch config'() {
    given:
    ServerGroupLocation source = new ServerGroupLocation(name: 'asg-v001', credentials: testCredentials, vpcId: 'vpc-1', region: 'us-east-1')
    ServerGroupLocation target = new ServerGroupLocation(credentials: prodCredentials, vpcId: 'vpc-2', region: 'eu-west-1', availabilityZones: ['eu-west-1b'])

    AutoScalingGroup asg = new AutoScalingGroup(launchConfigurationName: 'asg-v001-lc', loadBalancerNames: ['lb-1'])
    LaunchConfiguration lc = new LaunchConfiguration(
      instanceMonitoring: new InstanceMonitoring(enabled: false),
    )
    AWSServerGroupNameResolver nameResolver = Mock(AWSServerGroupNameResolver)
    RegionScopedProvider regionScopedProvider = Stub(RegionScopedProvider) {
      getAsgService() >> asgService
      getAWSServerGroupNameResolver() >> nameResolver
    }

    when:
    strategy.generateResults(source, target, sourceLookup, targetLookup,
      migrateLoadBalancerStrategy, migrateSecurityGroupStrategy, 'internal', 'external', null, null, null, [:], false, false)

    then:
    amazonClientProvider.getAutoScaling(testCredentials, 'us-east-1', true) >> amazonAutoScaling
    amazonAutoScaling.describeAutoScalingGroups() >> new DescribeAutoScalingGroupsResult().withAutoScalingGroups(asg)
    regionScopedProviderFactory.forRegion(testCredentials, 'us-east-1') >> regionScopedProvider
    regionScopedProviderFactory.forRegion(prodCredentials, 'eu-west-1') >> regionScopedProvider
    1 * asgService.getAutoScalingGroup('asg-v001') >> asg
    1 * asgService.getLaunchConfiguration('asg-v001-lc') >> lc
    1 * deployDefaults.getAddAppGroupToServerGroup() >> false
    1 * allowLaunchAtomicOperationConverter.convertOperation(_) >> allowLaunchOperation
    1 * allowLaunchOperation.operate(null)
    1 * migrateLoadBalancerStrategy.generateResults(sourceLookup, targetLookup,
      migrateSecurityGroupStrategy,
      {s -> s.name == 'lb-1' && s.region == 'us-east-1' && s.credentials == testCredentials && s.vpcId == 'vpc-1'},
      {s -> !s.name && s.region == 'eu-west-1' && s.credentials == prodCredentials && s.vpcId == 'vpc-2' && s.availabilityZones == ['eu-west-1b']},
      'external', _, false, false) >> new MigrateLoadBalancerResult()
    1 * basicAmazonDeployHandler.copySourceAttributes(regionScopedProvider, 'asg-v001', false, _) >> { a, b, c, d -> d }
    1 * basicAmazonDeployHandler.handle(_, []) >> new DeploymentResult(serverGroupNames: ['asg-v003'])
    0 * _
  }

  void 'generates security groups from launch config, filtering skipped ones'() {
    ServerGroupLocation source = new ServerGroupLocation(name: 'asg-v001', credentials: testCredentials, vpcId: 'vpc-1', region: 'us-east-1')
    ServerGroupLocation target = new ServerGroupLocation(credentials: prodCredentials, vpcId: 'vpc-2', region: 'eu-west-1')
    SecurityGroup group1 = new SecurityGroup(groupId: 'sg-1', groupName: 'group1', vpcId: 'vpc-1')
    SecurityGroup group2 = new SecurityGroup(groupId: 'sg-2', groupName: 'group2', vpcId: 'vpc-1')
    SecurityGroup skippedGroup = new SecurityGroup(groupId: 'sg-3', groupName: 'group3', vpcId: 'vpc-1')
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

    AutoScalingGroup asg = new AutoScalingGroup(launchConfigurationName: 'asg-v001-lc')
    LaunchConfiguration lc = new LaunchConfiguration(
      instanceMonitoring: new InstanceMonitoring(enabled: false),
      securityGroups: [ 'sg-1', 'sg-2', 'sg-3' ]
    )
    RegionScopedProvider regionScopedProvider = Stub(RegionScopedProvider) {
      getAsgService() >> asgService
    }

    when:
    strategy.generateResults(source, target, sourceLookup, targetLookup,
      migrateLoadBalancerStrategy, migrateSecurityGroupStrategy, 'internal', 'external', null, null, null, [:], false, false)

    then:
    amazonClientProvider.getAutoScaling(testCredentials, 'us-east-1', true) >> amazonAutoScaling
    amazonAutoScaling.describeAutoScalingGroups() >> new DescribeAutoScalingGroupsResult().withAutoScalingGroups(asg)
    regionScopedProviderFactory.forRegion(testCredentials, 'us-east-1') >> regionScopedProvider
    regionScopedProviderFactory.forRegion(prodCredentials, 'eu-west-1') >> regionScopedProvider
    3 * sourceLookup.getSecurityGroupById('test', 'sg-1', 'vpc-1') >> Optional.of(updater1)
    3 * sourceLookup.getSecurityGroupById('test', 'sg-2', 'vpc-1') >> Optional.of(updater2)
    3 * sourceLookup.getSecurityGroupById('test', 'sg-3', 'vpc-1') >> Optional.of(skipper)
    1 * asgService.getAutoScalingGroup('asg-v001') >> asg
    1 * asgService.getLaunchConfiguration('asg-v001-lc') >> lc
    1 * deployDefaults.getAddAppGroupToServerGroup() >> false
    1 * allowLaunchAtomicOperationConverter.convertOperation(_) >> allowLaunchOperation
    1 * allowLaunchOperation.operate(null)
    1 * migrateSecurityGroupStrategy.generateResults(
      {s -> s.name == 'group1' && s.region == 'us-east-1' && s.credentials == testCredentials},
      {s -> s.region == 'eu-west-1' && s.credentials == prodCredentials},
      sourceLookup, targetLookup, false, false) >> new MigrateSecurityGroupResult(target: new MigrateSecurityGroupReference(targetName: 'group1-vpc'))
    1 * migrateSecurityGroupStrategy.generateResults(
      {s -> s.name == 'group2' && s.region == 'us-east-1' && s.credentials == testCredentials},
      {s -> s.region == 'eu-west-1' && s.credentials == prodCredentials},
      sourceLookup, targetLookup, false, false) >> new MigrateSecurityGroupResult(target: new MigrateSecurityGroupReference(targetName: 'group2-vpc'))
    1 * migrateSecurityGroupStrategy.generateResults(
      {s -> s.name == 'group3' && s.region == 'us-east-1' && s.credentials == testCredentials},
      {s -> s.region == 'eu-west-1' && s.credentials == prodCredentials},
      sourceLookup, targetLookup, false, false) >> new MigrateSecurityGroupResult(target: skippedReference, skipped: [skippedReference])
    1 * basicAmazonDeployHandler.copySourceAttributes(regionScopedProvider, 'asg-v001', false, _) >> { a, b, c, d -> d }
    1 * basicAmazonDeployHandler.handle({
      {d -> d.securityGroups == ['group1-vpc', 'group2-vpc']}
    }, []) >> new DeploymentResult(serverGroupNames: ['asg-v003'])
    0 * _
  }

  void 'adds app security group if configured in deployDefaults'() {
    given:
    ServerGroupLocation source = new ServerGroupLocation(name: 'asg-v001', credentials: testCredentials, vpcId: 'vpc-1', region: 'us-east-1')
    ServerGroupLocation target = new ServerGroupLocation(credentials: prodCredentials, vpcId: 'vpc-2', region: 'eu-west-1')
    AutoScalingGroup asg = new AutoScalingGroup(launchConfigurationName: 'asg-v001-lc')
    LaunchConfiguration lc = new LaunchConfiguration(
      instanceMonitoring: new InstanceMonitoring(enabled: false)
    )
    RegionScopedProvider regionScopedProvider = Stub(RegionScopedProvider) {
      getAsgService() >> asgService
    }

    when:
    strategy.generateResults(source, target, sourceLookup, targetLookup,
      migrateLoadBalancerStrategy, migrateSecurityGroupStrategy, 'internal', 'external', null, null, null, [:], false, false)

    then:
    amazonClientProvider.getAutoScaling(testCredentials, 'us-east-1', true) >> amazonAutoScaling
    amazonAutoScaling.describeAutoScalingGroups() >> new DescribeAutoScalingGroupsResult().withAutoScalingGroups(asg)
    regionScopedProviderFactory.forRegion(testCredentials, 'us-east-1') >> regionScopedProvider
    regionScopedProviderFactory.forRegion(prodCredentials, 'eu-west-1') >> regionScopedProvider
    1 * asgService.getAutoScalingGroup('asg-v001') >> asg
    1 * asgService.getLaunchConfiguration('asg-v001-lc') >> lc
    1 * deployDefaults.getAddAppGroupToServerGroup() >> true
    1 * allowLaunchAtomicOperationConverter.convertOperation(_) >> allowLaunchOperation
    1 * allowLaunchOperation.operate(null)
    1 * migrateSecurityGroupStrategy.generateResults(
      {s -> s.name == 'asg' && s.region == 'us-east-1' && s.credentials == testCredentials},
      {s -> s.region == 'eu-west-1' && s.credentials == prodCredentials},
      sourceLookup, targetLookup, true, false) >> new MigrateSecurityGroupResult(target: new MigrateSecurityGroupReference())
    1 * basicAmazonDeployHandler.copySourceAttributes(regionScopedProvider, 'asg-v001', false, _) >> { a, b, c, d -> d }
    1 * basicAmazonDeployHandler.handle(_, []) >> new DeploymentResult(serverGroupNames: ['asg-v003'])
    0 * _
  }

  void 'does not add app security group if it is already there'() {
    given:
    ServerGroupLocation source = new ServerGroupLocation(name: 'theapp-v001', credentials: testCredentials, vpcId: 'vpc-1', region: 'us-east-1')
    ServerGroupLocation target = new ServerGroupLocation(credentials: prodCredentials, vpcId: 'vpc-2', region: 'eu-west-1')
    AutoScalingGroup asg = new AutoScalingGroup(launchConfigurationName: 'theapp-v001-lc')
    LaunchConfiguration lc = new LaunchConfiguration(
      instanceMonitoring: new InstanceMonitoring(enabled: false),
      securityGroups: ['sg-1']
    )
    RegionScopedProvider regionScopedProvider = Stub(RegionScopedProvider) {
      getAsgService() >> asgService
    }
    SecurityGroup appGroup = new SecurityGroup(groupId: 'sg-1', groupName: 'theapp', vpcId: 'vpc-1')
    SecurityGroupUpdater updater = Stub(SecurityGroupUpdater) {
      getSecurityGroup() >> appGroup
    }

    when:
    strategy.generateResults(source, target, sourceLookup, targetLookup,
      migrateLoadBalancerStrategy, migrateSecurityGroupStrategy, 'internal', 'external', null, null, null, [:], false, true)

    then:
    amazonClientProvider.getAutoScaling(testCredentials, 'us-east-1', true) >> amazonAutoScaling
    amazonAutoScaling.describeAutoScalingGroups() >> new DescribeAutoScalingGroupsResult().withAutoScalingGroups(asg)
    regionScopedProviderFactory.forRegion(testCredentials, 'us-east-1') >> regionScopedProvider
    regionScopedProviderFactory.forRegion(prodCredentials, 'eu-west-1') >> regionScopedProvider
    sourceLookup.getSecurityGroupById('test', 'sg-1', 'vpc-1') >> Optional.of(updater)
    1 * asgService.getAutoScalingGroup('theapp-v001') >> asg
    1 * asgService.getLaunchConfiguration('theapp-v001-lc') >> lc
    1 * deployDefaults.getAddAppGroupToServerGroup() >> true
    1 * migrateSecurityGroupStrategy.generateResults(
      {s -> s.name == 'theapp' && s.region == 'us-east-1' && s.credentials == testCredentials},
      {s -> s.region == 'eu-west-1' && s.credentials == prodCredentials},
      sourceLookup, targetLookup, false, true) >> new MigrateSecurityGroupResult(target: new MigrateSecurityGroupReference(targetName: 'theapp'))
    0 * _
  }

  void 'copies over suspended processes'() {
    given:
    ServerGroupLocation source = new ServerGroupLocation(name: 'asg-v001', credentials: testCredentials, vpcId: 'vpc-1', region: 'us-east-1')
    ServerGroupLocation target = new ServerGroupLocation(credentials: prodCredentials, vpcId: 'vpc-2', region: 'eu-west-1')
    AutoScalingGroup asg = new AutoScalingGroup(launchConfigurationName: 'asg-v001-lc')
      .withSuspendedProcesses([
        new SuspendedProcess().withProcessName('someProcess'),
        new SuspendedProcess().withProcessName('otherProcess')
    ])
    LaunchConfiguration lc = new LaunchConfiguration(
      instanceMonitoring: new InstanceMonitoring(enabled: false)
    )
    AWSServerGroupNameResolver nameResolver = Mock(AWSServerGroupNameResolver)
    RegionScopedProvider regionScopedProvider = Stub(RegionScopedProvider) {
      getAsgService() >> asgService
      getAWSServerGroupNameResolver() >> nameResolver
    }

    when:
    strategy.generateResults(source, target, sourceLookup, targetLookup,
      migrateLoadBalancerStrategy, migrateSecurityGroupStrategy, 'internal', 'external', null, null, null, [:], false, false)

    then:
    amazonClientProvider.getAutoScaling(testCredentials, 'us-east-1', true) >> amazonAutoScaling
    amazonAutoScaling.describeAutoScalingGroups() >> new DescribeAutoScalingGroupsResult().withAutoScalingGroups(asg)
    regionScopedProviderFactory.forRegion(testCredentials, 'us-east-1') >> regionScopedProvider
    regionScopedProviderFactory.forRegion(prodCredentials, 'eu-west-1') >> regionScopedProvider
    1 * asgService.getAutoScalingGroup('asg-v001') >> asg
    1 * asgService.getLaunchConfiguration('asg-v001-lc') >> lc
    1 * deployDefaults.getAddAppGroupToServerGroup() >> false
    1 * allowLaunchAtomicOperationConverter.convertOperation(_) >> allowLaunchOperation
    1 * allowLaunchOperation.operate(null)
    1 * basicAmazonDeployHandler.copySourceAttributes(regionScopedProvider, 'asg-v001', false, _) >> { a, b, c, d -> d }
    1 * basicAmazonDeployHandler.handle({d -> d.suspendedProcesses.sort() == ['otherProcess', 'someProcess']}, []) >> new DeploymentResult(serverGroupNames: ['asg-v003'])
    0 * _
  }

  void 'sets source on deploy handler from source parameter and server group name on results'() {
    given:
    ServerGroupLocation source = new ServerGroupLocation(name: 'asg-v001', credentials: testCredentials, vpcId: 'vpc-1', region: 'us-east-1')
    ServerGroupLocation target = new ServerGroupLocation(credentials: prodCredentials, vpcId: 'vpc-2', region: 'eu-west-1')
    AutoScalingGroup asg = new AutoScalingGroup(launchConfigurationName: 'asg-v001-lc')
    LaunchConfiguration lc = new LaunchConfiguration(
      instanceMonitoring: new InstanceMonitoring(enabled: false)
    )
    AWSServerGroupNameResolver nameResolver = Mock(AWSServerGroupNameResolver)
    RegionScopedProvider regionScopedProvider = Stub(RegionScopedProvider) {
      getAsgService() >> asgService
      getAWSServerGroupNameResolver() >> nameResolver
    }

    when:
    def results = strategy.generateResults(source, target, sourceLookup, targetLookup,
      migrateLoadBalancerStrategy, migrateSecurityGroupStrategy, 'internal', 'external', null, null, null, [:], false, false)

    then:
    amazonClientProvider.getAutoScaling(testCredentials, 'us-east-1', true) >> amazonAutoScaling
    amazonAutoScaling.describeAutoScalingGroups() >> new DescribeAutoScalingGroupsResult().withAutoScalingGroups(asg)
    regionScopedProviderFactory.forRegion(testCredentials, 'us-east-1') >> regionScopedProvider
    regionScopedProviderFactory.forRegion(prodCredentials, 'eu-west-1') >> regionScopedProvider
    1 * asgService.getAutoScalingGroup('asg-v001') >> asg
    1 * asgService.getLaunchConfiguration('asg-v001-lc') >> lc
    1 * deployDefaults.getAddAppGroupToServerGroup() >> false
    1 * allowLaunchAtomicOperationConverter.convertOperation(_) >> allowLaunchOperation
    1 * allowLaunchOperation.operate(null)
    1 * basicAmazonDeployHandler.copySourceAttributes(regionScopedProvider, 'asg-v001', false, _) >> { a, b, c, d -> d }
    1 * basicAmazonDeployHandler.handle(_, []) >> new DeploymentResult(serverGroupNames: ['asg-v003'])
    0 * _
    results.serverGroupNames == ['asg-v003']
  }

  void 'sets name on dryRun'() {
    given:
    ServerGroupLocation source = new ServerGroupLocation(name: 'asg-v001', credentials: testCredentials, vpcId: 'vpc-1', region: 'us-east-1')
    ServerGroupLocation target = new ServerGroupLocation(credentials: prodCredentials, vpcId: 'vpc-2', region: 'eu-west-1')
    AutoScalingGroup asg = new AutoScalingGroup(launchConfigurationName: 'asg-v001-lc')
    LaunchConfiguration lc = new LaunchConfiguration()
    AWSServerGroupNameResolver nameResolver = Mock(AWSServerGroupNameResolver)
    RegionScopedProvider regionScopedProvider = Stub(RegionScopedProvider) {
      getAsgService() >> asgService
      getAWSServerGroupNameResolver() >> nameResolver
    }

    when:
    def results = strategy.generateResults(source, target, sourceLookup, targetLookup,
      migrateLoadBalancerStrategy, migrateSecurityGroupStrategy, 'internal', 'external', null, null, null, [:], false, true)

    then:
    amazonClientProvider.getAutoScaling(testCredentials, 'us-east-1', true) >> amazonAutoScaling
    amazonAutoScaling.describeAutoScalingGroups() >> new DescribeAutoScalingGroupsResult().withAutoScalingGroups(asg)
    regionScopedProviderFactory.forRegion(testCredentials, 'us-east-1') >> regionScopedProvider
    regionScopedProviderFactory.forRegion(prodCredentials, 'eu-west-1') >> regionScopedProvider
    1 * asgService.getAutoScalingGroup('asg-v001') >> asg
    1 * asgService.getLaunchConfiguration('asg-v001-lc') >> lc
    1 * deployDefaults.getAddAppGroupToServerGroup() >> false
    1 * nameResolver.resolveNextServerGroupName('asg', null, null, false) >> 'asg-v002'
    0 * _
    results.serverGroupNames == ['asg-v002']
  }

}
