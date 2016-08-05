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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.MigrateClusterConfigurationsDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateClusterConfigurationStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupLookup
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.servergroup.ClusterConfigurationMigrator.ClusterConfiguration
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.servergroup.MigrateClusterConfigurationResult
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.servergroup.MigrateClusterConfigurationsAtomicOperation
import com.netflix.spinnaker.clouddriver.aws.model.SubnetAnalyzer
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory.RegionScopedProvider
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Provider

class MigrateClusterConfigurationsAtomicOperationSpec extends Specification {

  Task task

  @Shared
  NetflixAmazonCredentials testCredentials = TestCredential.named('test')

  @Shared
  NetflixAmazonCredentials prodCredentials = TestCredential.named('prod')

  def setup() {
    task = new DefaultTask("taskId")
    TaskRepository.threadLocalTask.set(task)
  }

  void 'performs no migrations when clusters are not matched on account, region, or subnet mappings'() {
    given:
    def clusters = [
      [ application: 'theapp',
        availabilityZones: ['us-east-1': ['us-east-1c']],
        region: 'us-east-1',
        account: 'test',
        iamRole: 'iam',
        keyPair: 'kp-1',
        credentials: testCredentials
      ],
      [ application: 'theapp',
        availabilityZones: ['us-east-1': ['us-east-1c']],
        region: 'us-east-1',
        account: 'prod',
        iamRole: 'iam2',
        keyPair: 'kp-2',
        credentials: prodCredentials
      ]
    ]
    def description = new MigrateClusterConfigurationsDescription(
      sources: clusters.collect { new ClusterConfiguration(cluster: it)},
      subnetTypeMapping: [ 'old-internal': 'internal'],
      accountMapping: [ 'other': 'someOther'],
      regionMapping: ['us-west-1':['us-east-1':['us-east-1c']]],
      iamRoleMapping: ['iam': 'iam3', 'iam2': 'iam4']
    )
    def operation = new MigrateClusterConfigurationsAtomicOperation(description)

    when:
    operation.operate([])

    then:
    task.resultObjects.size() == 2
    task.resultObjects.cluster == clusters
    0 * _
  }

  void 'migrates matched clusters, reusing security group lookups and subnet analyzers when possible'() {
    given:
    def clusters = [
      [ application: 'theapp',
        availabilityZones: ['us-east-1': ['us-east-1c']],
        region: 'us-east-1',
        account: 'test',
        iamRole: 'iam',
        keyPair: 'kp-1',
        credentials: testCredentials
      ],
      [ application: 'theapp',
        stack: 'a',
        availabilityZones: ['us-east-1': ['us-east-1c']],
        region: 'us-east-1',
        account: 'prod',
        iamRole: 'iam2',
        keyPair: 'kp-2',
        credentials: prodCredentials
      ],
      [ application: 'theapp',
        stack: 'b',
        availabilityZones: ['us-east-1': ['us-east-1c']],
        region: 'us-east-1',
        account: 'prod',
        iamRole: 'iam3',
        keyPair: 'kp-3',
        credentials: prodCredentials
      ]
    ]
    def source1 = new ClusterConfiguration(cluster: clusters[0])
    def source2 = new ClusterConfiguration(cluster: clusters[1])
    def source3 = new ClusterConfiguration(cluster: clusters[2])
    def description = new MigrateClusterConfigurationsDescription(
      elbSubnetTypeMapping: [ (MigrateClusterConfigurationsAtomicOperation.CLASSIC_SUBNET_KEY): 'external' ],
      sources: [source1, source2, source3],
    subnetTypeMapping: [ (MigrateClusterConfigurationsAtomicOperation.CLASSIC_SUBNET_KEY): 'internal'])
    def operation = new MigrateClusterConfigurationsAtomicOperation(description)

    SecurityGroupLookup lookup = Mock(SecurityGroupLookup) {
      1 * getCredentialsForName('test') >> testCredentials
      2 * getCredentialsForName('prod') >> prodCredentials
    }
    SecurityGroupLookupFactory securityGroupLookupFactory = Mock(SecurityGroupLookupFactory) {
      1 * getInstance('us-east-1', false) >> lookup
    }
    SubnetAnalyzer testSubnetAnalyzer = Mock(SubnetAnalyzer) {
      1 * getVpcIdForSubnetPurpose('internal') >> 'vpc-test'
    }
    SubnetAnalyzer prodSubnetAnalyzer = Mock(SubnetAnalyzer) {
      2 * getVpcIdForSubnetPurpose('internal') >> 'vpc-prod'
    }
    RegionScopedProvider testScopedProvider = Mock(RegionScopedProvider) {
      1 * getSubnetAnalyzer() >> testSubnetAnalyzer
    }
    RegionScopedProvider prodScopedProvider = Mock(RegionScopedProvider) {
      1 * getSubnetAnalyzer() >> prodSubnetAnalyzer
    }
    RegionScopedProviderFactory regionScopedProviderFactory = Mock(RegionScopedProviderFactory) {
      1 * forRegion(testCredentials, 'us-east-1') >> testScopedProvider
      1 * forRegion(prodCredentials, 'us-east-1') >> prodScopedProvider
    }
    MigrateClusterConfigurationStrategy clusterMigrateStrategy = Mock(MigrateClusterConfigurationStrategy)

    operation.regionScopedProviderFactory = regionScopedProviderFactory;
    operation.securityGroupLookupFactory = securityGroupLookupFactory;
    operation.migrationStrategy = Stub(Provider) {
      get() >> clusterMigrateStrategy
    }
    operation.migrateLoadBalancerStrategy = Mock(Provider)
    operation.migrateSecurityGroupStrategy = Mock(Provider)

    when:
    operation.operate([])

    then:
    1 * clusterMigrateStrategy.generateResults(source1, {
      it.region == 'us-east-1' && it.credentials == testCredentials && it.vpcId == 'vpc-test'
    }, lookup, lookup, _, _, 'internal', 'external', 'iam', 'kp-1', [:], false, false) >> new MigrateClusterConfigurationResult()
    1 * clusterMigrateStrategy.generateResults(source2, {
      it.region == 'us-east-1' && it.credentials == prodCredentials && it.vpcId == 'vpc-prod'
    }, lookup, lookup, _, _, 'internal', 'external', 'iam2', 'kp-2', [:], false, false) >> new MigrateClusterConfigurationResult()
    1 * clusterMigrateStrategy.generateResults(source3, {
      it.region == 'us-east-1' && it.credentials == prodCredentials && it.vpcId == 'vpc-prod'
    }, lookup, lookup, _, _, 'internal', 'external', 'iam3', 'kp-3', [:], false, false) >> new MigrateClusterConfigurationResult()
    task.resultObjects.size() == 3
  }
}
