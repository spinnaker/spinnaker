/*
 * Copyright 2016 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.common.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.microsoft.azure.credentials.ApplicationTokenCredentials
import com.microsoft.azure.management.compute.ComputeManagementClient
import com.microsoft.azure.management.compute.ComputeManagementClientImpl
import com.microsoft.azure.management.compute.VirtualMachineImagesOperations
import com.microsoft.azure.management.compute.models.VirtualMachineImageResource
import com.microsoft.rest.ServiceResponse
import com.netflix.spinnaker.clouddriver.azure.client.AzureComputeClient
import spock.lang.Shared
import spock.lang.Specification

class AzureComputeClientSpec extends Specification{
  static final String AZURE_VMIMAGE_PUBLISHER = "publisher1"
  static final String AZURE_VMIMAGE_OFFER = "offer2"
  static final String AZURE_VMIMAGE_SKU = "sku3"
  static final String AZURE_VMIMAGE_VERSION = "version4"

  @Shared
  ObjectMapper mapper = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true)

  @Shared
  ApplicationTokenCredentials credentials = Mock(ApplicationTokenCredentials)

  @Shared
  ComputeManagementClient computeManagementClient = Mock(ComputeManagementClient)

  @Shared
  AzureComputeClient azureComputeClient = Mock(AzureComputeClient)

  def setupSpec() {
    VirtualMachineImageResource vmImagePublisher = Mock(VirtualMachineImageResource)
    VirtualMachineImageResource vmImageOffer = Mock(VirtualMachineImageResource)
    VirtualMachineImageResource vmImageSKU = Mock(VirtualMachineImageResource)
    VirtualMachineImageResource vmImageVersion = Mock(VirtualMachineImageResource)
    ServiceResponse<List<VirtualMachineImageResource>> srPublisher = Mock(ServiceResponse)
    ServiceResponse<List<VirtualMachineImageResource>> srOffer = Mock(ServiceResponse)
    ServiceResponse<List<VirtualMachineImageResource>> srSKU = Mock(ServiceResponse)
    ServiceResponse<List<VirtualMachineImageResource>> srVersion = Mock(ServiceResponse)
    VirtualMachineImagesOperations ops = Mock(VirtualMachineImagesOperations)
    computeManagementClient.getVirtualMachineImagesOperations() >> ops
    azureComputeClient =  new AzureComputeClient("subscriptionId", computeManagementClient)

    ops.listPublishers(_) >> srPublisher
    srPublisher.body >> [vmImagePublisher]
    vmImagePublisher.name >> AZURE_VMIMAGE_PUBLISHER

    ops.listOffers(_,_) >> srOffer
    srOffer.body >> [vmImageOffer, vmImageOffer]
    vmImageOffer.name >> AZURE_VMIMAGE_OFFER

    ops.listSkus(_,_,_) >> srSKU
    srSKU.body >> [vmImageSKU, vmImageSKU, vmImageSKU]
    vmImageSKU.name >> AZURE_VMIMAGE_SKU

    ops.list(_,_,_,_,_,_,_) >> srVersion
    srVersion.body >> [vmImageVersion, vmImageVersion, vmImageVersion, vmImageVersion]
    vmImageVersion.name >> AZURE_VMIMAGE_VERSION
  }

  void "Get all VM images"() {
    setup:

    when:
    def vmImages = mapper.writeValueAsString(azureComputeClient.getVMImagesAll("westus"))

    then:
    vmImages == expectedFullListVMImages
  }

  void "Create an AzureComputeClient instance"() {
    setup:

    when:
    def azureComputeClient = new AzureComputeClient("subscriptionId", credentials, "")

    then:
    azureComputeClient instanceof AzureComputeClient
    //true
  }

  void "List all the Azure VMImage publishers"() {
    setup:

    when:
    def vmImages = computeManagementClient.getVirtualMachineImagesOperations().listPublishers("westus").body.collect { it.name}

    then:
    vmImages == [AZURE_VMIMAGE_PUBLISHER]
  }

  void "List all the Azure VMImage offers"() {
    setup:

    when:
    def vmImages = computeManagementClient.getVirtualMachineImagesOperations().listOffers("westus", "publisher").body.collect { it.name}

    then:
    vmImages == [AZURE_VMIMAGE_OFFER, AZURE_VMIMAGE_OFFER]
  }

  void "List all the Azure VMImage SKUs"() {
    setup:

    when:
    def vmImages = computeManagementClient.getVirtualMachineImagesOperations().listSkus("westus", "publisher", "sku").body.collect { it.name}

    then:
    vmImages == [AZURE_VMIMAGE_SKU, AZURE_VMIMAGE_SKU, AZURE_VMIMAGE_SKU]
  }

  void "List all the Azure VMImage versions"() {
    setup:

    when:
    def vmImages = computeManagementClient.getVirtualMachineImagesOperations().list("westus", "publisher", "offer", "sku", null, 100, "name").body.collect { it.name}

    then:
    vmImages == [AZURE_VMIMAGE_VERSION, AZURE_VMIMAGE_VERSION, AZURE_VMIMAGE_VERSION, AZURE_VMIMAGE_VERSION]
  }

  private static String expectedFullListVMImages = '''[ {
  "publisher" : "publisher1",
  "offer" : "offer2",
  "sku" : "sku3",
  "version" : "version4"
}, {
  "publisher" : "publisher1",
  "offer" : "offer2",
  "sku" : "sku3",
  "version" : "version4"
}, {
  "publisher" : "publisher1",
  "offer" : "offer2",
  "sku" : "sku3",
  "version" : "version4"
}, {
  "publisher" : "publisher1",
  "offer" : "offer2",
  "sku" : "sku3",
  "version" : "version4"
}, {
  "publisher" : "publisher1",
  "offer" : "offer2",
  "sku" : "sku3",
  "version" : "version4"
}, {
  "publisher" : "publisher1",
  "offer" : "offer2",
  "sku" : "sku3",
  "version" : "version4"
}, {
  "publisher" : "publisher1",
  "offer" : "offer2",
  "sku" : "sku3",
  "version" : "version4"
}, {
  "publisher" : "publisher1",
  "offer" : "offer2",
  "sku" : "sku3",
  "version" : "version4"
}, {
  "publisher" : "publisher1",
  "offer" : "offer2",
  "sku" : "sku3",
  "version" : "version4"
}, {
  "publisher" : "publisher1",
  "offer" : "offer2",
  "sku" : "sku3",
  "version" : "version4"
}, {
  "publisher" : "publisher1",
  "offer" : "offer2",
  "sku" : "sku3",
  "version" : "version4"
}, {
  "publisher" : "publisher1",
  "offer" : "offer2",
  "sku" : "sku3",
  "version" : "version4"
}, {
  "publisher" : "publisher1",
  "offer" : "offer2",
  "sku" : "sku3",
  "version" : "version4"
}, {
  "publisher" : "publisher1",
  "offer" : "offer2",
  "sku" : "sku3",
  "version" : "version4"
}, {
  "publisher" : "publisher1",
  "offer" : "offer2",
  "sku" : "sku3",
  "version" : "version4"
}, {
  "publisher" : "publisher1",
  "offer" : "offer2",
  "sku" : "sku3",
  "version" : "version4"
}, {
  "publisher" : "publisher1",
  "offer" : "offer2",
  "sku" : "sku3",
  "version" : "version4"
}, {
  "publisher" : "publisher1",
  "offer" : "offer2",
  "sku" : "sku3",
  "version" : "version4"
}, {
  "publisher" : "publisher1",
  "offer" : "offer2",
  "sku" : "sku3",
  "version" : "version4"
}, {
  "publisher" : "publisher1",
  "offer" : "offer2",
  "sku" : "sku3",
  "version" : "version4"
}, {
  "publisher" : "publisher1",
  "offer" : "offer2",
  "sku" : "sku3",
  "version" : "version4"
}, {
  "publisher" : "publisher1",
  "offer" : "offer2",
  "sku" : "sku3",
  "version" : "version4"
}, {
  "publisher" : "publisher1",
  "offer" : "offer2",
  "sku" : "sku3",
  "version" : "version4"
}, {
  "publisher" : "publisher1",
  "offer" : "offer2",
  "sku" : "sku3",
  "version" : "version4"
} ]'''

}
