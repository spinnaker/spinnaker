/*
 * Copyright 2015 Pivotal Inc.
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

package com.netflix.spinnaker.clouddriver.cf.deploy.ops

import com.netflix.spinnaker.clouddriver.cf.config.CloudFoundryConstants
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.cf.deploy.description.EnableDisableCloudFoundryServerGroupDescription
import com.netflix.spinnaker.clouddriver.cf.security.CloudFoundryClientFactory
import com.netflix.spinnaker.clouddriver.cf.model.CloudFoundryResourceRetriever
import org.cloudfoundry.client.lib.domain.CloudApplication
import org.cloudfoundry.client.lib.domain.InstanceState
import org.cloudfoundry.client.lib.domain.InstancesInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier

/**
 * @author Greg Turnquist
 */
abstract class AbstractEnableDisableAtomicOperation implements AtomicOperation<Void> {

  abstract boolean isDisable()

  abstract String getPhaseName()

  EnableDisableCloudFoundryServerGroupDescription description

  @Autowired
  CloudFoundryClientFactory cloudFoundryClientFactory

  @Autowired
  CloudFoundryResourceRetriever cloudFoundryResourceRetriever

  @Autowired
  @Qualifier('cloudFoundryOperationPoller')
  OperationPoller operationPoller

  AbstractEnableDisableAtomicOperation(EnableDisableCloudFoundryServerGroupDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    String verb = disable ? 'disable' : 'enable'
    String presentParticipling = disable ? 'Disabling' : 'Enabling'

    task.updateStatus phaseName, "Initializing $verb server group operation for $description.serverGroupName in " +
        "$description.zone..."

    if (description.nativeLoadBalancers == null) {
      description.nativeLoadBalancers =
          cloudFoundryResourceRetriever.serverGroupByAccountAndServerGroupName[description.credentials.name][description.serverGroupName].nativeLoadBalancers
    }

    def client = cloudFoundryClientFactory.createCloudFoundryClient(description.credentials, true)

    def app = client.getApplication(description.serverGroupName)
    def loadBalancers = app.envAsMap[CloudFoundryConstants.LOAD_BALANCERS]?.split(',') as List

    if (loadBalancers.empty) {
      throw new RuntimeException("${description.serverGroupName} is not linked to any load balancers and can NOT be ${verb}d")
    }

    def loadBalancerHosts = loadBalancers.collect { loadBalancer ->
      description.nativeLoadBalancers?.find { it?.name == loadBalancer }?.nativeRoute?.name
    }

    if (disable) {
      task.updateStatus phaseName, "Deregistering instances from load balancers..."
      def revisedUris = app.uris - loadBalancerHosts
      client.updateApplicationUris(description.serverGroupName, revisedUris)

      task.updateStatus phaseName, "Stopping server group ${description.serverGroupName}"
      client.stopApplication(description.serverGroupName)

      operationPoller.waitForOperation(
          {client.getApplication(description.serverGroupName)},
          { CloudApplication application -> application.state == CloudApplication.AppState.STOPPED},
          null, task, description.serverGroupName, phaseName)

    } else {
      task.updateStatus phaseName, "Registering instances with load balancers..."
      def revisedUris = app.uris + loadBalancerHosts
      client.updateApplicationUris(description.serverGroupName, revisedUris)

      task.updateStatus phaseName, "Starting server group ${description.serverGroupName}"
      client.startApplication(description.serverGroupName)

      operationPoller.waitForOperation(
          {client.getApplicationInstances(description.serverGroupName)},
          { InstancesInfo instancesInfo -> instancesInfo?.instances?.any {it.state == InstanceState.RUNNING}},
          null, task, description.serverGroupName, phaseName)

    }

    task.updateStatus phaseName, "Done ${presentParticipling.toLowerCase()} server group $description.serverGroupName in $description.zone."
    null
  }

  Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

}
