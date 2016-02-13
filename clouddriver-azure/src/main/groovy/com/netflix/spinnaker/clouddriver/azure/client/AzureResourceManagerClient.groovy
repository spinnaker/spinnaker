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

import com.microsoft.azure.management.resources.models.Deployment
import com.microsoft.azure.management.resources.models.DeploymentExtended
import com.microsoft.azure.management.resources.models.DeploymentMode
import com.microsoft.azure.management.resources.models.DeploymentOperation
import com.microsoft.azure.management.resources.models.DeploymentOperationsListParameters
import com.microsoft.azure.management.resources.models.DeploymentOperationsListResult
import com.microsoft.azure.management.resources.models.DeploymentProperties
import com.microsoft.azure.management.resources.models.ResourceGroup
import com.microsoft.azure.management.resources.ResourceManagementClient
import com.microsoft.azure.management.resources.ResourceManagementService
import com.microsoft.windowsazure.exception.ServiceException
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import groovy.transform.Canonical
import groovy.transform.CompileStatic

@CompileStatic
class AzureResourceManagerClient extends AzureBaseClient {

  AzureResourceManagerClient(String subscriptionId) {
    super(subscriptionId)
  }

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

    DeploymentExtended deployment = createTemplateDeployment(this.getResourceManagementClient(credentials),
      resourceGroupName,
      DeploymentMode.Incremental,
      deploymentName,
      template,
      templateParams)

    deployment
  }

  ResourceGroup createResourceGroup(AzureCredentials creds, String resourceGroupName, String region) {
    try {
      ResourceGroup resourceGroup = this.getResourceManagementClient(creds).getResourceGroupsOperations().createOrUpdate(resourceGroupName,new ResourceGroup(region)).resourceGroup

      resourceGroup
    } catch (e) {
      throw new RuntimeException("Unable to create Resource Group ${resourceGroupName} in region ${region}", e)
    }
  }

  ResourceGroup initializeResourceGroupAndVNet(AzureCredentials creds, String resourceGroupName, String virtualNetworkName, String region) {
    ResourceGroup resourceGroup
    if (!resourceGroupExists(creds, resourceGroupName)) {
      resourceGroup = createResourceGroup(creds, resourceGroupName, region)
    } else {
      resourceGroup = this.getResourceManagementClient(creds).getResourceGroupsOperations().get(resourceGroupName).getResourceGroup()
    }

    initializeResourceGroupVNet(creds, resourceGroupName, virtualNetworkName, region)

    resourceGroup
  }

  boolean resourceGroupExists(AzureCredentials creds, String resourceGroupName) {
    this.getResourceManagementClient(creds).getResourceGroupsOperations().checkExistence(resourceGroupName).isExists()
  }

  ArrayList<DeploymentOperation> getDeploymentOperations(AzureCredentials creds,
                                                         String resourceGroupName,
                                                         String deploymentName,
                                                         Integer operationCount = 10) {
    DeploymentOperationsListParameters parameters = new DeploymentOperationsListParameters(top: operationCount)
    DeploymentOperationsListResult result = this.getResourceManagementClient(creds).getDeploymentOperationsOperations().list(resourceGroupName, deploymentName, parameters)

    result.operations
  }

  DeploymentExtended getDeployment(AzureCredentials creds, String resourceGroupName, String deploymentName) {
    this.getResourceManagementClient(creds).getDeploymentsOperations().get(resourceGroupName, deploymentName).deployment
  }

  String getResourceGroupLocation(String resourceGroupName, AzureCredentials creds) {
    this.getResourceManagementClient(creds).getResourceGroupsOperations().get(resourceGroupName).getResourceGroup().getLocation()
  }

  void healthCheck(AzureCredentials creds) {
    try {
      this.getResourceManagementClient(creds).getResourcesOperations().list(null)
    }
    catch (Exception e) {
      throw new Exception("Unable to ping Azure", e)
    }
  }

  protected ResourceManagementClient getResourceManagementClient(AzureCredentials creds) {
    ResourceManagementService.create(this.buildConfiguration(creds))
  }

  private static void initializeResourceGroupVNet(AzureCredentials creds, String resourceGroupName, String virtualNetworkName = null, String region) {
    def vNetName = virtualNetworkName ?
      virtualNetworkName : AzureUtilities.getVirtualNetworkName(resourceGroupName)

    try {
      creds.getNetworkClient().getVirtualNetwork(creds, resourceGroupName, vNetName)
      }
    catch (ServiceException ignore) {
      // Assumes that a service exception means that the rest call failed to locate the vNet
      creds.getNetworkClient().createVirtualNetwork(creds, resourceGroupName, vNetName, region)
    }
  }

  private static DeploymentExtended createTemplateDeployment(
    ResourceManagementClient resourceManagementClient,
    String resourceGroupName,
    DeploymentMode deploymentMode,
    String deploymentName,
    String template,
    Map<String, String> templateParameters) throws URISyntaxException, IOException, ServiceException {

    DeploymentProperties deploymentProperties = new DeploymentProperties()
    deploymentProperties.setMode(deploymentMode)

    // set the link to template JSON
    deploymentProperties.setTemplate(template)

    // initialize the parameters for this template
    if (templateParameters) {
      Map<String, ParameterValue> parameters = new HashMap<String, ParameterValue>()
      for (Map.Entry<String, String> entry : templateParameters.entrySet()) {
        parameters.put(entry.getKey(), new ParameterValue(entry.getValue()))
      }

      deploymentProperties.setParameters(mapper.writeValueAsString(parameters))
    }

    // kick off the deployment
    Deployment deployment = new Deployment()
    deployment.setProperties(deploymentProperties)

    return resourceManagementClient
      .getDeploymentsOperations()
      .createOrUpdate(resourceGroupName, deploymentName, deployment)
      .getDeployment()
  }

  @Canonical
  private static class ParameterValue {
    String value
  }

  static class DeploymentState {
    public static final String SUCCEEDED = "Succeeded"
    public static final String FAILED = "Failed"
    public static final String CANCELED = "Canceled"
    public static final String READY = "Ready"
    public static final String DELETED = "Deleted"
    public static final String ACCEPTED = "Accepted"
    public static final String DEPLOYING = "Deploying"
  }

}
