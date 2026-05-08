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

import com.azure.core.credential.TokenCredential
import com.azure.core.management.exception.ManagementException
import com.azure.core.management.profile.AzureProfile
import com.azure.resourcemanager.network.models.Network
import com.azure.resourcemanager.resources.models.Deployment
import com.azure.resourcemanager.resources.models.DeploymentMode
import com.azure.resourcemanager.resources.models.DeploymentOperation
import com.azure.resourcemanager.resources.models.Provider
import com.azure.resourcemanager.resources.models.ResourceGroup
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.common.model.AzureDeploymentOperation
import com.netflix.spinnaker.clouddriver.azure.resources.common.model.AzureDeploymentOperation.FailedResourceDetail
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
  AzureResourceManagerClient(String subscriptionId, TokenCredential credentials, AzureProfile azureProfile) {
    super(subscriptionId, azureProfile, credentials)
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
                                        String resourceName,
                                        String resourceType,
                                        Map<String, Object> templateParams = [:]) {

    String deploymentName = [resourceName, resourceType, "deployment"].join(AzureUtilities.NAME_SEPARATOR)
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
      list.asList()
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
    } catch (ManagementException ignore) {
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
      return executeOp({
        azure.deployments().define(deploymentName)
          .withExistingResourceGroup(resourceGroupName)
          .withTemplate(template)
          .withParameters(parameters)
          .withMode(deploymentMode)
          .create()
      })
    } catch (Exception e) {
      String original = e.message ?: "${e.class.simpleName} (no message)".toString()
      String enriched = enrichWithDeploymentOperationErrors(
        original,
        lookupDeploymentOperationsForErrorEnrichment(resourceGroupName, deploymentName))
      log.error("Exception occured during deployment ${deploymentName}: ${enriched}", e)
      throw wrapAsRichException(enriched, e)
    } finally {
      logDeploymentTemplate(deploymentName, template, templateParameters)
    }
  }

  /**
   * Wrap an exception with an enriched message while preserving the original exception type
   * when it is a {@link ManagementException}, so downstream {@code catch (ManagementException)}
   * blocks continue to match.
   */
  static RuntimeException wrapAsRichException(String enrichedMessage, Exception original) {
    if (original instanceof ManagementException) {
      ManagementException me = (ManagementException) original
      ManagementException enriched = new ManagementException(enrichedMessage, me.response, me.value)
      enriched.initCause(original)
      return enriched
    }
    new RuntimeException(enrichedMessage, original)
  }

  /**
   * Best-effort lookup of deployment operations so that the original LRO failure can be enriched
   * with the underlying ARM error details. Performs a small bounded retry to ride out the
   * race window between LRO terminal-failure and ARM materializing the per-op failure rows.
   * Returns an empty list (rather than propagating) if every attempt fails or 404s, since at
   * this point we already have an exception to surface.
   */
  private static final int LOOKUP_RETRY_ATTEMPTS = 3
  private static final long LOOKUP_RETRY_BACKOFF_MS = 1000

  private Collection<DeploymentOperation> lookupDeploymentOperationsForErrorEnrichment(
    String resourceGroupName, String deploymentName) {
    Exception lastEx = null
    for (int attempt = 0; attempt < LOOKUP_RETRY_ATTEMPTS; attempt++) {
      if (attempt > 0) {
        try {
          Thread.sleep(LOOKUP_RETRY_BACKOFF_MS)
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt()
          break
        }
      }
      try {
        Collection<DeploymentOperation> ops = getDeploymentOperations(resourceGroupName, deploymentName)
        if (ops) {
          return ops
        }
      } catch (Exception lookupEx) {
        lastEx = lookupEx
      }
    }
    if (lastEx != null) {
      log.warn("Failed to fetch deployment operations for ${deploymentName} while enriching error", lastEx)
    }
    Collections.<DeploymentOperation> emptyList()
  }

  /**
   * Build a richer error message by appending each FAILED deployment operation's statusMessage to
   * the original exception message. Azure's LRO poller throws a generic
   * "Long running operation failed." with no details — the actual ARM error lives in each
   * operation's statusMessage. Stale failures from prior retries (deployment names are
   * deterministic) are filtered out via timestamp windowing.
   */
  static String enrichWithDeploymentOperationErrors(
    String originalMessage,
    Collection<DeploymentOperation> operations) {
    List<FailedResourceDetail> failures = AzureDeploymentOperation.extractFailedResources(operations)
    failures = AzureDeploymentOperation.filterToRecentFailures(failures)
    if (!failures) {
      return originalMessage
    }
    String joined = failures.collect { "[${it.label()}] ${it.statusMessageRendered}".toString() }.join(' | ')
    "${originalMessage} :: ${joined}".toString()
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
      throw e
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
