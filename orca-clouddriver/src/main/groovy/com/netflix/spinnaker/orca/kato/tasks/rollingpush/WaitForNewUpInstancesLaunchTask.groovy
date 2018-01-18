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

import com.netflix.spinnaker.orca.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.clouddriver.utils.HealthHelper

import java.util.concurrent.TimeUnit
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class WaitForNewUpInstancesLaunchTask implements OverridableTimeoutRetryableTask {

  @Autowired OortService oortService
  @Autowired ObjectMapper objectMapper

  long getBackoffPeriod() { TimeUnit.SECONDS.toMillis(10) }

  long getTimeout() { TimeUnit.HOURS.toMillis(2) }

  @Override
  TaskResult execute(Stage stage) {
    StageData stageData = stage.mapTo(StageData)

    // similar check in `AbstractInstancesCheckTask`
    def response = oortService.getServerGroup(
      stageData.account,
      stage.context.region as String,
      stage.context.asgName as String
    )

    Map serverGroup = objectMapper.readValue(response.body.in(), Map)

    List<Map> serverGroupInstances = serverGroup.instances as List<Map>
    Set<String> knownInstanceIds = new HashSet(stage.context.knownInstanceIds as List)

    List<String> healthProviders = stage.context.interestingHealthProviderNames as List<String>
    Set<String> newUpInstanceIds = serverGroupInstances.findResults {
      String id = stageData.cloudProvider == 'titus' ? it.id : it.instanceId
      !knownInstanceIds.contains(id) &&
        HealthHelper.someAreUpAndNoneAreDown(it, healthProviders) ? id : null
    }

    int expectedNewInstances = (stage.context.instanceIds as List).size()
    if (newUpInstanceIds.size() >= expectedNewInstances) {
      knownInstanceIds.addAll(newUpInstanceIds)
      return new TaskResult(ExecutionStatus.SUCCEEDED, [knownInstanceIds: knownInstanceIds.toList()])
    }
    return new TaskResult(ExecutionStatus.RUNNING)
  }
}
