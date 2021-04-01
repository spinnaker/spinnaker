/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.scalingpolicy

import com.netflix.spinnaker.orca.api.operations.OperationsContext
import com.netflix.spinnaker.orca.api.operations.OperationsInput
import com.netflix.spinnaker.orca.api.operations.OperationsRunner
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware

import org.springframework.stereotype.Component

import javax.annotation.Nonnull

@Component
class DeleteScalingPolicyTask implements CloudProviderAware, Task {

  private final OperationsRunner operationsRunner

  DeleteScalingPolicyTask(OperationsRunner operationsRunner) {
    this.operationsRunner = operationsRunner
  }

  @Nonnull
  @Override
  TaskResult execute(@Nonnull StageExecution stage) {
    OperationsInput operationsInput = OperationsInput.builder()
      .cloudProvider(getCloudProvider(stage))
      .operations([[deleteScalingPolicy: stage.context]])
      .stageExecution(stage)
      .build()

    OperationsContext operationsContext = operationsRunner.run(operationsInput)

    TaskResult.builder(ExecutionStatus.SUCCEEDED).context(Map.of(
        operationsContext.contextKey(), operationsContext.contextValue(),
        "deploy.account.name", stage.context.credentials,
        "deploy.server.groups", [(stage.context.region): [stage.context.serverGroupName]]
    )).build()
  }
}
