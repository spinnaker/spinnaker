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
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.DestroyServerGroupTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.DisableServerGroupTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.WaitForAllInstancesNotUpTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.WaitForDestroyedServerGroupTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DestroyServerGroupStage extends TargetServerGroupLinearStageSupport {
  static final String PIPELINE_CONFIG_TYPE = "destroyServerGroup"

  @Autowired
  DisableServerGroupStage disableServerGroupStage

  DestroyServerGroupStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    try {
      composeTargets(stage)

      List steps = []

      //TODO(cfieber) - need to remove this once proper enable / disable has been implemented in Titus.
      if (!stage.context.cloudProvider || stage.context.cloudProvider != 'titan') {
        steps += [
          buildStep(stage, "disableServerGroup", DisableServerGroupTask),
          buildStep(stage, "monitorServerGroup", MonitorKatoTask),
          buildStep(stage, "waitForNotUpInstances", WaitForAllInstancesNotUpTask),
          buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask),
        ]
      }

      steps + [
        buildStep(stage, "destroyServerGroup", DestroyServerGroupTask),
        buildStep(stage, "monitorServerGroup", MonitorKatoTask),
        buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask),
        buildStep(stage, "waitForDestroyedServerGroup", WaitForDestroyedServerGroupTask),
      ]

    } catch (TargetServerGroup.NotFoundException ignored) {
      [buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask)]
    }
  }
}
