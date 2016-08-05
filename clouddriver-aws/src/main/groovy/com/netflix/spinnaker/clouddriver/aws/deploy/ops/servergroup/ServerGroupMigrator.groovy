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

import com.netflix.spinnaker.clouddriver.aws.deploy.description.AbstractAmazonCredentialsDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateLoadBalancerStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateSecurityGroupStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateServerGroupStrategy
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository

import static com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupLookup

class ServerGroupMigrator {

  private static final String BASE_PHASE = "MIGRATE_SERVER_GROUP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  MigrateServerGroupStrategy migrationStrategy
  MigrateLoadBalancerStrategy migrateLoadBalancerStrategy
  MigrateSecurityGroupStrategy migrateSecurityGroupStrategy
  ServerGroupLocation source
  ServerGroupLocation target
  SecurityGroupLookup sourceLookup
  SecurityGroupLookup targetLookup
  String iamRole
  String keyPair
  String subnetType
  String elbSubnetType
  String targetAmi
  Map<String, String> loadBalancerNameMapping
  boolean allowIngressFromClassic

  ServerGroupMigrator(MigrateServerGroupStrategy migrationStrategy,
                      ServerGroupLocation source,
                      ServerGroupLocation target,
                      SecurityGroupLookup sourceLookup,
                      SecurityGroupLookup targetLookup,
                      MigrateLoadBalancerStrategy migrateLoadBalancerStrategy,
                      MigrateSecurityGroupStrategy migrateSecurityGroupStrategy,
                      String subnetType,
                      String elbSubnetType,
                      String iamRole,
                      String keyPair,
                      String targetAmi,
                      Map<String, String> loadBalancerNameMapping,
                      boolean allowIngressFromClassic) {

    this.migrationStrategy = migrationStrategy
    this.migrateLoadBalancerStrategy = migrateLoadBalancerStrategy
    this.migrateSecurityGroupStrategy = migrateSecurityGroupStrategy
    this.source = source
    this.target = target
    this.sourceLookup = sourceLookup
    this.targetLookup = targetLookup
    this.iamRole = iamRole
    this.keyPair = keyPair
    this.subnetType = subnetType
    this.elbSubnetType = elbSubnetType
    this.targetAmi = targetAmi
    this.loadBalancerNameMapping = loadBalancerNameMapping
    this.allowIngressFromClassic = allowIngressFromClassic
  }

  public MigrateServerGroupResult migrate(boolean dryRun) {
    task.updateStatus BASE_PHASE, (dryRun ? "Calculating" : "Beginning") + " migration of server group " + source.toString()
    MigrateServerGroupResult results = migrationStrategy.generateResults(source, target, sourceLookup, targetLookup,
      migrateLoadBalancerStrategy, migrateSecurityGroupStrategy, subnetType, elbSubnetType, iamRole, keyPair, targetAmi,
      loadBalancerNameMapping, allowIngressFromClassic, dryRun)
    task.updateStatus BASE_PHASE, "Migration of server group " + source.toString() +
      (dryRun ? " calculated" : " completed") + ". Migrated server group name: " + results.serverGroupNames.get(0)
    results
  }


  public static class ServerGroupLocation extends AbstractAmazonCredentialsDescription {
    String name
    String region
    String vpcId
    List<String> availabilityZones

    @Override
    String toString() {
      "$name in $credentialAccount/$region" + (vpcId ? "/$vpcId" : "")
    }
  }
}
