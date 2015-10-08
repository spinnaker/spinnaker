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

package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeSupport
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetServerGroup
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetServerGroupResolver
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@Slf4j
class ResizeServerGroupTask extends AbstractCloudProviderAwareTask implements Task {

  @Autowired
  KatoService kato

  @Override
  TaskResult execute(Stage stage) {
    String cloudProvider = getCloudProvider(stage)
    String account = getCredentials(stage)

    def operation = convert(stage)
    def taskId = kato.requestOperations(cloudProvider, [[resizeServerGroup: operation]])
                     .toBlocking()
                     .first()
    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [
      "notification.type"   : "resizeasg", // TODO(someone on NFLX side): Rename to 'resizeservergroup'?
      "deploy.account.name" : account,
      "kato.last.task.id"   : taskId,
      "asgName"             : operation.asgName,
      "capacity"            : operation.capacity,
      "deploy.server.groups": operation.regions.collectEntries {
        [(it): [operation.asgName]]
      }
    ])
  }

  Map convert(Stage stage) {
    if (TargetServerGroup.isDynamicallyBound(stage)) {
      def tsg = TargetServerGroupResolver.fromPreviousStage(stage)
      def descriptors = ResizeSupport.createResizeDescriptors(stage, [tsg])
      return descriptors?.get(0)
    }

    // Statically bound resize operations put the descriptor as the context.
    return stage.context
  }
}
