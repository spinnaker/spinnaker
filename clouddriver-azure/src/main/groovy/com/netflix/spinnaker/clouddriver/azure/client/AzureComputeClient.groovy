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
import com.microsoft.azure.management.compute.VirtualMachineImage
import com.microsoft.azure.management.compute.VirtualMachineOffer
import com.microsoft.azure.management.compute.VirtualMachinePublisher
import com.microsoft.azure.management.compute.VirtualMachineScaleSetVM
import com.microsoft.azure.management.compute.VirtualMachineSku
import com.microsoft.rest.ServiceResponse
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureInstance
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureServerGroupDescription
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureVMImage
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j


@Slf4j
@CompileStatic
public class AzureComputeClient extends AzureBaseClient {

  AzureComputeClient(String subscriptionId, ApplicationTokenCredentials credentials, String userAgentApplicationName) {
    super(subscriptionId, userAgentApplicationName, credentials)
  }

  /**
   * Return list of available VM images
   * @param location - filter for images to given location
   * @return List of AzureVMImages
   */
  List<AzureVMImage> getVMImagesAll(String location) {
    def result = [] as List<AzureVMImage>

    try {
      List<VirtualMachinePublisher> publishers = executeOp({
        azure.virtualMachineImages()
          .publishers()
          .listByRegion(location)
      })

      log.info("getVMImagesAll-> Found ${publishers.size()} publisher items in azure/${location}")

      publishers?.each { publisher ->
        List<VirtualMachineOffer> offers = executeOp({
          publisher.offers().list()
        })
        log.info("getVMImagesAll-> Found ${offers.size()} offer items for ${publisher} in azure/${location}")

        offers?.each { offer ->
          List<VirtualMachineSku> skus = executeOp({
            offer.skus().list()
          })
          log.info("getVMImagesAll-> Found ${skus.size()} SKU items for ${publisher}/${offer} in azure/${location}")

          skus?.each { sku ->
            // Add a try/catch here in order to avoid an all-or-nothing return
            try {
              List<VirtualMachineImage> images = executeOp({
                sku.images().list()
              })
              log.info("getVMImagesAll-> Found ${skus.size()} version items for ${publisher}/${offer}/${sku} in azure/${location}")

              images?.each { image ->
                result += new AzureVMImage(
                  publisher: publisher.name(),
                  offer: offer.name(),
                  sku: sku.name(),
                  version: image.version())
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

  VirtualMachineImage getVMImage(String location, String publisher, String offer, String skus, String version) {
    executeOp({
      azure.virtualMachineImages()
        .getImage(location, publisher, offer, skus, version)
    })
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
      def vmssList = executeOp({
        resourceGroup ? azure.virtualMachineScaleSets().listByResourceGroup(resourceGroup) :
          azure.virtualMachineScaleSets().list()
      })

      vmssList?.each { scaleSet ->
        if (scaleSet.regionName() == region) {
          try {
            def sg = AzureServerGroupDescription.build(scaleSet.inner())
            sg.lastReadTime = lastReadTime
            serverGroups.add(sg)
          } catch (Exception e) {
            log.warn("Unable to parse scale set ${scaleSet.name()} from Azure: ${e.message}")
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
      def vmss = executeOp({
        azure.virtualMachineScaleSets().getByResourceGroup(resourceGroupName, serverGroupName)
      })
      def sg = AzureServerGroupDescription.build(vmss.inner())
      sg.lastReadTime = System.currentTimeMillis()
      return sg
    } catch (CloudException e) {
      if (resourceNotFound(e)) {
        log.warn("ServerGroup: ${e.message} (${serverGroupName} was not found)")
      } else {
        throw e
      }
    }
    null
  }

  /**
   * It deletes a given server group
   * @param resourceGroupName - name of the resource group
   * @param serverGroupName - name of the server group
   * @return a ServiceResponse object
   */
  ServiceResponse<Void> destroyServerGroup(String resourceGroupName, String serverGroupName) {
    deleteAzureResource(
      azure.virtualMachineScaleSets().&deleteByResourceGroup,
      resourceGroupName,
      serverGroupName,
      null,
      "Delete Server Group ${serverGroupName}",
      "Failed to delete Server Group ${serverGroupName} in ${resourceGroupName}"
    )
  }

  /**
   * Get the instances associated with a given server group
   * @param resourceGroupName - name of the resource group
   * @param serverGroupName - name of the server group
   * @return Collection of AzureInstance objects
   */
  Collection<AzureInstance> getServerGroupInstances(String resourceGroupName, String serverGroupName) {
    def instances = new ArrayList<AzureInstance>()

    executeOp({
      List<VirtualMachineScaleSetVM> vms = azure.virtualMachineScaleSets().getByResourceGroup(resourceGroupName, serverGroupName)?.virtualMachines()?.list()
      vms?.each {
        instances.add(AzureInstance.build(it))
      }
    })

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
