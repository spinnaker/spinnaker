/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.securitygroup

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
public class SecurityGroupForceCacheRefreshTask extends AbstractCloudProviderAwareTask implements Task {
  static final String REFRESH_TYPE = "SecurityGroup"

  @Autowired
  CloudDriverCacheService cacheService

  @Override
  TaskResult execute(Stage stage) {
    String cloudProvider = getCloudProvider(stage)

    stage.context.targets.each { Map target ->
      cacheService.forceCacheUpdate(
        cloudProvider, REFRESH_TYPE, [account: target.accountName, securityGroupName: target.name, region: target.region]
      )
    }

    new TaskResult(ExecutionStatus.SUCCEEDED)
  }
}
