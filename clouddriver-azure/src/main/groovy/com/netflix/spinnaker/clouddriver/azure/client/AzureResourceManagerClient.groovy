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

import com.microsoft.azure.management.resources.ResourceManagementClient
import com.microsoft.azure.management.resources.ResourceManagementService
import com.microsoft.azure.management.resources.models.DeploymentExtended
import com.microsoft.azure.management.resources.models.DeploymentMode
import com.microsoft.azure.management.resources.models.ResourceGroup
import com.microsoft.azure.utility.ResourceHelper
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import groovy.transform.CompileStatic

@CompileStatic
class AzureResourceManagerClient extends AzureBaseClient {
  // URI to the internal load balancer template. we should create our own public and private LB templates and store them in a local cache.
  private String loadBalancerTemplateUri = "https://raw.githubusercontent.com/Azure/azure-quickstart-templates/master/101-create-internal-loadbalancer/azuredeploy.json"

  public AzureResourceManagerClient(String subscriptionId) {
    super(subscriptionId)
  }

  public String createLoadBalancer(AzureCredentials creds,
                                   String resourceGroupName,
                                   String loadBalancerName,
                                   String region) {
    String templateVersion = "1.0.0.0"

    try {
      if (!resourceGroupExists(creds, resourceGroupName)) {
        createResouceGroup(creds, resourceGroupName, region)
      }

      Map<String, String> templateParams = new HashMap<String, String>()
      templateParams.put("location", region)
      templateParams.put("addressPrefix", "10.0.0.0/16")
      templateParams.put("subnetPrefix", "10.0.0.0/24")

      DeploymentExtended deployment = ResourceHelper.createTemplateDeploymentFromURI(
        this.getResourceManagementClient(creds),
        resourceGroupName,
        DeploymentMode.Incremental,
        loadBalancerName,
        loadBalancerTemplateUri,
        templateVersion,
        templateParams
      )

      return deployment.name
    } catch (e) {
      throw new RuntimeException("Unable to create load balancer ${loadBalancerName}", e)
    }
  }

  public ResourceGroup createResouceGroup(AzureCredentials creds, String resourceGroupName, String region) {
    ResourceGroup rg = new ResourceGroup(region)
    try {
      return this.getResourceManagementClient(creds).getResourceGroupsOperations().createOrUpdate(resourceGroupName, rg).resourceGroup
    } catch (e) {
      throw new RuntimeException("Unable to create Resource Group ${resourceGroupName} in region ${region}", e)
    }
  }

  public boolean resourceGroupExists(AzureCredentials creds, String resourceGroupName) {
    return this.getResourceManagementClient(creds).getResourceGroupsOperations().checkExistence(resourceGroupName).isExists()
  }

  public void healthCheck(AzureCredentials creds) throws Exception {
    try {
      this.getResourceManagementClient(creds).getResourcesOperations().list(null)
    }
    catch (Exception e) {
      throw new Exception("Unable to ping Azure", e)
    }
  }

  protected ResourceManagementClient getResourceManagementClient(AzureCredentials creds) {
    return ResourceManagementService.create(this.buildConfiguration(creds))
  }
}
