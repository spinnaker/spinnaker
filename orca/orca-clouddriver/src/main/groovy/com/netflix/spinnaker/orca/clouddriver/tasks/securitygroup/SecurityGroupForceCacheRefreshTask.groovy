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

import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

@Slf4j
@Component
public class SecurityGroupForceCacheRefreshTask implements CloudProviderAware, Task {
  static final String REFRESH_TYPE = "SecurityGroup"

  @Autowired
  CloudDriverCacheService cacheService

  @Nonnull
  @Override
  TaskResult execute(@Nonnull StageExecution stage) {
    String cloudProvider = getCloudProvider(stage)

    List<String> errors = []
    stage.context.targets.each { Map target ->
      def model = [account: target.accountName, securityGroupName: target.name, region: target.region] as Map
      try {
        Retrofit2SyncCall.executeCall(cacheService.forceCacheUpdate(cloudProvider, REFRESH_TYPE, model))
      } catch (Exception e) {
        log.warn("Failed to force cache refresh (cloudProvider: {}, type: {}, model: {})", cloudProvider, REFRESH_TYPE, model, e)
        errors << "Failed to refresh ${target.name} in ${target.region}: ${e.message}".toString()
      }
    }

    TaskResult.builder(ExecutionStatus.SUCCEEDED).context(errors ? ["force.cache.refresh.errors": errors] : [:]).build()
  }
}
