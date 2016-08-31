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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.AbstractDeployStrategyStage
import com.netflix.spinnaker.orca.clouddriver.tasks.DetermineHealthProvidersTask
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForUpInstancesTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.CloneServerGroupTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

@Slf4j
@Component
class CloneServerGroupStage extends AbstractDeployStrategyStage {

  public static final String PIPELINE_CONFIG_TYPE = "cloneServerGroup"

  CloneServerGroupStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  CloneServerGroupStage(String type) {
    super(type)
  }

  @Override
  List<Step> basicSteps(Stage stage) {
    [
      buildStep(stage, "cloneServerGroup", CloneServerGroupTask),
      buildStep(stage, "monitorDeploy", MonitorKatoTask),
      buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask),
      buildStep(stage, "waitForUpInstances", WaitForUpInstancesTask),
      buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask),
    ]
  }
}
