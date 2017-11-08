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

package com.netflix.spinnaker.orca.clouddriver.pipeline.cluster

import java.beans.Introspector
import com.netflix.spinnaker.orca.clouddriver.tasks.DetermineHealthProvidersTask
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractClusterWideClouddriverTask
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractWaitForClusterWideClouddriverTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Stage

abstract class AbstractClusterWideClouddriverOperationStage implements StageDefinitionBuilder {
  abstract Class<? extends AbstractClusterWideClouddriverTask> getClusterOperationTask()

  abstract Class<? extends AbstractWaitForClusterWideClouddriverTask> getWaitForTask()

  protected static String getStepName(String taskClassSimpleName) {
    if (taskClassSimpleName.endsWith("Task")) {
      return taskClassSimpleName.substring(0, taskClassSimpleName.length() - "Task".length())
    }
    return taskClassSimpleName
  }

  @Override
  void taskGraph(Stage stage, TaskNode.Builder builder) {
    stage.resolveStrategyParams()
    def operationTask = clusterOperationTask
    String name = getStepName(operationTask.simpleName)
    String opName = Introspector.decapitalize(name)
    def waitTask = waitForTask
    String waitName = Introspector.decapitalize(getStepName(waitTask.simpleName))

    builder
      .withTask("determineHealthProviders", DetermineHealthProvidersTask)
      .withTask(opName, operationTask)
      .withTask("monitor${name}", MonitorKatoTask)
      .withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask)
      .withTask(waitName, waitTask)
      .withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask)
  }
}
