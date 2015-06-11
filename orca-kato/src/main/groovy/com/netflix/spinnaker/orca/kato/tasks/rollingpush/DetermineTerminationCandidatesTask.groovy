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
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DetermineTerminationCandidatesTask implements Task {

  @Autowired
  OortService oortService
  @Autowired
  ObjectMapper objectMapper

  @Override
  TaskResult execute(Stage stage) {
    def stageData = stage.mapTo(StageData)
    def response = oortService.getServerGroup(stageData.application, stageData.account, stageData.cluster, stage.context.asgName, stage.context.region, stage.context.providerType ?: 'aws')
    def serverGroup = objectMapper.readValue(response.body.in(), Map)
    boolean ascending = stage.context.termination?.order != 'newest'
    int totalRelaunches = getNumberOfRelaunches(stage.context.termination, serverGroup.instances.size())
    def knownInstanceIds = serverGroup.instances.sort { ascending ? it.launchTime : -it.launchTime }*.instanceId
    def terminationInstanceIds = knownInstanceIds.take(totalRelaunches)
    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [terminationInstanceIds: terminationInstanceIds, knownInstanceIds: knownInstanceIds])
  }

  int getNumberOfRelaunches(Map termination, int totalAsgSize) {
    if (termination == null || termination.relaunchAllInstances || termination.totalRelaunches == null) {
      return totalAsgSize
    }

    return termination.totalRelaunches as Integer
  }
}
