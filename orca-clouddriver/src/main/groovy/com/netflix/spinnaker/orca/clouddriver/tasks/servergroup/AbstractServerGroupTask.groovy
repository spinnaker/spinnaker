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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location.Type
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractServerGroupTask extends AbstractCloudProviderAwareTask implements RetryableTask {

  @Override
  long getBackoffPeriod() {
    return 2000
  }

  @Override
  long getTimeout() {
    return 60000
  }

  @Autowired
  KatoService kato

  protected boolean isAddTargetOpOutputs() {
    false
  }

  protected void validateClusterStatus(Map operation) {}

  abstract String getServerGroupAction()

  Map getAdditionalStageOutputs(Stage stage, Map operation) {
    [:]
  }

  TaskResult execute(Stage stage) {
    String cloudProvider = getCloudProvider(stage)
    String account = getCredentials(stage)

    def operation = convert(stage)
    validateClusterStatus(operation)
    if (!operation) {
      // nothing to do but succeed
      return new TaskResult(ExecutionStatus.SUCCEEDED)
    }

    def taskId = kato.requestOperations(cloudProvider, [[(serverGroupAction): operation]])
        .toBlocking()
        .first()

    def stageOutputs = [
        "notification.type"   : serverGroupAction.toLowerCase(),
        "kato.last.task.id"   : taskId,
        "deploy.account.name" : account,
        "asgName"             : operation.serverGroupName,
        "serverGroupName"     : operation.serverGroupName,
        "deploy.server.groups": deployServerGroups(operation)
    ]
    if (addTargetOpOutputs) {
      stageOutputs = stageOutputs + [
          ("targetop.asg.${serverGroupAction}.name".toString())   : operation.serverGroupName,
          ("targetop.asg.${serverGroupAction}.regions".toString()): deployServerGroups(operation).keySet(),
      ]
    }

    new TaskResult(ExecutionStatus.SUCCEEDED, stageOutputs + getAdditionalStageOutputs(stage, operation))
  }

  Map convert(Stage stage) {
    def operation = new HashMap(stage.context)
    operation.serverGroupName = (operation.serverGroupName ?: operation.asgName) as String

    if (TargetServerGroup.isDynamicallyBound(stage)) {
      def tsg = TargetServerGroupResolver.fromPreviousStage(stage)
      operation.asgName = tsg.name
      operation.serverGroupName = tsg.name

      def location = tsg.getLocation()
      operation.deployServerGroupsRegion = tsg.region
      if (location.type == Location.Type.ZONE) {
        operation.zone = location.value
        operation.remove("zones")
      }
    }

    operation
  }

  /**
   * @return a Map of location -> server group name
   */
  static Map deployServerGroups(Map operation) {
    def collection
    if (operation.deployServerGroupsRegion) {
      collection = [operation.deployServerGroupsRegion]
    } else if (operation.region) {
      collection = [operation.region]
    } else if (operation.regions) {
      collection = operation.regions
    } else if (operation.zone) {
      collection = [operation.zone]
    } else if (operation.zones) {
      collection = operation.zones
    } else {
      throw new IllegalStateException("Cannot find either regions or zones in operation.")
    }

    return collection.collectEntries {
      [(it): [operation.serverGroupName]]
    }
  }

  protected Location getLocation(Map operation) {
    operation.region ? new Location(Type.REGION, operation.region) :
      operation.zone ? new Location(Type.ZONE, operation.zone) :
        operation.namespace ? new Location(Type.NAMESPACE, operation.namespace) :
          null
  }

}
