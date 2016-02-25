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

package com.netflix.spinnaker.clouddriver.azure.client

import com.microsoft.azure.CloudException
import com.microsoft.azure.credentials.ApplicationTokenCredentials
import com.microsoft.azure.management.resources.ResourceManagementClientImpl
import com.microsoft.azure.management.resources.models.Deployment
import com.microsoft.azure.management.resources.models.DeploymentExtended
import com.microsoft.azure.management.resources.models.DeploymentMode
import com.microsoft.azure.management.resources.models.DeploymentOperation
import com.microsoft.azure.management.resources.models.DeploymentProperties
import com.microsoft.azure.management.resources.models.ResourceGroup
import com.microsoft.azure.management.resources.ResourceManagementClient
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import okhttp3.logging.HttpLoggingInterceptor

@CompileStatic
class AzureResourceManagerClient extends AzureBaseClient {

  private final ResourceManagementClient client

  /**
   * Client for communication with Azure Resource Management
   * @param subscriptionId - Azure Subscription ID
   * @param credentials - Token Credentials to use for communication with Auzre
   */
  AzureResourceManagerClient(String subscriptionId, ApplicationTokenCredentials credentials) {
    super(subscriptionId)
    this.client = initializeClient(credentials)
  }

  /**
   *
   * @param credentials
   * @param template
   * @param resourceGroupName
   * @param region
   * @param resourceName
   * @param templateParams
   * @return
   */
  DeploymentExtended createResourceFromTemplate(AzureCredentials credentials,
                                                String template,
                                                String resourceGroupName,
                                                String region,
                                                String resourceName,
                                                Map<String, String> templateParams = [:]) {

    // TODO validate that all callers invoke this themselves, then remove this call
    initializeResourceGroupAndVNet(credentials, resourceGroupName, null, region)

    String deploymentName = resourceName + AzureUtilities.NAME_SEPARATOR +"deployment"
    if (!templateParams['location']) {
      templateParams['location'] = region
    }

    DeploymentExtended deployment = createTemplateDeployment(client,
      resourceGroupName,
      DeploymentMode.INCREMENTAL,
      deploymentName,
      template,
      templateParams)

    deployment
  }

  /**
   *
   * @param creds
   * @param resourceGroupName
   * @param region
   * @return
   */
  ResourceGroup createResourceGroup(String resourceGroupName, String region) {
    try {
      //Create an instance of the resource group to be passed as the "parameters" for the createOrUpdate method
      //Set appropriate attributes of instance to define resource group
      ResourceGroup resourceGroup = new ResourceGroup()
      resourceGroup.setLocation(region)

      client.getResourceGroupsOperations().createOrUpdate(resourceGroupName,resourceGroup).body

    } catch (e) {
      throw new RuntimeException("Unable to create Resource Group ${resourceGroupName} in region ${region}", e)
    }
  }

  /**
   *
   * @param creds
   * @param resourceGroupName
   * @param virtualNetworkName
   * @param region
   * @return
   */
  ResourceGroup initializeResourceGroupAndVNet(AzureCredentials creds, String resourceGroupName, String virtualNetworkName, String region) {
    ResourceGroup resourceGroupParameters = new ResourceGroup()
    resourceGroupParameters.setLocation(region)
    ResourceGroup resourceGroup
    if (!resourceGroupExists(resourceGroupName)) {
      resourceGroup = createResourceGroup(resourceGroupName, region)
    } else {
      resourceGroup = client.getResourceGroupsOperations().get(resourceGroupName).body
    }

    initializeResourceGroupVNet(creds, resourceGroupName, virtualNetworkName, region)

    resourceGroup
  }

  /**
   *
   * @param creds
   * @param resourceGroupName
   * @return
   */
  boolean resourceGroupExists(String resourceGroupName) {
    client.getResourceGroupsOperations().checkExistence(resourceGroupName).body
  }

  /**
   *
   * @param creds
   * @param resourceGroupName
   * @param deploymentName
   * @param operationCount
   * @return
   */
  List<DeploymentOperation> getDeploymentOperations(String resourceGroupName,
                                                    String deploymentName,
                                                    Integer operationCount = 10) {
    client.getDeploymentOperationsOperations().list(resourceGroupName, deploymentName, operationCount).body
  }

  /**
   *
   * @param creds
   * @param resourceGroupName
   * @param deploymentName
   * @return
   */
  DeploymentExtended getDeployment(String resourceGroupName, String deploymentName) {
    client.getDeploymentsOperations().get(resourceGroupName, deploymentName).body
  }

  /**
   * Azure Health Check
   */
  void healthCheck() {
    try {
      client.getResourcesOperations().list(null, 1)
    }
    catch (Exception e) {
      throw new Exception("Unable to ping Azure", e)
    }
  }

  /**
   *
   * @param creds
   * @param resourceGroupName
   * @param virtualNetworkName
   * @param region
   */
  private static void initializeResourceGroupVNet(AzureCredentials creds, String resourceGroupName, String virtualNetworkName = null, String region) {
    def vNetName = virtualNetworkName ?
      virtualNetworkName : AzureUtilities.getVirtualNetworkName(resourceGroupName)

    try {
      creds.networkClient.getVirtualNetwork(resourceGroupName, vNetName)
      }
    catch (CloudException ignore) {
      // Assumes that a cloud exception means that the rest call failed to locate the vNet
      creds.networkClient.createVirtualNetwork(resourceGroupName, vNetName, region)
    }
  }

  /**
   *
   * @param resourceManagementClient
   * @param resourceGroupName
   * @param deploymentMode
   * @param deploymentName
   * @param template
   * @param templateParameters
   * @return Azure Deployment object
   */
  private static DeploymentExtended createTemplateDeployment(
    ResourceManagementClient resourceManagementClient,
    String resourceGroupName,
    DeploymentMode deploymentMode,
    String deploymentName,
    String template,
    Map<String, String> templateParameters) {

    DeploymentProperties deploymentProperties = new DeploymentProperties()
    deploymentProperties.setMode(deploymentMode)

    // set the link to template JSON.
    // Deserialize to pass it as an instance of a JSON Node object
    deploymentProperties.setTemplate(mapper.readTree(template))

    // initialize the parameters for this template
    if (templateParameters) {
      Map<String, ParameterValue> parameters = new HashMap<String, ParameterValue>()
      for (Map.Entry<String, String> entry : templateParameters.entrySet()) {
        parameters.put(entry.getKey(), new ParameterValue(entry.getValue()))
      }

      deploymentProperties.setParameters(mapper.readTree(mapper.writeValueAsString(parameters)))
    }

    // kick off the deployment
    Deployment deployment = new Deployment()
    deployment.setProperties(deploymentProperties)

    return resourceManagementClient
      .getDeploymentsOperations()
      .createOrUpdate(resourceGroupName, deploymentName, deployment)
      .body
  }

  /**
   *
   * @param subscriptionId
   * @param credentials
   * @return
   */
  private ResourceManagementClient initializeClient(ApplicationTokenCredentials credentials) {
    ResourceManagementClient resourceManagementClient = new ResourceManagementClientImpl(credentials)
    resourceManagementClient.setSubscriptionId(this.subscriptionId)
    resourceManagementClient.setLogLevel(HttpLoggingInterceptor.Level.NONE)
    resourceManagementClient
  }

  @Canonical
  private static class ParameterValue {
    String value
  }

}
