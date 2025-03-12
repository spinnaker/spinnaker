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
import com.azure.core.http.rest.Response
import com.azure.core.management.exception.ManagementException
import com.azure.core.management.profile.AzureProfile
import com.azure.resourcemanager.compute.models.VirtualMachineCustomImage
import com.azure.resourcemanager.compute.models.VirtualMachineImage
import com.azure.resourcemanager.compute.models.VirtualMachineOffer
import com.azure.resourcemanager.compute.models.VirtualMachinePublisher
import com.azure.resourcemanager.compute.models.VirtualMachineScaleSetVM
import com.azure.resourcemanager.compute.models.VirtualMachineSizes
import com.azure.resourcemanager.compute.models.VirtualMachineSku
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureInstance
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureServerGroupDescription
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureManagedVMImage
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureVMImage
import com.netflix.spinnaker.clouddriver.azure.security.AzureNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.model.HealthState
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.util.stream.Collectors

@Slf4j
@CompileStatic
public class AzureComputeClient extends AzureBaseClient {

  AzureComputeClient(String subscriptionId, TokenCredential credentials, AzureProfile azureProfile) {
    super(subscriptionId, azureProfile, credentials)
  }



  /**
   * Return list of available Managed VM images
   * @param resourceGroup - filter by resource group
   * @param region - filter by region
   * @return List of AzureManagedVMImage
   */
  List<AzureManagedVMImage> getAllVMCustomImages(String resourceGroup, String region) {

    def result = [] as List<AzureManagedVMImage>
    try {
      List<VirtualMachineCustomImage> virtualMachineCustomImages = executeOp({
        azure.virtualMachineCustomImages()
          .listByResourceGroup(resourceGroup)
          .asList()
          .stream()
          .filter({ vm -> vm.regionName().equals(region) })
          .collect(Collectors.toList())
      })

      virtualMachineCustomImages.each {vm ->
        result += new AzureManagedVMImage(
          name: vm.name(),
          resourceGroup: vm.resourceGroupName(),
          region: vm.regionName(),
          osType: vm.osDiskImage().osType().name())
      }

    } catch (Exception e) {
      log.error("getAllVMCustomImages -> Unexpected exception ", e)
    }

    result
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
          .asList()
      })


      log.info("getVMImagesAll-> Found ${publishers.size()} publisher items in azure/${location}")

      publishers?.each { publisher ->
        List<VirtualMachineOffer> offers = executeOp({
          publisher.offers().list().asList()
        })
        log.info("getVMImagesAll-> Found ${offers.size()} offer items for ${publisher} in azure/${location}")

        offers?.each { offer ->
          List<VirtualMachineSku> skus = executeOp({
            offer.skus().list().asList()
          })
          log.info("getVMImagesAll-> Found ${skus.size()} SKU items for ${publisher}/${offer} in azure/${location}")

          skus?.each { sku ->
            // Add a try/catch here in order to avoid an all-or-nothing return
            try {
              List<VirtualMachineImage> images = executeOp({
                sku.images().list().asList()
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
            def sg = AzureServerGroupDescription.build(scaleSet.innerModel())
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
      def sg = AzureServerGroupDescription.build(vmss.innerModel())
      sg.lastReadTime = System.currentTimeMillis()
      return sg
    } catch (ManagementException e) {
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
  Response<Void> destroyServerGroup(String resourceGroupName, String serverGroupName) {
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
      List<VirtualMachineScaleSetVM> vms = azure.virtualMachineScaleSets().getByResourceGroup(resourceGroupName, serverGroupName)?.virtualMachines()?.list()?.asList()
      vms?.each {
        instances.add(AzureInstance.build(it))
      }
    })

    instances
  }

  /**
   * check the scale set's health status, wait for the timeout return true when healthy, false if we hit the timeout
   */
  Boolean waitForScaleSetHealthy(String resourceGroupName, String serverGroupName, long timeoutMillis) {
    def now = System.nanoTime()
    def currentTime = System.nanoTime()

    // TODO: use available health probes to determine the sleep time
    def sleepTimeSeconds = 5

    while (currentTime - now < timeoutMillis * 1000000) {
      def instances = getServerGroupInstances(resourceGroupName, serverGroupName)
      if (!instances.any { it.healthState != HealthState.Up }) {
        return true
      }

      Thread.sleep(sleepTimeSeconds * 1000)
      currentTime = System.nanoTime()
    }

    false
  }

  Map<String, List<VirtualMachineSize>> getVirtualMachineSizesByRegions(List<AzureNamedAccountCredentials.AzureRegion> regions) {
    HashMap<String, List<VirtualMachineSize>> result = new HashMap<>()
    executeOp({
      VirtualMachineSizes sizes = azure.virtualMachines().sizes()
      for (AzureNamedAccountCredentials.AzureRegion region : regions) {
        List<VirtualMachineSize> regionSizes = sizes.listByRegion(region.name).toList().collect { new VirtualMachineSize(name: it.name())}
        result.put(region.name, regionSizes)
      }
    })
    result
  }

  Response<Void> resizeServerGroup(String resourceGroupName, String serverGroupName, int capacity) {
    try {
      def vmss = executeOp({
        azure.virtualMachineScaleSets().getByResourceGroup(resourceGroupName, serverGroupName)
      })
      vmss.update().withCapacity(capacity).apply()
    } catch (ManagementException e) {
      if (resourceNotFound(e)) {
        log.warn("ServerGroup: ${e.message} (${serverGroupName} was not found)")
      } else {
        throw e
      }
    }
    null
  }

  @Canonical
  static class VirtualMachineSize {
    String name
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
