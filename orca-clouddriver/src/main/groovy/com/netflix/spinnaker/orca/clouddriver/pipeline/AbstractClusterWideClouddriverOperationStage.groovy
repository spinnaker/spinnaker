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

import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractClusterWideClouddriverTask
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractWaitForClusterWideClouddriverTask
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step

import java.beans.Introspector

abstract class AbstractClusterWideClouddriverOperationStage extends LinearStage {

  AbstractClusterWideClouddriverOperationStage(String name) {
    super(name)
  }

  abstract Class<? extends AbstractClusterWideClouddriverTask> getClusterOperationTask()
  abstract Class<? extends AbstractWaitForClusterWideClouddriverTask> getWaitForTask()

  protected String getStepName(String taskClassSimpleName) {
    if (taskClassSimpleName.endsWith("Task")) {
      return taskClassSimpleName.substring(0, taskClassSimpleName.length() - "Task".length())
    }
    return taskClassSimpleName
  }

  @Override
  List<Step> buildSteps(Stage stage) {
    def operationTask = clusterOperationTask
    String name = getStepName(operationTask.simpleName)
    String opName = Introspector.decapitalize(name)
    def waitTask = waitForTask
    String waitName = Introspector.decapitalize(getStepName(waitTask.simpleName))
    [buildStep(stage, opName, operationTask),
     buildStep(stage, "monitor${name}", MonitorKatoTask),
     buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask),
     buildStep(stage, waitName, waitTask),
     buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask),
    ]
  }
}
