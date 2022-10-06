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
import com.azure.core.management.profile.AzureProfile
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobContainerClientBuilder
import com.azure.storage.blob.models.BlobItem
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureCustomImageStorage
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureCustomVMImage
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
class AzureStorageClient extends AzureBaseClient {
  static final String AZURE_IMAGE_FILE_EXT = ".vhd"

  AzureStorageClient(String subscriptionId, TokenCredential credentials, AzureProfile azureProfile) {
    super(subscriptionId, azureProfile, credentials)
  }

  /**
   * Delete a storage account in the resource group specified
   * @param resourceGroupName Resource Group in Azure where the storage account will exist
   * @param storageName Name of the storage account to delete
   * @throws RuntimeException Throws RuntimeException if operation response indicates failure
   * @return a ServiceResponse object
   */
  Response<Void> deleteStorageAccount(String resourceGroupName, String storageName) {

    deleteAzureResource(
      azure.storageAccounts().&deleteByResourceGroup,
      resourceGroupName,
      storageName,
      null,
      "Delete Storage Account ${storageName}",
      "Failed to delete Storage Account ${storageName} in ${resourceGroupName}"
    )
  }

  /**
   * Return list of available .VHD files from a list of connection strings
   * @param imageStorageList - list of connection strings, relative paths, regions and os types
   * @return List of AzureCustomVMImage
   */
  static List<AzureCustomVMImage> getCustomImages(List<AzureCustomImageStorage> imageStorageList) {
    def vmImages = new ArrayList<AzureCustomVMImage>()

    imageStorageList?.each {AzureCustomImageStorage storage ->
      if (storage && storage.scs && storage.blobDir && storage.osType) {
        try {
          ArrayList<String> blobDirectoryList = []
          blobDirectoryList.addAll(storage.blobDir.split("/"))
          final BlobContainerClient blobContainerClient = new BlobContainerClientBuilder()
            .connectionString(storage.scs)
            .containerName(blobDirectoryList.remove(0))

            .buildClient()
          if (blobContainerClient.exists()) {
            if (blobDirectoryList.size()) {
              String folderPath = blobDirectoryList.join("/")


                getBlobsContent(blobContainerClient.listBlobsByHierarchy(folderPath).asList(), AZURE_IMAGE_FILE_EXT).each { String uri ->
                  vmImages.add(getAzureCustomVMImage(uri, '/', storage.osType, storage.region))
                }

            } else {
              getBlobsContent(blobContainerClient, AZURE_IMAGE_FILE_EXT).each { String uri ->
                vmImages.add(getAzureCustomVMImage(uri, "/", storage.osType, storage.region))
              }
            }
          }
        }
        catch (Exception e) {
          // Most likely reason we got here was an invalid storage connection string
          //  log an error but without the full exception stack
          log.error("getCustomImages -> Unexpected exception: ${e.message}")
        }
      }
    }

    vmImages
  }

  /**
   * Return list of files in a CloudBlobDirectory matching a filter
   * @param blobItems - CloudBlobDirectory to retrieve the content from
   * @param filter - extension of the files to be retrieved
   * @return List of URI strings corresponding to the files found
   */
  static List<String> getBlobsContent(List<BlobItem> blobItems, String filter) {
    def uriList = new ArrayList<String>()

    blobItems.each { BlobItem blob ->
      if (blob.getName().toLowerCase().endsWith(filter)) {
        uriList.add(blob.getName())
      }
    }

    uriList
  }

  /**
   * Return list of files in a CloudBlobContainer matching a filter
   * @param blobDir - CloudBlobContainer to retrieve the content from
   * @param filter - extension of the files to be retrieved
   * @return List of URI strings corresponding to the files found
   */
  static List<String> getBlobsContent(BlobContainerClient container, String filter) {
    def uriList = new ArrayList<String>()

    container?.listBlobs()?.each { BlobItem blob ->
      if (blob.getName().toLowerCase().endsWith(filter)) {
        uriList.add(blob.getName())
      }
    }

    uriList
  }

  /**
   * Return list of files in a CloudBlobContainer matching a filter recursively
   * @param blobDir - CloudBlobContainer to retrieve the content from
   * @param filter - extension of the files to be retrieved
   * @return List of URI strings corresponding to the files found
   */
  static List<String> getBlobsContentAll(BlobContainerClient container, String filter) {
    def uriList = new ArrayList<String>()

    container?.listBlobs()?.each { BlobItem blob ->

        if (blob.isPrefix()) {
          uriList.addAll(getBlobsContentAll(container.getBlobClient(blob.getName()).getContainerClient(), filter))
        } else if (blob.getName().toLowerCase().endsWith(filter)) {
          uriList.add(blob.getName())
        }
    }

    uriList
  }

  static AzureCustomVMImage getAzureCustomVMImage(String uri, String delimiter, String osType, String region) {
    String imageName = uri
    def idx = imageName.lastIndexOf(delimiter)
    if (idx > 0) {
      imageName = imageName.substring(idx+1)
    }

    new AzureCustomVMImage(
      name: imageName,
      uri: uri.toString(),
      osType: osType,
      region: region
    )
  }

  /***
   * The namespace for the Azure Resource Provider
   * @return namespace of the resource provider
   */
  @Override
  String getProviderNamespace() {
    "Microsoft.Storage"
  }

}
