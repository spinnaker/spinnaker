/*
 * Copyright 2017 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.deploy.util.monitor

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.App

class PollingDcosDeploymentMonitor implements DcosDeploymentMonitor {

  private final OperationPoller operationPoller

  PollingDcosDeploymentMonitor(final OperationPoller operationPoller) {
    this.operationPoller = operationPoller
  }

  @Override
  DcosDeploymentResult waitForAppDeployment(DCOS dcosClient, App marathonApp, String deploymentId, Long timeoutSeconds, Task task, String basePhase) {

    // Wait for the deployment to complete. Either the App will not return (404), meaning the deployment failed, or
    // the deployment id will be removed from the apps deployment list, meaning the deployment succeeded.
    Optional<App> maybeApp = operationPoller.waitForOperation(
      { dcosClient.maybeApp(marathonApp.id) },
      { Optional<App> retrievedApp ->
        !retrievedApp.isPresent() || !retrievedApp.get().deployments.find {
          it.id == deploymentId
        }
      },
      timeoutSeconds, task, marathonApp.id, basePhase) as Optional<App>

    new DcosDeploymentResult(success: maybeApp.isPresent(), deployedApp: maybeApp)
  }

  @Override
  void waitForAppDestroy(DCOS dcosClient, String appId, Long timeoutSeconds, Task task, String basePhase) {
    operationPoller.waitForOperation(
            { dcosClient.maybeApp(appId) },
            { Optional<App> retrievedApp -> !retrievedApp.isPresent() },
            timeoutSeconds, task, appId, basePhase)
  }

  @Override
  void waitForAppResize(DCOS dcosClient, String appId, String deploymentId, int target, Long timeoutSeconds, Task task, String basePhase) {
    operationPoller.waitForOperation(
            { dcosClient.maybeApp(appId) },
            { Optional<App> retrievedApp -> retrievedApp.isPresent() && retrievedApp.get().tasks.size() == target && !retrievedApp.get().deployments.find {it.id == deploymentId } },
            timeoutSeconds, task, appId, basePhase)
  }
}
