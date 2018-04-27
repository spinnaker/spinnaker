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

package com.netflix.spinnaker.orca.clouddriver.pipeline.cluster;

import com.netflix.spinnaker.orca.clouddriver.tasks.DetermineHealthProvidersTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractClusterWideClouddriverTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractWaitForClusterWideClouddriverTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;

import java.beans.Introspector;

public abstract class AbstractClusterWideClouddriverOperationStage implements StageDefinitionBuilder {
  protected abstract Class<? extends AbstractClusterWideClouddriverTask> getClusterOperationTask();

  protected abstract Class<? extends AbstractWaitForClusterWideClouddriverTask> getWaitForTask();

  protected static String getStepName(String taskClassSimpleName) {
    if (taskClassSimpleName.endsWith("Task")) {
      return taskClassSimpleName.substring(0, taskClassSimpleName.length() - "Task".length());
    }
    return taskClassSimpleName;
  }

  @Override
  public void taskGraph(Stage stage, TaskNode.Builder builder) {
    stage.resolveStrategyParams();
    Class<? extends AbstractClusterWideClouddriverTask> operationTask = getClusterOperationTask();
    String name = getStepName(operationTask.getSimpleName());
    String opName = Introspector.decapitalize(name);
    Class<? extends AbstractWaitForClusterWideClouddriverTask> waitTask = getWaitForTask();
    String waitName = Introspector.decapitalize(getStepName(waitTask.getSimpleName()));

    builder
      .withTask("determineHealthProviders", DetermineHealthProvidersTask.class)
      .withTask(opName, operationTask)
      .withTask("monitor" + name, MonitorKatoTask.class)
      .withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask.class)
      .withTask(waitName, waitTask)
      .withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask.class);
  }
}
