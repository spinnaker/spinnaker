/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.tasks.rollingpush

import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.utils.HealthHelper

import java.util.concurrent.TimeUnit
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class WaitForNewUpInstancesLaunchTask implements OverridableTimeoutRetryableTask {

  @Autowired CloudDriverService cloudDriverService
  @Autowired(required = false) List<ServerGroupInstanceIdCollector> serverGroupInstanceIdCollectors = []

  long getBackoffPeriod() { TimeUnit.SECONDS.toMillis(10) }

  long getTimeout() { TimeUnit.HOURS.toMillis(2) }

  @Override
  TaskResult execute(StageExecution stage) {
    StageData stageData = stage.mapTo(StageData)

    // similar check in `AbstractInstancesCheckTask`
    Map serverGroup = cloudDriverService.getServerGroup(
      stageData.account,
      stage.context.region as String,
      stage.context.asgName as String
    )

    List<Map> serverGroupInstances = serverGroup.instances as List<Map>
    Set<String> knownInstanceIds = new HashSet(stage.context.knownInstanceIds as List)

    List<String> healthProviders = stage.context.interestingHealthProviderNames as List<String>
    Set<String> newUpInstanceIds = serverGroupInstances.findResults {
      String id = getServerGroupInstanceIdCollector(stage)
          .map { collector -> collector.one(it) }
          .orElse((String) it.instanceId)
      !knownInstanceIds.contains(id) &&
        HealthHelper.someAreUpAndNoneAreDownOrStarting(it, healthProviders) ? id : null
    }

    int expectedNewInstances = (stage.context.instanceIds as List).size()
    if (newUpInstanceIds.size() >= expectedNewInstances) {
      knownInstanceIds.addAll(newUpInstanceIds)
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context([knownInstanceIds: knownInstanceIds.toList()]).build()
    }
    return TaskResult.ofStatus(ExecutionStatus.RUNNING)
  }

  private Optional<ServerGroupInstanceIdCollector> getServerGroupInstanceIdCollector(StageExecution stage) {
    return Optional.ofNullable((String) stage.getContext().get("cloudProvider")).flatMap { cloudProvider ->
      serverGroupInstanceIdCollectors.stream().filter { it.supports(cloudProvider) }.findFirst()
    }
  }
}
