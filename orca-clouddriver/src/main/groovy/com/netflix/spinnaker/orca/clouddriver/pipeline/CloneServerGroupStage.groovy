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

package com.netflix.spinnaker.orca.clouddriver.pipeline

import com.netflix.spinnaker.orca.clouddriver.tasks.CloneServerGroupTask
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.clouddriver.tasks.WaitForUpInstancesTask
import com.netflix.spinnaker.orca.kato.pipeline.strategy.DeployStrategyStage
import com.netflix.spinnaker.orca.kato.tasks.DiffTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class CloneServerGroupStage extends DeployStrategyStage {

  public static final String PIPELINE_CONFIG_TYPE = "cloneServerGroup"

  @Autowired(required = false)
  List<DiffTask> diffTasks

  CloneServerGroupStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  CloneServerGroupStage(String type) {
    super(type)
  }

  @Override
  List<Step> basicSteps(Stage stage) {
    def steps = []

    steps << buildStep(stage, "cloneServerGroup", CloneServerGroupTask)
    steps << buildStep(stage, "monitorDeploy", MonitorKatoTask)
    steps << buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask)
    steps << buildStep(stage, "waitForUpInstances", WaitForUpInstancesTask)
    steps << buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask)

    if (diffTasks) {
      diffTasks.each { DiffTask diffTask ->
        steps << buildStep(stage, getDiffTaskName(diffTask.class.simpleName), diffTask.class)
      }
    }

    return steps
  }

  private String getDiffTaskName(String className) {
    try {
      className = className[0].toLowerCase() + className.substring(1)
      className = className.replaceAll("Task", "")
    } catch (e) {}
    return className
  }
}