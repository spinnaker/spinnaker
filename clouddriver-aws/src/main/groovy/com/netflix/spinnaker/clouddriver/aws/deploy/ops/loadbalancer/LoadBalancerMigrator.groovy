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

import com.netflix.spinnaker.config.AwsConfiguration.DeployDefaults
import com.netflix.spinnaker.clouddriver.aws.deploy.description.AbstractAmazonCredentialsDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateLoadBalancerStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateSecurityGroupStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.servergroup.ClusterConfigurationMigrator.ClusterConfigurationTarget
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.servergroup.ServerGroupMigrator.ServerGroupLocation
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository

import static com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupLookup

class LoadBalancerMigrator {

  public static final String BASE_PHASE = "MIGRATE_LOAD_BALANCER"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  AmazonClientProvider amazonClientProvider
  RegionScopedProviderFactory regionScopedProviderFactory
  MigrateSecurityGroupStrategy migrateSecurityGroupStrategy
  DeployDefaults deployDefaults

  MigrateLoadBalancerStrategy migrationStrategy
  LoadBalancerLocation source
  TargetLoadBalancerLocation target
  SecurityGroupLookup sourceLookup
  SecurityGroupLookup targetLookup
  String applicationName
  String subnetType
  boolean allowIngressFromClassic

  LoadBalancerMigrator(SecurityGroupLookup sourceLookup,
                       SecurityGroupLookup targetLookup,
                       AmazonClientProvider amazonClientProvider,
                       RegionScopedProviderFactory regionScopedProviderFactory,
                       MigrateSecurityGroupStrategy migrateSecurityGroupStrategy,
                       DeployDefaults deployDefaults,
                       MigrateLoadBalancerStrategy migrationStrategy,
                       LoadBalancerLocation source,
                       TargetLoadBalancerLocation target,
                       String subnetType,
                       String applicationName,
                       boolean allowIngressFromClassic) {

    this.sourceLookup = sourceLookup
    this.targetLookup = targetLookup
    this.amazonClientProvider = amazonClientProvider
    this.regionScopedProviderFactory = regionScopedProviderFactory
    this.migrateSecurityGroupStrategy = migrateSecurityGroupStrategy
    this.deployDefaults = deployDefaults
    this.migrationStrategy = migrationStrategy
    this.source = source
    this.target = target
    this.subnetType = subnetType
    this.applicationName = applicationName
    this.allowIngressFromClassic = allowIngressFromClassic
  }

  public MigrateLoadBalancerResult migrate(boolean dryRun) {
    task.updateStatus BASE_PHASE, (dryRun ? "Calculating" : "Beginning") + " migration of load balancer " + source.toString()
    def results = migrationStrategy.generateResults(sourceLookup, targetLookup, migrateSecurityGroupStrategy,
      source, target, subnetType, applicationName, allowIngressFromClassic, dryRun)
    task.updateStatus BASE_PHASE, "Migration of load balancer " + source.toString() +
      (dryRun ? " calculated" : " completed") + ". Migrated load balancer name: " + results.targetName +
      (results.targetExists ? " (already exists)": "")
    results
  }

  public static class LoadBalancerLocation extends AbstractAmazonCredentialsDescription {
    String name
    String region
    String vpcId

    @Override
    String toString() {
      "$name in $credentialAccount/$region" + (vpcId ? "/$vpcId" : "")
    }
  }

  public static class TargetLoadBalancerLocation extends LoadBalancerLocation {
    List<String> availabilityZones
    boolean useZonesFromSource

    TargetLoadBalancerLocation() {}

    TargetLoadBalancerLocation(LoadBalancerLocation sourceLocation, ServerGroupLocation serverGroupLocation) {
      this.credentials = serverGroupLocation.credentials
      this.region = serverGroupLocation.region
      this.vpcId = serverGroupLocation.vpcId
      this.useZonesFromSource = isSameRegion(sourceLocation)
      this.availabilityZones = useZonesFromSource ? [] : serverGroupLocation.availabilityZones
    }

    TargetLoadBalancerLocation(LoadBalancerLocation sourceLocation, ClusterConfigurationTarget clusterConfigurationTarget) {
      this.credentials = clusterConfigurationTarget.credentials
      this.region = clusterConfigurationTarget.region
      this.vpcId = clusterConfigurationTarget.vpcId
      this.useZonesFromSource = isSameRegion(sourceLocation)
      this.availabilityZones = useZonesFromSource ? [] : clusterConfigurationTarget.availabilityZones
    }


    private boolean isSameRegion(LoadBalancerLocation sourceLocation) {
      return credentialAccount == sourceLocation?.credentialAccount && region == sourceLocation?.region
    }


  }
}
