/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.orca.clouddriver.tasks.instance.AbstractInstancesCheckTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForUpInstancesTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class WaitForCapacityMatchTask extends AbstractInstancesCheckTask {

  @Override
  protected Map<String, List<String>> getServerGroups(Stage stage) {
    (Map<String, List<String>>) stage.context."deploy.server.groups"
  }

  @Override
  Map getAdditionalRunningStageContext(Stage stage, Map serverGroup) {
    return serverGroup.disabled ?
      [:] :
      [ targetDesiredSize: WaitForUpInstancesTask.calculateTargetDesiredSize(stage, serverGroup) ]
  }

  @Override
  protected boolean hasSucceeded(Stage stage, Map serverGroup, List<Map> instances, Collection<String> interestingHealthProviderNames) {
    def splainer = new WaitForUpInstancesTask.Splainer()
      .add("Capacity match check for server group ${serverGroup?.name} [executionId=${stage.execution.id}, stagedId=${stage.execution.id}]")

    try {
      if (!serverGroup.capacity) {
        splainer.add("short-circuiting out of WaitForCapacityMatchTask because of empty capacity in serverGroup=${serverGroup}")
        return false
      }

      splainer.add("checking if capacity matches (capacity.desired=${serverGroup.capacity.desired}, instances.size()=${instances.size()}) ")
      if (serverGroup.capacity.desired != instances.size()) {
        splainer.add("short-circuiting out of WaitForCapacityMatchTask because expected and current capacity don't match}")
        return false
      }

      if (serverGroup.disabled) {
        splainer.add("capacity matches but server group is disabled, so returning hasSucceeded=true")
        return true
      }

      splainer.add("capacity matches and server group is enabled, so we delegate to WaitForUpInstancesTask to check for healthy instances")
      return WaitForUpInstancesTask.allInstancesMatch(stage, serverGroup, instances, interestingHealthProviderNames, splainer)
    } finally {
      splainer.splain()
    }
  }
}
