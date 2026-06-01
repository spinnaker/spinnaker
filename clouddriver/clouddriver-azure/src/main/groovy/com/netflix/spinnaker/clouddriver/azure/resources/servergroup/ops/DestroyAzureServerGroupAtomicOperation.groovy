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
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureServerGroupDescription
import com.netflix.spinnaker.clouddriver.cache.OnDemandCacheUpdater
import com.netflix.spinnaker.clouddriver.cache.OnDemandType
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.EnableDisableDestroyAzureServerGroupDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException
import groovy.util.logging.Slf4j

@Slf4j
class DestroyAzureServerGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DESTROY_SERVER_GROUP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final EnableDisableDestroyAzureServerGroupDescription description
  private final List<OnDemandCacheUpdater> onDemandCacheUpdaters

  DestroyAzureServerGroupAtomicOperation(EnableDisableDestroyAzureServerGroupDescription description,
                                         List<OnDemandCacheUpdater> onDemandCacheUpdaters = []) {
    this.description = description
    this.onDemandCacheUpdaters = onDemandCacheUpdaters
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "destroyServerGroup": { "serverGroupName": "taz-web1-d1-v000", "name": "taz-web1-d1-v000", "account" : "azure-cred1", "cloudProvider" : "azure", "appName" : "taz", "regions": ["westus"], "credentials": "azure-cred1" }} ]' localhost:7002/azure/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Destroy Azure Server Group Operation..."

    def region = description.region
    if (description.serverGroupName) description.name = description.serverGroupName
    if (!description.application) description.application = description.appName ?: Names.parseName(description.name).app
    task.updateStatus BASE_PHASE, "Destroying server group ${description.name} " + "in ${region}..."

    if (!description.credentials) {
      throw new IllegalArgumentException("Unable to resolve credentials for the selected Azure account.")
    }

    def errList = new ArrayList<String>()

    try {
      String resourceGroupName = AzureUtilities.getResourceGroupName(description.application, region)
      AzureServerGroupDescription serverGroupDescription = description.credentials.computeClient.getServerGroup(resourceGroupName, description.name)

      if (!serverGroupDescription) {
        task.updateStatus(BASE_PHASE, "Destroy Server Group Operation failed: could not find server group ${description.name} in ${region}")
        errList.add("could not find server group ${description.name} in ${region}")
      } else {
        try {
          description
            .credentials
            .computeClient
            .destroyServerGroup(resourceGroupName, description.name)

          task.updateStatus BASE_PHASE, "Done destroying Azure server group ${description.name} in ${region}."
        } catch (Exception e) {
          task.updateStatus(BASE_PHASE, "Deletion of server group ${description.name} failed: ${e.message}")
          errList.add("Failed to delete server group ${description.name}: ${e.message}")
        }

        // Clean-up the storage account and the subnet that were attached to the server group
        // Backend pools are static and do not need to be removed on server group destroy
        if (errList.isEmpty()) {
          // Delete storage accounts if any
          serverGroupDescription.storageAccountNames?.each { def storageAccountName ->
            task.updateStatus(BASE_PHASE, "Deleting storage account ${storageAccountName} " + "in ${region}...")
            try {
              description
                .credentials
                .storageClient
                .deleteStorageAccount(resourceGroupName, storageAccountName)

              task.updateStatus(BASE_PHASE, "Deletion of Azure storage account ${storageAccountName} in ${region} has succeeded.")
            } catch (Exception e) {
              task.updateStatus(BASE_PHASE, "Deletion of Azure storage account ${storageAccountName} failed: ${e.message}")
              errList.add("Failed to delete storage account ${storageAccountName}: ${e.message}")
            }
          }

          // Delete load balancer attached to server group
          if (serverGroupDescription.enableInboundNAT) {
            String loadBalancerName = AzureUtilities.LB_NAME_PREFIX + serverGroupDescription.name
            task.updateStatus(BASE_PHASE, "Deleting load balancer ${loadBalancerName} " + "in ${region}...")
            try {
              description
                .credentials
                .networkClient
                .deleteLoadBalancer(resourceGroupName, loadBalancerName)

              task.updateStatus(BASE_PHASE, "Deletion of Azure load balancer ${loadBalancerName} in ${region} has succeeded.")
            } catch (Exception e) {
              task.updateStatus(BASE_PHASE, "Deletion of Azure load balancer ${loadBalancerName} failed: ${e.message}")
              errList.add("Failed to delete ${loadBalancerName}: ${e.message}")
            }
          }

          // Delete subnet attached to server group
          if (serverGroupDescription.hasNewSubnet && serverGroupDescription.subnetId) {
            String subnetName = AzureUtilities.getNameFromResourceId(serverGroupDescription.subnetId)
            String virtualNetworkName = AzureUtilities.getVirtualNetworkName(resourceGroupName)
            task.updateStatus(BASE_PHASE, "Deleting subnet ${subnetName} " + "in ${region}...")
            try {
              description
                .credentials
                .networkClient
                .deleteSubnet(resourceGroupName, virtualNetworkName, subnetName)

              task.updateStatus(BASE_PHASE, "Deletion of subnet ${subnetName} in ${region} has succeeded.")
            } catch (Exception e) {
              task.updateStatus(BASE_PHASE, "Deletion of subnet ${subnetName} in ${virtualNetworkName} failed: ${e.message}")
              errList.add("Failed to delete subnet ${subnetName} in ${virtualNetworkName}: ${e.message}")
            }
          }
        }
      }
    } catch (Exception e) {
      task.updateStatus(BASE_PHASE, "Destroying server group ${description.name} failed: ${e.message}")
      errList.add("Failed to destroy server group ${description.name}: ${e.message}")
    }

    if (errList.isEmpty()) {
      task.updateStatus BASE_PHASE, "Destroy Azure Server Group Operation for ${description.name} succeeded."
      onDemandCacheUpdaters.each {
        if (it.handles(OnDemandType.ServerGroup, AzureCloudProvider.ID)) {
          try {
            it.handle(OnDemandType.ServerGroup, AzureCloudProvider.ID, [
              serverGroupName: description.name,
              account        : description.accountName,
              region         : region
            ])
          } catch (Exception e) {
            // best-effort: background sweep reconciles within ~60s regardless
            log.warn("Failed to evict ${description.name} from on-demand cache after destroy: ${e.message}")
          }
        }
      }
    } else {
      errList.add(" Go to Azure Portal for more info")
      throw new AtomicOperationException("Failed to destroy ${description.name}", errList)
    }

    null
  }
}
