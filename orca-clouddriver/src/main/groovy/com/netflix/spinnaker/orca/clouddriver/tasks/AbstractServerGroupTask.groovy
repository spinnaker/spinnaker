/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.pipeline.support.Location
import com.netflix.spinnaker.orca.clouddriver.pipeline.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.pipeline.support.TargetServerGroupResolver
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractServerGroupTask extends AbstractCloudProviderAwareTask implements Task {

  @Autowired
  KatoService kato

  abstract String getServerGroupAction()

  TaskResult execute(Stage stage) {
    String cloudProvider = getCloudProvider(stage)
    String account = getCredentials(stage)

    def operation = convert(stage)
    def taskId = kato.requestOperations(cloudProvider, [[(serverGroupAction): operation]])
      .toBlocking()
      .first()
    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [
      "notification.type"                                     : serverGroupAction.toLowerCase(),
      "kato.last.task.id"                                     : taskId,
      "deploy.account.name"                                   : account,
      "asgName"                                               : operation.asgName,
      "serverGroupName"                                       : operation.serverGroupName,
      ("targetop.asg.${serverGroupAction}.name".toString())   : operation.asgName,
      ("targetop.asg.${serverGroupAction}.regions".toString()): operation.regions,
      "deploy.server.groups"                                  : (operation.regions as Collection<String>).collectEntries {
        [(it): [operation.serverGroupName]]
      }
    ])
  }

  Map convert(Stage stage) {
    def operation = new HashMap(stage.context)
    operation.serverGroupName = (operation.serverGroupName ?: operation.asgName) as String

    if (TargetServerGroup.isDynamicallyBound(stage)) {
      def tsg = TargetServerGroupResolver.fromPreviousStage(stage)
      operation.asgName = tsg.name
      operation.serverGroupName = tsg.name

      def location = tsg.getLocation()
      if (location.type == Location.Type.ZONE) {
        operation.zone = location.value
        operation.remove("zones")
      }
    }

    operation
  }
}
