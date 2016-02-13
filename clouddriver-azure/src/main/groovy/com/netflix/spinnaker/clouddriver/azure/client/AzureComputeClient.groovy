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

import com.microsoft.azure.management.compute.ComputeManagementClient
import com.microsoft.azure.management.compute.ComputeManagementService
import com.microsoft.azure.management.compute.models.VirtualMachineImageListOffersParameters
import com.microsoft.azure.management.compute.models.VirtualMachineImageListParameters
import com.microsoft.azure.management.compute.models.VirtualMachineImageListPublishersParameters
import com.microsoft.azure.management.compute.models.VirtualMachineImageListSkusParameters
import com.microsoft.azure.management.compute.models.VirtualMachineImageResource
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureVMImage
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
public class AzureComputeClient extends AzureBaseClient {
  AzureComputeClient(String subscriptionId) {
    super(subscriptionId)
  }

  /**
   * get the ComputeManagementClient which will be used for all interaction related to compute resources in Azure
   * @param creds the credentials to use when communicating to the Azure subscription(s)
   * @return an instance of the Azure ComputeManagementClient
   */
  protected ComputeManagementClient getComputeClient(AzureCredentials creds) {
    ComputeManagementService.create(this.buildConfiguration(creds))
  }

  List<AzureVMImage> getVMImagesAll(AzureCredentials creds, String location){
    def result = [] as List<AzureVMImage>

    try {
      ComputeManagementClient computeManagementClient = getComputeClient(creds)
      VirtualMachineImageListPublishersParameters paramsPublishers = new VirtualMachineImageListPublishersParameters()
      paramsPublishers.location = location
      def vmImagesOps = computeManagementClient.getVirtualMachineImagesOperations()
      def listPublishers = vmImagesOps.listPublishers(paramsPublishers)?.resources
      listPublishers.each { VirtualMachineImageResource itemVMPublisher ->
        def paramsOffers = new VirtualMachineImageListOffersParameters(itemVMPublisher.name, location)

        def listOffers = vmImagesOps.listOffers(paramsOffers)?.resources
        listOffers.each {itemVMOffers ->
          def paramsSkus = new VirtualMachineImageListSkusParameters(itemVMOffers.name, itemVMPublisher.name, location)

          def listSkus = vmImagesOps.listSkus(paramsSkus)?.resources
          listSkus.each { itemVMSku ->
            def params = new VirtualMachineImageListParameters()
            params.location = location
            params.publisherName = itemVMPublisher.name
            params.offer = itemVMOffers.name
            params.skus = itemVMSku.name

            def listVersions = vmImagesOps.list(params)?.resources
            listVersions.each { itemVMImage ->
              result += new AzureVMImage(
                publisher: itemVMPublisher.name,
                offer: itemVMOffers.name,
                sku: itemVMSku.name,
                version: itemVMImage.name)
            }
          }
        }
      }
    }
    catch (Exception e) {
      log.info("getVMImagesAll -> Unexpected exception " + e.toString())
    }

    result
  }

}
