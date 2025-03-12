/*
 * Copyright 2016 Google, Inc.
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

import com.netflix.spinnaker.orca.api.operations.OperationsContext
import com.netflix.spinnaker.orca.api.operations.OperationsInput
import com.netflix.spinnaker.orca.api.operations.OperationsRunner
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

@Component
class UpsertServerGroupTagsTask implements CloudProviderAware, Task {

  @Autowired
  OperationsRunner operationsRunner

  @Nonnull
  @Override
  TaskResult execute(@Nonnull StageExecution stage) {
    OperationsInput operationsInput = OperationsInput.of(getCloudProvider(stage), [[upsertServerGroupTags: stage.context]], stage)
    OperationsContext operationsContext = operationsRunner.run(operationsInput)

    def deployServerGroups = []
    if (stage.context.regions && (stage.context.serverGroupName || stage.context.asgName)) {
      deployServerGroups = (stage.context.regions as Collection<String>).collectEntries {
        [(it): [stage.context.serverGroupName ?: stage.context.asgName]]
      }
    } else if (stage.context.asgs) {
      deployServerGroups = (stage.context.asgs as Collection<Map>).collectEntries {
        [(it.region): [it.serverGroupName ?: it.asgName]]
      }
    }

    TaskResult.builder(ExecutionStatus.SUCCEEDED).context([
        "notification.type"             : "upsertservergrouptags",
        "deploy.account.name"           : getCredentials(stage),
        (operationsContext.contextKey()): operationsContext.contextValue(),
        "deploy.server.groups"          : deployServerGroups,
    ]).build()
  }
}
