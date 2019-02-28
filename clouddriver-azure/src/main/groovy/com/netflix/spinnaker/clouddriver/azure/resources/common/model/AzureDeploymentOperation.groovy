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
import com.microsoft.azure.management.resources.DeploymentOperation
import com.microsoft.azure.management.resources.implementation.DeploymentOperationInner
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import com.netflix.spinnaker.clouddriver.data.task.Task
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@CompileStatic
@Slf4j
class AzureDeploymentOperation {

  static final Integer AZURE_DEPLOYMENT_OPERATION_STATUS_RETRIES_MAX = 1000
  static ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  static AzureDeploymentOperation getObjectFromJson(String responseContent) {
    mapper.readValue(responseContent, AzureDeploymentOperation.class)
  }

  /**
   * Return a collection of deployment related errors (empty list if deployment was successful)
   * @param task the current task to be updated
   * @param resourceGroupName the resource group where we try to deploy
   * @param deploymentName the name of the deployment
   * @return a Collection of error messages capturing deployment related errors
   */
  static List<String> checkDeploymentOperationStatus(Task task, String opsName, AzureCredentials creds, String resourceGroupName, String deploymentName) {
    def errList = new ArrayList<String>()
    Map<String, Boolean> resourceCompletedState = new HashMap<String, Boolean>()
    String deploymentState = AzureUtilities.ProvisioningState.DEPLOYING
    Integer checkDeployment = 0

    while (checkDeployment < AZURE_DEPLOYMENT_OPERATION_STATUS_RETRIES_MAX) {
      deploymentState = creds.resourceManagerClient.getDeployment(resourceGroupName, deploymentName).inner().properties().provisioningState()

      creds.resourceManagerClient.getDeploymentOperations(resourceGroupName, deploymentName).each { DeploymentOperation d ->

        // NOTE: With SDK 1.0 we've started getting an extra deployment operation returned. It doesn't show in
        // the portal and the target resource listed is null so we don't know what resource this operation is\
        // acting on. The operations for all the resources created in the deployment do get returned and we can
        // identify which operation is for what resource. So for now, until we get clarity from the SDK, we will
        // ignore those operations that have a null target resource.
        DeploymentOperationInner inner = d.inner()
        if (inner.properties().targetResource()) {
          if (!resourceCompletedState.containsKey(inner.id())) {
            resourceCompletedState[inner.id()] = false
          }

          if (inner.properties().provisioningState() == AzureUtilities.ProvisioningState.SUCCEEDED) {

            if (!resourceCompletedState[inner.id()]) {
              task.updateStatus opsName, String.format("Resource %s created", inner.properties().targetResource().resourceName())
              resourceCompletedState[inner.id()] = true
            }
          } else if (inner.properties().provisioningState() == AzureUtilities.ProvisioningState.FAILED) {
            if (!resourceCompletedState[inner.id()]) {

              //String statusMessage = updatedDeploymentOperation?.value?.first()?.properties?.statusMessage?.error?.message
              String err = "Failed to create resource ${inner.properties().targetResource().resourceName()}: "
              err += inner.properties().statusMessage() ? inner.properties().statusMessage() : "See Azure Portal for more information."
              task.updateStatus opsName, err
              resourceCompletedState[inner.id()] = true
              errList.add(err)
            }
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

    if (deploymentState != AzureUtilities.ProvisioningState.SUCCEEDED) {
      String err = "Failed to deploy ${deploymentName}; see Azure Portal for more information'}"
      task.updateStatus opsName, err
      errList.add(err)
    }

    errList
  }

  private static boolean deploymentIsRunning(String deploymentState) {
    deploymentState != AzureUtilities.ProvisioningState.CANCELED &&
      deploymentState != AzureUtilities.ProvisioningState.DELETED &&
      deploymentState != AzureUtilities.ProvisioningState.FAILED &&
      deploymentState != AzureUtilities.ProvisioningState.SUCCEEDED
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
