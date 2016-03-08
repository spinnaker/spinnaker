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

import com.microsoft.azure.credentials.ApplicationTokenCredentials
import com.microsoft.azure.management.compute.ComputeManagementClient
import com.microsoft.azure.management.compute.ComputeManagementClientImpl
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureVMImage
import groovy.util.logging.Slf4j
import groovy.transform.CompileStatic
import okhttp3.logging.HttpLoggingInterceptor


@Slf4j
@CompileStatic
public class AzureComputeClient extends AzureBaseClient {
  private final ComputeManagementClient client

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
      def vmImagesOps = client.getVirtualMachineImagesOperations()
      vmImagesOps?.listPublishers(location)?.body?.each { itemVMPublisher ->
        vmImagesOps?.listOffers(location, itemVMPublisher.name)?.body?.each { itemVMOffers ->
          vmImagesOps?.listSkus(location, itemVMPublisher.name, itemVMOffers.name)?.body?.each { itemVMSku ->
            vmImagesOps?.list(location, itemVMPublisher.name, itemVMOffers.name,
              itemVMSku.name, null, 100, "")?.body?.each { itemVMImage ->
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
