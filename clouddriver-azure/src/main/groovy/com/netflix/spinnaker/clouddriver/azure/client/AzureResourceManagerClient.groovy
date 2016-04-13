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
import groovy.util.logging.Slf4j
import okhttp3.logging.HttpLoggingInterceptor

@Slf4j
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
   * Create a given set of resources in Azure based on template provided
   * @param credentials - AzureCredentials to use
   * @param template - ARM Template that defines the resources to be created
   * @param resourceGroupName - name of the resource group where the resources will be created
   * @param region - Azure region to create the resources in
   * @param resourceName - name of the main resource to be created
   * @param resourceType - type of the main resource to be created
   * @param templateParams - key/value list of parameters to pass to the template
   * @return
   */
  DeploymentExtended createResourceFromTemplate(AzureCredentials credentials,
                                                String template,
                                                String resourceGroupName,
                                                String region,
                                                String resourceName,
                                                String resourceType,
                                                Map<String, String> templateParams = [:]) {

    // TODO validate that all callers invoke this themselves, then remove this call
    initializeResourceGroupAndVNet(credentials, resourceGroupName, null, region)

    String deploymentName = [resourceName, resourceType, "deployment"].join(AzureUtilities.NAME_SEPARATOR)
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
   * Create a resource group in Azure
   * @param resourceGroupName - name of the resource group to create
   * @param region - region to create the resource group in
   * @return instance of the Azure SDK ResourceGroup class
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
   * Initialize the resource group and virtual network
   * @param creds - AzureCredentials
   * @param resourceGroupName - name of the resource group
   * @param virtualNetworkName - name of the virtual network to be created/initialized
   * @param region - Azure region
   * @return - instance of the Azure SDK ResourceGroup class
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
   * Check to see if a resource group already exists in the subscription
   * @param resourceGroupName name of the resource group to look for
   * @return True if it already exists
   */
  boolean resourceGroupExists(String resourceGroupName) {
    client.getResourceGroupsOperations().checkExistence(resourceGroupName).body
  }

  /**
   * Retrieve the operations associated with a given deployment in Azure
   * @param resourceGroupName - name of the resource group where the deployment exists
   * @param deploymentName - name of the deployment
   * @param operationCount - number of operations to return. Default is 10
   * @return List of Azure SDK DeploymentOperations objects
   */
  List<DeploymentOperation> getDeploymentOperations(String resourceGroupName,
                                                    String deploymentName,
                                                    Integer operationCount = 10) {
    client.getDeploymentOperationsOperations().list(resourceGroupName, deploymentName, operationCount).body
  }

  /**
   * Retrieve the deployment resource from Azure
   * @param resourceGroupName - name of the resource group where the deployment exists
   * @param deploymentName - name of the deployment
   * @return Azure SDK DeploymentExtended object
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
   * Create the virtual network resource in Azure if it does not exist
   * @param creds - AzureCredentials instance
   * @param resourceGroupName - name of the resource group to lookup/create the virtual network resource in
   * @param virtualNetworkName - name of the virtual network to lookup/create
   * @param region - Azure region to lookup/create virtual network resource in
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
   * Deploy the resource template to Azure
   * @param resourceManagementClient - the Azure SDK ResourceManagementClient instance
   * @param resourceGroupName - name of the resource group where the template will be deployed
   * @param deploymentMode - Deployment Mode
   * @param deploymentName - name of the deployment
   * @param template - the ARM template to be deployed
   * @param templateParameters - key/value list of parameters that will be passed to the template
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

    // initialize the parameters for resourceManagementClientthis template
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
   * initialize the Azure client that will be used for interactions(s) with this provider in Azure
   * @param credentials - Credentials that will be used for authentication with Azure
   * @return - an initialized instance of the Azure ResourceManagementClient object
   */
  private ResourceManagementClient initializeClient(ApplicationTokenCredentials credentials) {
    ResourceManagementClient resourceManagementClient = new ResourceManagementClientImpl(credentials)
    resourceManagementClient.setSubscriptionId(this.subscriptionId)
    resourceManagementClient.setLogLevel(HttpLoggingInterceptor.Level.NONE)
    resourceManagementClient
  }

  /**
   * Register the Resource Provider in Azure
   * @param namespace - the namespace for the Resource Provider to register
   */
  void registerProvider(String namespace) {
    def ops = client.getProvidersOperations()
    try {
      if (ops.get(namespace).body.registrationState != "Registered") {
        log.info("Registering Azure provider: ${namespace}")
        ops.register(namespace)
        log.info("Azure provider ${namespace} registered")
      }
    } catch (Exception e) {
      // Something went wrong. log the exception
      log.error("Unable to register Azure Provider: ${namespace}", e)
    }
  }

  /**
   * The namespace for the Azure Resource Provider
   * @return namespace of the resource provider
   */
  @Override
  String getProviderNamespace() {
    "Microsoft.Resources"
  }

  @Canonical
  private static class ParameterValue {
    String value
  }

}
