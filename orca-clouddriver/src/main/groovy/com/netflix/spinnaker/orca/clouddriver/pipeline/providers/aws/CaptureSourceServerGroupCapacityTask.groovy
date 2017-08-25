/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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


package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class CaptureSourceServerGroupCapacityTask implements Task {
  @Autowired
  OortHelper oortHelper

  @Autowired
  ObjectMapper objectMapper

  @Override
  TaskResult execute(Stage stage) {
    def stageOutputs = [:]
    StageData stageData = stage.mapTo(StageData)
    if (stageData.useSourceCapacity) {
      if (!stageData.source && stageData.preferSourceCapacity) {
        stageData.setUseSourceCapacity(false)
        stageOutputs = [
          useSourceCapacity: false
        ]
      } else {
        def sourceServerGroup = oortHelper.getTargetServerGroup(
          stageData.source.account,
          stageData.source.asgName,
          stageData.source.region,
          stageData.cloudProvider ?: stageData.providerType
        ).orElse(null)

        if (sourceServerGroup) {
          // capture the source server group's capacity AND specify an explicit capacity to use when deploying the next
          // server group (ie. no longer use source capacity)
          stageData.setUseSourceCapacity(false)
          stageData.source.useSourceCapacity = false
          stageOutputs = [
            useSourceCapacity                : false,
            source                           : stageData.source,
            sourceServerGroupCapacitySnapshot: sourceServerGroup.capacity,
            capacity                         : [
              min    : sourceServerGroup.capacity.desired,
              desired: sourceServerGroup.capacity.desired,
              max    : sourceServerGroup.capacity.max
            ]
          ]
        }
      }
    }

    return new TaskResult(ExecutionStatus.SUCCEEDED, stageOutputs)
  }
}
