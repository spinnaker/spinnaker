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
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.App

/**
 * Methods for monitoring the state of a DCOS deployment
 */
interface DcosDeploymentMonitor {

  /**
   * @param dcosClient a DCOS client instance (cannot be null).
   * @param marathonApp The Marathon application to monitor (cannot be null).
   * @param deploymentId The corresponding deployment id to monitor (cannot be null).
   * @param timeoutSeconds The timeout interval in seconds (may be null).
   * @param task The task to be updated (may be null).
   * @param basePhase The phase
   * @return DcosDeploymentResult indicating the success or failure of the deployment
   * @throws com.netflix.spinnaker.clouddriver.exceptions.OperationTimedOutException if the timeout interval elapses.
   */
  DcosDeploymentResult waitForAppDeployment(DCOS dcosClient, App marathonApp, String deploymentId,
                                            Long timeoutSeconds, Task task, String basePhase)

  /**
   * @param dcosClient a DCOS client instance (cannot be null).
   * @param appId The Marathon application ID to monitor (cannot be null).
   * @param timeoutSeconds The timeout interval in seconds (may be null).
   * @param task The task to be updated (may be null).
   * @param basePhase The phase
   * @throws com.netflix.spinnaker.clouddriver.exceptions.OperationTimedOutException if the timeout interval elapses.
   */
  void waitForAppDestroy(DCOS dcosClient, String appId,
                         Long timeoutSeconds, Task task, String basePhase)

  /**
   * @param dcosClient a DCOS client instance (cannot be null).
   * @param appId The Marathon application ID to monitor (cannot be null).
   * @param deploymentId The corresponding deployment id to monitor (cannot be null).
   * @param target The target number of instances to check for (cannot be null).
   * @param timeoutSeconds The timeout interval in seconds (may be null).
   * @param task The task to be updated (may be null).
   * @param basePhase The phase
   * @throws com.netflix.spinnaker.clouddriver.exceptions.OperationTimedOutException if the timeout interval elapses.
   */
  void waitForAppResize(DCOS dcosClient, String appId, String deploymentId, int target,
                         Long timeoutSeconds, Task task, String basePhase)


  static class DcosDeploymentResult {
    boolean success
    Optional<App> deployedApp
  }
}
