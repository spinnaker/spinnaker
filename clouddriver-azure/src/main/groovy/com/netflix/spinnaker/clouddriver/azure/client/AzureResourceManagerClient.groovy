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
import com.microsoft.azure.management.network.Network
import com.microsoft.azure.management.resources.Deployment
import com.microsoft.azure.management.resources.DeploymentMode
import com.microsoft.azure.management.resources.DeploymentOperation
import com.microsoft.azure.management.resources.Provider
import com.microsoft.azure.management.resources.ResourceGroup
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@CompileStatic
@Slf4j
class AzureResourceManagerClient extends AzureBaseClient {

  /**
   * Client for communication with Azure Resource Management
   * @param subscriptionId - Azure Subscription ID
   * @param credentials - Token Credentials to use for communication with Auzre
   */
  AzureResourceManagerClient(String subscriptionId, ApplicationTokenCredentials credentials, String userAgentApplicationName = "") {
    super(subscriptionId, userAgentApplicationName, credentials)
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
  Deployment createResourceFromTemplate(String template,
                                        String resourceGroupName,
                                        String region,
                                        String resourceType,
                                        Map<String, Object> templateParams = [:]) {

    String deploymentName = [resourceType, "deployment"].join(AzureUtilities.NAME_SEPARATOR)
    if (!templateParams['location']) {
      templateParams['location'] = region
    }

    createTemplateDeployment(resourceGroupName,
      DeploymentMode.INCREMENTAL,
      deploymentName,
      template,
      templateParams)
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
      azure.resourceGroups()
        .define(resourceGroupName)
        .withRegion(region)
        .create()

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
  ResourceGroup initializeResourceGroupAndVNet(String resourceGroupName, String virtualNetworkName, String region) {
    ResourceGroup resourceGroup
    if (!resourceGroupExists(resourceGroupName)) {
      resourceGroup = createResourceGroup(resourceGroupName, region)
    } else {
      resourceGroup = getResourceGroup(resourceGroupName)
    }

    if (virtualNetworkName) {
      initializeResourceGroupVNet(resourceGroupName, virtualNetworkName, region)
    }

    resourceGroup
  }

  /**
   * Check to see if a resource group already exists in the subscription
   * @param resourceGroupName name of the resource group to look for
   * @return True if it already exists
   */
  boolean resourceGroupExists(String resourceGroupName) {
    azure.resourceGroups().contain(resourceGroupName)
  }

  private ResourceGroup getResourceGroup(String resourceGroupName) {
    azure.resourceGroups().getByName(resourceGroupName)
  }

  /**
   * Retrieve the operations associated with a given deployment in Azure
   * @param resourceGroupName - name of the resource group where the deployment exists
   * @param deploymentName - name of the deployment
   * @param operationCount - number of operations to return. Default is 10
   * @return List of Azure SDK DeploymentOperations objects
   */
  List<DeploymentOperation> getDeploymentOperations(String resourceGroupName,
                                                    String deploymentName) {
    executeOp({
      def list = azure.deployments()
        .getByResourceGroup(resourceGroupName, deploymentName)
        .deploymentOperations()
        .list()
      list.loadAll()
      list
    })
  }

  /**
   * Retrieve the deployment resource from Azure
   * @param resourceGroupName - name of the resource group where the deployment exists
   * @param deploymentName - name of the deployment
   * @return Azure SDK DeploymentExtended object
   */
  Deployment getDeployment(String resourceGroupName, String deploymentName) {
    executeOp({
      azure.deployments().getByResourceGroup(resourceGroupName, deploymentName)
    })
  }

  /**
   * Azure Health Check
   */
  void healthCheck() {
    try {
      azure.genericResources().list()
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
  private void initializeResourceGroupVNet(String resourceGroupName, String virtualNetworkName, String region) {
    Network vNet = null

    try {
      vNet = azure.networks().getByResourceGroup(resourceGroupName, virtualNetworkName)
    } catch (CloudException ignore) {
      // Assumes that a cloud exception means that the rest call failed to locate the vNet
      log.warn("Failed to locate Azure Virtual Network ${virtualNetworkName}")
    }
    if (!vNet) {
      azure.networks()
        .define(virtualNetworkName)
        .withRegion(region)
        .withExistingResourceGroup(resourceGroupName)
        .create()
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
  private Deployment createTemplateDeployment(
    String resourceGroupName,
    DeploymentMode deploymentMode,
    String deploymentName,
    String template,
    Map<String, Object> templateParameters) {
    try {
      String parameters = AzureUtilities.convertParametersToTemplateJSON(mapper, templateParameters)
      return azure.deployments().define(deploymentName)
        .withExistingResourceGroup(resourceGroupName)
        .withTemplate(template)
        .withParameters(parameters)
        .withMode(deploymentMode)
        .create()
    } catch (Throwable e) {
      log.error("Exception occured during deployment ${e.message}")
      throw e
    } finally {
      logDeploymentTemplate(deploymentName, template, templateParameters)
    }
  }

  static void logDeploymentTemplate(String deploymentName, String template, Map<String, Object> parameters) {
    log.info("Template for deployment {}: {}\nTemplate Parameters: {}", deploymentName, template, parameters.toMapString())
  }

  /**
   * Register the Resource Provider in Azure
   * @param namespace - the namespace for the Resource Provider to register
   */
  void registerProvider(String namespace) {
    try {
      Provider provider = azure.providers().getByName(namespace)
      if (provider.registrationState() != "Registered") {
        log.info("Registering Azure provider: ${namespace}")
        azure.providers().register(namespace)
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
}
