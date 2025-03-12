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

package com.netflix.spinnaker.orca.clouddriver.tasks.instance

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.pipeline.instance.TerminatingInstance
import com.netflix.spinnaker.orca.clouddriver.pipeline.instance.TerminatingInstanceSupport
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware

import com.netflix.spinnaker.orca.clouddriver.utils.MonikerHelper
import com.netflix.spinnaker.orca.clouddriver.utils.TrafficGuard
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

@Component
class TerminateInstanceAndDecrementServerGroupTask implements CloudProviderAware, Task {
  static final String CLOUD_OPERATION_TYPE = "terminateInstanceAndDecrementServerGroup"

  @Autowired
  KatoService kato

  @Autowired
  TerminatingInstanceSupport instanceSupport

  @Autowired
  TrafficGuard trafficGuard

  @Nonnull
  @Override
  TaskResult execute(@Nonnull StageExecution stage) {
    String cloudProvider = getCloudProvider(stage)
    String account = getCredentials(stage)

    List<TerminatingInstance> remainingInstances = instanceSupport.remainingInstances(stage)

    List<String> instanceIds = remainingInstances*.id

    def ctx = [
            "notification.type"                                       : "terminateinstanceanddecrementservergroup",
            "terminate.account.name"                                  : account,
            "terminate.region"                                        : stage.context.region,
            "terminate.instance.ids"                                  : instanceIds,
            (TerminatingInstanceSupport.TERMINATE_REMAINING_INSTANCES): remainingInstances,
    ]

    if (instanceIds) {
      String serverGroupName = stage.context.serverGroupName ?: stage.context.asgName

      trafficGuard.verifyInstanceTermination(
              serverGroupName,
              MonikerHelper.monikerFromStage(stage, serverGroupName),
              instanceIds,
              account,
              Location.region(stage.context.region as String),
              cloudProvider,
              "Terminating the requested instance in ")
      def taskId = kato.requestOperations(cloudProvider, [[(CLOUD_OPERATION_TYPE): stage.context]])

      ctx['kato.last.task.id'] = taskId
    }

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(ctx).build()
  }
}
