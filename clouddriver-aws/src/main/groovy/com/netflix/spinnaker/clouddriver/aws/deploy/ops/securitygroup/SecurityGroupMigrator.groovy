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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup

import com.netflix.spinnaker.clouddriver.aws.deploy.description.AbstractAmazonCredentialsDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateSecurityGroupStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.LoadBalancerMigrator.LoadBalancerLocation
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository

import static com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupLookup

class SecurityGroupMigrator {

  private static final String BASE_PHASE = "MIGRATE_SECURITY_GROUP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  MigrateSecurityGroupStrategy migrationStrategy
  SecurityGroupLocation source
  SecurityGroupLocation target
  SecurityGroupLookup sourceLookup
  SecurityGroupLookup targetLookup
  boolean createIfSourceMissing

  SecurityGroupMigrator(SecurityGroupLookup sourceLookup,
                        SecurityGroupLookup targetLookup,
                        MigrateSecurityGroupStrategy migrationStrategy,
                        SecurityGroupLocation source,
                        SecurityGroupLocation target) {

    this.sourceLookup = sourceLookup
    this.targetLookup = targetLookup
    this.migrationStrategy = migrationStrategy
    this.source = source
    this.target = target
  }

  public MigrateSecurityGroupResult migrate(boolean dryRun) {
    task.updateStatus BASE_PHASE, "Calculating security group migration requirements for ${source.name}"
    def results = migrationStrategy.generateResults(source, target, sourceLookup, targetLookup, createIfSourceMissing, dryRun)
    task.updateStatus BASE_PHASE, "Migration of security group " + source.toString() +
      (dryRun ? " calculated" : " completed") + ". Migrated security group name: " + results.target.targetName +
      (results.targetExists() ? " (already exists)": "")
    results
  }

  public static class SecurityGroupLocation extends AbstractAmazonCredentialsDescription {
    String name
    String region
    String vpcId

    @Override
    String toString() {
      "${name ?: '(no name)'} in $credentialAccount/$region" + (vpcId ? "/$vpcId" : "")
    }
    SecurityGroupLocation() {}

    SecurityGroupLocation(LoadBalancerLocation loadBalancerLocation) {
      this.credentials = loadBalancerLocation.credentials
      this.region = loadBalancerLocation.region
      this.vpcId = loadBalancerLocation.vpcId
    }
  }
}
