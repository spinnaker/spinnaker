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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupLinearStageSupport
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.*
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DestroyServerGroupStage extends TargetServerGroupLinearStageSupport {
  static final String PIPELINE_CONFIG_TYPE = "destroyServerGroup"

  @Autowired
  DisableServerGroupStage disableServerGroupStage

  @Override
  void taskGraph(Stage stage, TaskNode.Builder builder) {
    try {
      builder
        .withTask("disableServerGroup", DisableServerGroupTask)
        .withTask("monitorServerGroup", MonitorKatoTask)
        .withTask("waitForNotUpInstances", WaitForAllInstancesNotUpTask)
        .withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask)
        .withTask("destroyServerGroup", DestroyServerGroupTask)
        .withTask("monitorServerGroup", MonitorKatoTask)
        .withTask("waitForDestroyedServerGroup", WaitForDestroyedServerGroupTask)
    } catch (TargetServerGroup.NotFoundException ignored) {
      builder
        .withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask)
    }
  }
}
