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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.concurrent.TimeUnit

@Component
class WaitForNewInstanceLaunchTask implements RetryableTask {

  @Autowired OortService oortService
  @Autowired ObjectMapper objectMapper

  final long backoffPeriod = TimeUnit.SECONDS.toMillis(5)
  final long timeout = TimeUnit.MINUTES.toMillis(10)

  @Override
  TaskResult execute(Stage stage) {
    StageData stageData = stage.mapTo(StageData)
    def response = oortService.getServerGroup(stageData.application, stageData.account, stageData.cluster, stage.context.asgName as String, stage.context.region as String, stageData.providerType ?: 'aws' )

    Map serverGroup = objectMapper.readValue(response.body.in(), Map)

    Set<String> currentIds = serverGroup.instances*.instanceId
    Set<String> knownInstanceIds = new HashSet(stage.context.knownInstanceIds as List)

    Set<String> newLaunches = new  HashSet(currentIds)
    newLaunches.removeAll(knownInstanceIds)

    int expectedNewInstances = (stage.context.instanceIds as List).size()
    if (newLaunches.size() >= expectedNewInstances) {
      knownInstanceIds.addAll(currentIds)
      return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [instanceIds: newLaunches.toList(), knownInstanceIds: knownInstanceIds.toList()])
    }
    return new DefaultTaskResult(ExecutionStatus.RUNNING)
  }
}
