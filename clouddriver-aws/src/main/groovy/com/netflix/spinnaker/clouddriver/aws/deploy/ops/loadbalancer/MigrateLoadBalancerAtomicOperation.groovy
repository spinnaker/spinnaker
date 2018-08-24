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
import com.netflix.spinnaker.clouddriver.aws.deploy.description.MigrateLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateLoadBalancerStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateSecurityGroupStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupLookup
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

import javax.inject.Provider

class MigrateLoadBalancerAtomicOperation implements AtomicOperation<Void> {

  final MigrateLoadBalancerDescription description

  MigrateLoadBalancerAtomicOperation(MigrateLoadBalancerDescription description) {
    this.description = description
  }

  @Autowired
  Provider<MigrateLoadBalancerStrategy> migrationStrategy

  @Autowired
  SecurityGroupLookupFactory securityGroupLookupFactory

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  @Autowired
  Provider<MigrateSecurityGroupStrategy> migrateSecurityGroupStrategy

  @Autowired
  DeployDefaults deployDefaults

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  Void operate(List priorOutputs) {
    SecurityGroupLookup sourceLookup = securityGroupLookupFactory.getInstance(description.source.region, false)
    SecurityGroupLookup targetLookup = description.source.region == description.target.region ?
      sourceLookup :
      securityGroupLookupFactory.getInstance(description.target.region, false)

    def migrator = new LoadBalancerMigrator(sourceLookup, targetLookup, amazonClientProvider,
      regionScopedProviderFactory, migrateSecurityGroupStrategy.get(), deployDefaults, migrationStrategy.get(),
      description.source, description.target, description.subnetType, description.application,
      description.allowIngressFromClassic)

    task.addResultObjects([migrator.migrate(description.dryRun)])
  }
}
