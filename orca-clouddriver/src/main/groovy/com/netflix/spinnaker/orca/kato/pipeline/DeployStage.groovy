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

package com.netflix.spinnaker.orca.kato.pipeline

import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForUpInstancesTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.kato.pipeline.strategy.DeployStrategyStage
import com.netflix.spinnaker.orca.kato.tasks.CreateDeployTask
import com.netflix.spinnaker.orca.kato.tasks.DiffTask
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.TaskNode.TaskDefinition
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
@Deprecated
class DeployStage extends DeployStrategyStage {
  public static final String PIPELINE_CONFIG_TYPE = "linearDeploy"

  DeployStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Autowired(required = false)
  List<DiffTask> diffTasks

  @VisibleForTesting
  @Override
  protected List<TaskDefinition> basicTasks(Stage stage) {
    def tasks = [
      TaskNode.task("createDeploy", CreateDeployTask),
      TaskNode.task("monitorDeploy", MonitorKatoTask),
      TaskNode.task("forceCacheRefresh", ServerGroupCacheForceRefreshTask),
      TaskNode.task("waitForUpInstances", WaitForUpInstancesTask),
      TaskNode.task("forceCacheRefresh", ServerGroupCacheForceRefreshTask)
    ]

    if (diffTasks) {
      diffTasks.each { DiffTask diffTask ->
        tasks << TaskNode.task(getDiffTaskName(diffTask.class.simpleName), diffTask.class)
      }
    }

    return tasks
  }

  private String getDiffTaskName(String className) {
    try {
      className = className[0].toLowerCase() + className.substring(1)
      className = className.replaceAll("Task", "")
    } catch (e) {}
    return className
  }
}
