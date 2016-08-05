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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.aws.deploy.description.MigrateClusterConfigurationsDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateClusterConfigurationStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateLoadBalancerStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateSecurityGroupStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupLookup
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.servergroup.ClusterConfigurationMigrator.ClusterConfigurationTarget
import com.netflix.spinnaker.clouddriver.aws.model.SubnetAnalyzer
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

import javax.inject.Provider

class MigrateClusterConfigurationsAtomicOperation implements AtomicOperation<Void> {

  final MigrateClusterConfigurationsDescription description

  // When migrating from EC2-Classic to VPC, this is the key that should be passed in with the subnetTypeMappings
  public final static String CLASSIC_SUBNET_KEY = 'EC2-CLASSIC'

  MigrateClusterConfigurationsAtomicOperation(MigrateClusterConfigurationsDescription description) {
    this.description = description
  }

  @Autowired
  Provider<MigrateClusterConfigurationStrategy> migrationStrategy

  @Autowired
  SecurityGroupLookupFactory securityGroupLookupFactory

  @Autowired
  Provider<MigrateSecurityGroupStrategy> migrateSecurityGroupStrategy

  @Autowired
  Provider<MigrateLoadBalancerStrategy> migrateLoadBalancerStrategy

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  Void operate(List priorOutputs) {
    Map<String, SecurityGroupLookup> lookups = [:]
    Map<String, SubnetAnalyzer> subnetAnalyzers = [:]
    List<MigrateClusterConfigurationResult> results = []

    description.sources.each { source ->
      String targetRegion = source.region
      List<String> targetZones = ((Map) source.cluster.availabilityZones).get(source.region) as List<String>
      if (description.regionMapping.containsKey(source.region)) {
        def targetRegionZone = description.regionMapping.get(source.region)
        targetRegion = targetRegionZone.keySet().first()
        targetZones = targetRegionZone.values().first()
      }
      String sourceAccount = (String) source.cluster.account
      String sourceIamRole = (String) source.cluster.iamRole
      String sourceSubnetType = (String) source.cluster.subnetType ?: CLASSIC_SUBNET_KEY
      String sourceKeyPair = (String) source.cluster.keyPair
      String account = description.accountMapping.getOrDefault(sourceAccount, sourceAccount)
      String iamRole = description.iamRoleMapping.getOrDefault(sourceIamRole, sourceIamRole)
      String subnetType = description.subnetTypeMapping.getOrDefault(sourceSubnetType, sourceSubnetType)
      String keyPair = description.keyPairMapping.getOrDefault(sourceKeyPair, sourceKeyPair)
      String elbSubnetType = description.elbSubnetTypeMapping.getOrDefault(sourceSubnetType, sourceSubnetType)

      // nothing changed? don't calculate anything for this cluster
      if (sourceAccount == account && source.region == targetRegion && sourceSubnetType == subnetType) {
        MigrateClusterConfigurationResult result = new MigrateClusterConfigurationResult(cluster: source.cluster)
        results.add(result)
      } else {
        SecurityGroupLookup sourceLookup = lookups.get(source.region)
        if (!sourceLookup) {
          sourceLookup = securityGroupLookupFactory.getInstance(source.region, false)
          lookups.put(source.region, sourceLookup)
        }
        SecurityGroupLookup targetLookup = lookups.get(targetRegion)
        if (!targetLookup) {
          targetLookup = securityGroupLookupFactory.getInstance(targetRegion, false)
          lookups.put(targetRegion, targetLookup)
        }
        def credentials = targetLookup.getCredentialsForName(account)

        if (targetZones.empty) {
          targetZones = credentials.getRegions().find { it.name == targetRegion }.preferredZones
        }
        SubnetAnalyzer subnetAnalyzer = subnetAnalyzers.get(targetRegion + ':' + credentials.name)
        if (!subnetAnalyzer) {
          subnetAnalyzer = regionScopedProviderFactory.forRegion(credentials, targetRegion).subnetAnalyzer
          subnetAnalyzers.put(targetRegion + ':' + credentials.name, subnetAnalyzer)
        }

        ClusterConfigurationTarget target = new ClusterConfigurationTarget(region: targetRegion, credentials: credentials,
          availabilityZones: targetZones, vpcId: subnetAnalyzer.getVpcIdForSubnetPurpose(subnetType))

        def migrator = new ClusterConfigurationMigrator(migrationStrategy.get(), source, target,
          sourceLookup, targetLookup,
          migrateLoadBalancerStrategy.get(), migrateSecurityGroupStrategy.get(), iamRole, keyPair, subnetType,
          elbSubnetType, description.loadBalancerNameMapping, description.allowIngressFromClassic)

        results.add(migrator.migrate(description.dryRun))
      }
    }
    task.addResultObjects(results)
  }
}
