/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.servergroup.ops

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancer
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureServerGroupDescription
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.EnableDisableDestroyAzureServerGroupDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException

class EnableAzureServerGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "ENABLE_SERVER_GROUP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final EnableDisableDestroyAzureServerGroupDescription description

  EnableAzureServerGroupAtomicOperation(EnableDisableDestroyAzureServerGroupDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "enableServerGroup": { "serverGroupName": "taz-web1-d1-v000", "name": "taz-web1-d1-v000", "account" : "azure-cred1", "cloudProvider" : "azure", "appName" : "taz", "regions": ["westus"], "credentials": "azure-cred1" }} ]' localhost:7002/azure/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Enable Azure Server Group Operation..."

    def region = description.region
    if (description.serverGroupName) description.name = description.serverGroupName
    if (!description.application) description.application = description.appName ?: Names.parseName(description.name).app
    task.updateStatus BASE_PHASE, "Enabling server group ${description.name} " + "in ${region}..."

    if (!description.credentials) {
      throw new IllegalArgumentException("Unable to resolve credentials for the selected Azure account.")
    }

    def errList = new ArrayList<String>()

    try {
      String resourceGroupName = AzureUtilities.getResourceGroupName(description.application, region)
      AzureServerGroupDescription serverGroupDescription = description.credentials.computeClient.getServerGroup(resourceGroupName, description.name)

      if (!serverGroupDescription) {
        task.updateStatus(BASE_PHASE, "Enable Server Group Operation failed: could not find server group ${description.name} in ${region}")
        errList.add("could not find server group ${description.name} in ${region}")
      } else {
        try {
          if(serverGroupDescription.loadBalancerType == AzureLoadBalancer.AzureLoadBalancerType.AZURE_LOAD_BALANCER.toString()) {
            if (description.credentials.networkClient.isServerGroupWithLoadBalancerDisabled(resourceGroupName, serverGroupDescription.loadBalancerName, serverGroupDescription.name)) {
              description
                .credentials
                .networkClient
                .enableServerGroupWithLoadBalancer(resourceGroupName, serverGroupDescription.loadBalancerName, serverGroupDescription.name)
              task.updateStatus BASE_PHASE, "Done enabling Azure server group ${serverGroupDescription.name} in ${region}."
            } else {
              task.updateStatus BASE_PHASE, "Azure server group ${serverGroupDescription.name} in ${region} is already enabled."
            }
          } else {
            if (description.credentials.networkClient.isServerGroupDisabled(resourceGroupName, serverGroupDescription.appGatewayName, serverGroupDescription.name)) {
              description
                .credentials
                .networkClient
                .enableServerGroup(resourceGroupName, serverGroupDescription.appGatewayName, serverGroupDescription.name)
              task.updateStatus BASE_PHASE, "Done enabling Azure server group ${serverGroupDescription.name} in ${region}."
            } else {
              task.updateStatus BASE_PHASE, "Azure server group ${serverGroupDescription.name} in ${region} is already enabled."
            }
          }

        } catch (Exception e) {
          task.updateStatus(BASE_PHASE, "Enabling of server group ${description.name} failed: ${e.message}")
          errList.add("Failed to enable server group ${description.name}: ${e.message}")
        }
      }
    } catch (Exception e) {
      task.updateStatus(BASE_PHASE, "Enabling server group ${description.name} failed: ${e.message}")
      errList.add("Failed to enable server group ${description.name}: ${e.message}")
    }

    if (errList.isEmpty()) {
      task.updateStatus BASE_PHASE, "Enable Azure Server Group Operation for ${description.name} succeeded."
    }
    else {
      errList.add(" Go to Azure Portal for more info")
      throw new AtomicOperationException("Failed to enable ${description.name}", errList)
    }

    null
  }
}
