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
import com.microsoft.azure.management.compute.ComputeManagementClient
import com.microsoft.azure.management.compute.ComputeManagementClientImpl
import com.microsoft.azure.management.compute.VirtualMachineScaleSetsOperations
import com.microsoft.azure.management.compute.models.VirtualMachineScaleSet
import com.microsoft.rest.ServiceResponse
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureInstance
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureServerGroupDescription
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureVMImage
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import okhttp3.logging.HttpLoggingInterceptor


@Slf4j
@CompileStatic
public class AzureComputeClient extends AzureBaseClient {
  private final ComputeManagementClient client

  AzureComputeClient(String subscriptionId, ComputeManagementClient client) {
    super(subscriptionId)
    this.client = client
  }

  AzureComputeClient(String subscriptionId, ApplicationTokenCredentials credentials) {
    super(subscriptionId)
    this.client = this.initialize(credentials)
  }

  /**
   * get the ComputeManagementClient which will be used for all interaction related to compute resources in Azure
   * @param creds the credentials to use when communicating to the Azure subscription(s)
   * @return an instance of the Azure ComputeManagementClient
   */
  private ComputeManagementClient initialize(ApplicationTokenCredentials tokenCredentials) {
    ComputeManagementClient computeClient = new ComputeManagementClientImpl(tokenCredentials)
    computeClient.setSubscriptionId(this.subscriptionId)
    computeClient.setLogLevel(HttpLoggingInterceptor.Level.NONE)
    computeClient
  }

  /**
   * Return list of available VM images
   * @param location - filter for images to given location
   * @return List of AzureVMImages
   */
  List<AzureVMImage> getVMImagesAll(String location){
    def result = [] as List<AzureVMImage>

    try {
      // Usage of local variables to ease with debugging the code; keeping the content retrieved from Azure JSDK call to help with stepping through the code and inspect the values
      List<String> publishers = client.getVirtualMachineImagesOperations()?.listPublishers(location)?.body?.collect { it.name }
      log.info("getVMImagesAll-> Found ${publishers.size()} publisher items in azure/${location}/${ComputeManagementClient.simpleName}")

      publishers?.each { publisher ->
        List<String> offers = client.getVirtualMachineImagesOperations()?.listOffers(location, publisher)?.body?.collect {
          it.name
        }
        log.info("getVMImagesAll-> Found ${offers.size()} offer items for ${publisher} in azure/${location}/${ComputeManagementClient.simpleName}")

        offers?.each { offer ->
          List<String> skus = client.getVirtualMachineImagesOperations()?.listSkus(location, publisher, offer)?.body?.collect {
            it.name
          }
          log.info("getVMImagesAll-> Found ${skus.size()} SKU items for ${publisher}/${offer} in azure/${location}/${ComputeManagementClient.simpleName}")

          skus?.each { sku ->
            // Add a try/catch here in order to avoid an all-or-nothing return
            try {
              List<String> versions = client.getVirtualMachineImagesOperations()?.list(location, publisher, offer, sku, null, 100, "name")?.body?.collect {
                it.name
              }
              log.info("getVMImagesAll-> Found ${skus.size()} version items for ${publisher}/${offer}/${sku} in azure/${location}/${ComputeManagementClient.simpleName}")

              versions?.each { version ->
                result += new AzureVMImage(
                  publisher: publisher,
                  offer: offer,
                  sku: sku,
                  version: version)
              }
            }
            catch (Exception e) {
              log.info("getVMImagesAll -> Unexpected exception " + e.toString())
            }
          }
        }
      }
    }
    catch (Exception e) {
      log.error("getVMImagesAll -> Unexpected exception ", e)
    }

    result
  }

  /**
   *
   * @param Region
   * @return
   */
  Collection<AzureServerGroupDescription> getServerGroupsAll(String region, String resourceGroup = null) {
    def serverGroups = new ArrayList<AzureServerGroupDescription>()
    def lastReadTime = System.currentTimeMillis()

    try {
      List<VirtualMachineScaleSet> vmssList = resourceGroup ? this.client.virtualMachineScaleSetsOperations?.list(resourceGroup)?.body
                                                            : this.client.virtualMachineScaleSetsOperations?.listAll()?.body
      vmssList?.each { scaleSet ->
        if (scaleSet.location == region) {
          try {
            def sg = AzureServerGroupDescription.build(scaleSet)
            sg.lastReadTime = lastReadTime
            serverGroups.add(sg)
          } catch (Exception e) {
            log.warn("Unable to parse scale set ${scaleSet.name} from Azure: ${e.message}")
          }
        }
      }
    } catch (Exception e) {
      log.error("getServerGroupsAll -> Unexpected exception: ${e.message}")
    }

    serverGroups
  }

  AzureServerGroupDescription getServerGroup(String resourceGroupName, String serverGroupName) {
    try {
      def vmss = this.client.getVirtualMachineScaleSetsOperations()?.get(resourceGroupName, serverGroupName)?.body
      def sg = AzureServerGroupDescription.build(vmss)
      sg.lastReadTime = System.currentTimeMillis()
      return sg
    } catch (CloudException e) {
      if (resourceNotFound(e.response.code())) {
        log.warn("ServerGroup: ${e.message} (${serverGroupName} was not found)")
      }
      else {
        throw e
      }
    }
    null
  }

  ServiceResponse<Void> destroyServerGroup(String resourceGroupName, String serverGroupName) {
    VirtualMachineScaleSetsOperations ops = getAzureOps(
      client.&getVirtualMachineScaleSetsOperations, "Get operations object", "Failed to get operation object") as VirtualMachineScaleSetsOperations

    deleteAzureResource(
      ops.&delete,
      resourceGroupName,
      serverGroupName,
      null,
      "Delete Server Group ${serverGroupName}",
      "Failed to delete Server Group ${serverGroupName} in ${resourceGroupName}"
    )
  }

  ServiceResponse<Void> disableServerGroup(String resourceGroupName, String serverGroupName) {
    VirtualMachineScaleSetsOperations ops = getAzureOps(
      client.&getVirtualMachineScaleSetsOperations, "Get operations object", "Failed to get operation object") as VirtualMachineScaleSetsOperations

    List<String> instanceIds = this.getServerGroupInstances(resourceGroupName,serverGroupName)?.collect {it.resourceId}

    ops.powerOff(resourceGroupName, serverGroupName, instanceIds)

    // TODO: investigate if we can deallocate the VMs
    //ops.deallocate(resourceGroupName, serverGroupName, instanceIds)
  }

  ServiceResponse<Void> enableServerGroup(String resourceGroupName, String serverGroupName) {
    VirtualMachineScaleSetsOperations ops = getAzureOps(
      client.&getVirtualMachineScaleSetsOperations, "Get operations object", "Failed to get operation object") as VirtualMachineScaleSetsOperations

    List<String> instanceIds = this.getServerGroupInstances(resourceGroupName,serverGroupName)?.collect {it.resourceId}

    ops.start(resourceGroupName, serverGroupName, instanceIds)
  }

  Collection<AzureInstance> getServerGroupInstances(String resourceGroupName, String serverGroupName) {
    def vmOps = this.client.virtualMachineScaleSetVMsOperations
    def instances = new ArrayList<AzureInstance>()
    try {
      vmOps.list(resourceGroupName, serverGroupName, null, null, "instanceView")?.body?.each {
        instances.add(AzureInstance.build(it))
      }
    } catch (Exception e) {
      log.error("getServerGroupInstances -> Unexpected exception", e)
    }
    instances
  }

  /***
   * The namespace for the Azure Resource Provider
   * @return namespace of the resource provider
   */
  @Override
  String getProviderNamespace() {
    "Microsoft.Compute"
  }
}
