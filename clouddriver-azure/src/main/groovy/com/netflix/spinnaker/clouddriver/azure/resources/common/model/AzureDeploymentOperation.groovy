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

package com.netflix.spinnaker.clouddriver.azure.resources.common.model

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.azure.management.resources.models.DeploymentOperation
import com.netflix.spinnaker.clouddriver.azure.client.AzureResourceManagerClient
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import com.netflix.spinnaker.clouddriver.data.task.Task
import groovy.util.logging.Slf4j

@Slf4j
class AzureDeploymentOperation {
  static final Integer AZURE_DEPLOYMENT_OPERATION_STATUS_RETRIES_MAX = 1000

  /**
   * Return a collection of deployment related errors (empty list if deployment was successful)
   * @param task the current task to be updated
   * @param creds the credentials to use when communicating to the Azure subscription(s)
   * @param resourceGroupName the resource group where we try to deploy
   * @param deploymentName the name of the deployment
   * @return a Collection of error messages capturing deployment related errors
   */
  static List<String> checkDeploymentOperationStatus(Task task, String opsName, AzureCredentials creds, String resourceGroupName, String deploymentName) {
    def errList = new ArrayList<String>()
    Map<String, Boolean> resourceCompletedState = new HashMap<String, Boolean>()
    String deploymentState = AzureResourceManagerClient.DeploymentState.DEPLOYING
    Integer checkDeployment = 0

    while (checkDeployment < AZURE_DEPLOYMENT_OPERATION_STATUS_RETRIES_MAX) {
      deploymentState = creds.resourceManagerClient.getDeployment(creds, resourceGroupName, deploymentName).properties.provisioningState

      creds.resourceManagerClient.getDeploymentOperations(creds, resourceGroupName, deploymentName).each {DeploymentOperation d ->

        if (!resourceCompletedState.containsKey(d.id)){
          resourceCompletedState[d.id] = false
        }
        if (d.properties.provisioningState == AzureResourceManagerClient.DeploymentState.SUCCEEDED) {
          if (!resourceCompletedState[d.id]) {
            task.updateStatus opsName, String.format("Resource %s created", d.properties.targetResource.resourceName)
            resourceCompletedState[d.id] = true
          }
        }
        else if (d.properties.provisioningState == AzureResourceManagerClient.DeploymentState.FAILED) {
          if (!resourceCompletedState[d.id]) {

            // Work around for Azure SDK bug while retrieving the status message
            // TODO remove the REST call and use d.properties.satusMessage.error.message instead

            AzureDeploymentOperation updatedDeploymentOperation = creds.resourceManagerClient.
              getAzureRESTJson(
                              creds,
                              "https://management.azure.com",
                              "resourceGroups/${resourceGroupName}/providers/Microsoft.Resources/deployments/${deploymentName}/operations",
                              ["api-version=2015-11-01"],
                              AzureDeploymentOperation.class
                              )
            String statusMessage = updatedDeploymentOperation?.value?.first()?.properties?.statusMessage?.error?.message
            String err = "Failed to create resource ${d.properties.targetResource.resourceName}: "
            err += statusMessage?:"See Azure Portal for more information."
            task.updateStatus opsName, err
            resourceCompletedState[d.id] = true
            errList.add(err)
          }
        }
      }

      if (deploymentIsRunning(deploymentState)) {
        // Add a delay in order to avoid making too many network calls and allow Azure to make some progress on the deployment
        // log current call to sleep() in order to get a sense of how much the delay should be
        log.info("checkDeploymentOperationStatus -> SLEEP")
        sleep(500)
        checkDeployment += 1
      }
      else {
        checkDeployment = AZURE_DEPLOYMENT_OPERATION_STATUS_RETRIES_MAX
      }
    }

    if (deploymentState != AzureResourceManagerClient.DeploymentState.SUCCEEDED) {
      String err = "Failed to deploy ${deploymentName}; see Azure Portal for more information'}"
      task.updateStatus opsName, err
      errList.add(err)
    }

    errList
  }

  private static boolean deploymentIsRunning(String deploymentState) {
    deploymentState != AzureResourceManagerClient.DeploymentState.CANCELED &&
      deploymentState != AzureResourceManagerClient.DeploymentState.DELETED &&
      deploymentState != AzureResourceManagerClient.DeploymentState.FAILED &&
      deploymentState != AzureResourceManagerClient.DeploymentState.SUCCEEDED
  }

  List<OpsValue> value

  public static class OpsValue {
    String id
    String operationId
    OpsProperties properties

  }

  public static class OpsProperties {
    String provisioningOperation
    String provisioningState
    String timestamp
    String duration
    String trackingId
    String statusCode
    StatusMessage statusMessage
    TargetResource targetResource

  }

  public static class StatusMessage {
    StatusMessageError error
  }

  public static class StatusMessageError {
    String code
    String message
    List<String> details = []
  }

  public static class TargetResource {
    String id
    String resourceType
    String resourceName
  }
}
