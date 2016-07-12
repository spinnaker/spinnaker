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

import com.netflix.spinnaker.clouddriver.aws.AwsConfiguration.DeployDefaults
import com.netflix.spinnaker.clouddriver.aws.deploy.description.AbstractAmazonCredentialsDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateLoadBalancerStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateSecurityGroupStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.servergroup.ServerGroupMigrator.ServerGroupLocation
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository

import static com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupLookup

class LoadBalancerMigrator {

  private static final String BASE_PHASE = "MIGRATE_LOAD_BALANCER"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  AmazonClientProvider amazonClientProvider
  RegionScopedProviderFactory regionScopedProviderFactory
  MigrateSecurityGroupStrategy migrateSecurityGroupStrategy
  DeployDefaults deployDefaults

  MigrateLoadBalancerStrategy migrationStrategy
  LoadBalancerLocation source
  LoadBalancerLocation target
  SecurityGroupLookup sourceLookup
  SecurityGroupLookup targetLookup
  String applicationName
  String subnetType

  LoadBalancerMigrator(SecurityGroupLookup sourceLookup,
                       SecurityGroupLookup targetLookup,
                       AmazonClientProvider amazonClientProvider,
                       RegionScopedProviderFactory regionScopedProviderFactory,
                       MigrateSecurityGroupStrategy migrateSecurityGroupStrategy,
                       DeployDefaults deployDefaults,
                       MigrateLoadBalancerStrategy migrationStrategy,
                       LoadBalancerLocation source,
                       LoadBalancerLocation target,
                       String subnetType,
                       String applicationName) {

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
  }

  public MigrateLoadBalancerResult migrate(boolean dryRun) {
    task.updateStatus BASE_PHASE, (dryRun ? "Calculating" : "Beginning") + " migration of load balancer " + source.toString()
    def results = migrationStrategy.generateResults(sourceLookup, targetLookup, migrateSecurityGroupStrategy,
      source, target, subnetType, applicationName, dryRun)
    task.updateStatus BASE_PHASE, "Migration of load balancer " + source.toString() +
      (dryRun ? " calculated" : " completed") + ". Migrated load balancer name: " + results.targetName +
      (results.targetExists ? " (already exists)": "")
    results
  }

  public static class LoadBalancerLocation extends AbstractAmazonCredentialsDescription {
    String name
    String region
    String vpcId
    List<String> availabilityZones

    LoadBalancerLocation() {}

    LoadBalancerLocation(ServerGroupLocation serverGroupLocation) {
      this.credentials = serverGroupLocation.credentials
      this.availabilityZones = serverGroupLocation.availabilityZones
      this.region = serverGroupLocation.region
      this.vpcId = serverGroupLocation.vpcId
    }

    @Override
    String toString() {
      "$name in $credentialAccount/$region" + (vpcId ? "/$vpcId" : "")
    }
  }
}
