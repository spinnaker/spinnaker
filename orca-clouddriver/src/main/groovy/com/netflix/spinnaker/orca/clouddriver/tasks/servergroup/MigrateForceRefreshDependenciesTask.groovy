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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.clouddriver.tasks.loadbalancer.UpsertLoadBalancerForceRefreshTask
import com.netflix.spinnaker.orca.clouddriver.tasks.securitygroup.SecurityGroupForceCacheRefreshTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class MigrateForceRefreshDependenciesTask extends AbstractCloudProviderAwareTask implements Task {

  @Autowired
  CloudDriverCacheService cacheService

  @Override
  TaskResult execute(Stage stage) {
    String cloudProvider = getCloudProvider(stage)
    Map target = stage.context.target as Map
    Map migratedGroup = stage.context["kato.tasks"].get(0).resultObjects.find { it.serverGroupNames } as Map

    migratedGroup.securityGroups.each { Map securityGroup ->
      migrateSecurityGroups(securityGroup, cloudProvider, target.region as String, securityGroup.credentials as String)
    }

    migratedGroup.loadBalancers.each { Map loadBalancer ->
      cacheService.forceCacheUpdate(
        cloudProvider,
        UpsertLoadBalancerForceRefreshTask.REFRESH_TYPE,
        [loadBalancerName: loadBalancer.targetName, region: target.region, account: target.credentials]
      )
      loadBalancer.securityGroups.each { Map securityGroup ->
        migrateSecurityGroups(securityGroup, cloudProvider, target.region as String, loadBalancer.credentials as String)
      }
    }

    new TaskResult(ExecutionStatus.SUCCEEDED)
  }

  private void migrateSecurityGroups(Map migratedGroup, String cloudProvider, String region, String targetCredentials) {
    (migratedGroup.created + migratedGroup.reused).flatten().each { Map securityGroup ->
      cacheService.forceCacheUpdate(
        cloudProvider,
        SecurityGroupForceCacheRefreshTask.REFRESH_TYPE,
        [securityGroupName: securityGroup.targetName, region: region, account: targetCredentials ?: securityGroup.credentials, vpcId: securityGroup.vpcId]
      )
    }
  }
}
