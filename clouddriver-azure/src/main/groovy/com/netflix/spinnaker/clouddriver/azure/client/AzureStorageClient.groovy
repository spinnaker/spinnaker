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
import com.microsoft.azure.management.storage.StorageAccountsOperations
import com.microsoft.azure.management.storage.StorageManagementClient
import com.microsoft.azure.management.storage.StorageManagementClientImpl
import com.microsoft.azure.storage.blob.CloudBlobClient
import com.microsoft.azure.storage.blob.CloudBlobContainer
import com.microsoft.azure.storage.blob.CloudBlobDirectory
import com.microsoft.azure.storage.blob.ListBlobItem
import com.microsoft.azure.storage.CloudStorageAccount
import com.microsoft.rest.ServiceResponse
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureCustomImageStorage
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureCustomVMImage
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import okhttp3.logging.HttpLoggingInterceptor

@Slf4j
@CompileStatic
class AzureStorageClient extends AzureBaseClient {
  static final String AZURE_IMAGE_FILE_EXT = ".vhd"

  private final StorageManagementClient client

  AzureStorageClient(String subscriptionId, ApplicationTokenCredentials credentials, String userAgentApplicationName) {
    super(subscriptionId, userAgentApplicationName)
    this.client = this.initialize(credentials)
  }

  /**
   * get the StorageManagementClient which will be used for all interaction related to compute resources in Azure
   * @param creds the credentials to use when communicating to the Azure subscription(s)
   * @return an instance of the Azure StorageManagementClient
   */
  private StorageManagementClient initialize(ApplicationTokenCredentials tokenCredentials) {
    StorageManagementClient storageClient = new StorageManagementClientImpl(buildBaseUrl(tokenCredentials), tokenCredentials)
    storageClient.setSubscriptionId(this.subscriptionId)
    storageClient.setLogLevel(HttpLoggingInterceptor.Level.NONE)
    storageClient
  }

  /**
   * Delete a storage account in the resource group specified
   * @param resourceGroupName Resource Group in Azure where the storage account will exist
   * @param storageName Name of the storage account to delete
   * @throws RuntimeException Throws RuntimeException if operation response indicates failure
   * @return a ServiceResponse object
   */
  ServiceResponse<Void> deleteStorageAccount(String resourceGroupName, String storageName) {
    StorageAccountsOperations ops = client.getStorageAccountsOperations()

    deleteAzureResource(
      ops.&delete,
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

          // Retrieve storage account from connection-string.
          CloudStorageAccount storageAccount = CloudStorageAccount.parse(storage.scs)

          // retrieve the blob client.
          CloudBlobClient blobClient = storageAccount.createCloudBlobClient()
          String dirDelimiter = blobClient.getDirectoryDelimiter()
          blobDirectoryList.addAll(storage.blobDir.split(dirDelimiter))
          def container = blobClient.getContainerReference(blobDirectoryList.remove(0))

          if (container) {
            if (blobDirectoryList.size()) {
              def dir = blobDirectoryList.remove(0)
              def blob = container.getDirectoryReference(dir)

              while (blobDirectoryList.size()) {
                dir = blobDirectoryList.remove(0)
                blob = blob.getDirectoryReference(dir)
              }

              if (blob) {
                getBlobsContent(blob, AZURE_IMAGE_FILE_EXT).each { String uri ->
                  vmImages.add(getAzureCustomVMImage(uri, dirDelimiter, storage.osType, storage.region))
                }
              }
            } else {
              getBlobsContent(container, AZURE_IMAGE_FILE_EXT).each { String uri ->
                vmImages.add(getAzureCustomVMImage(uri, dirDelimiter, storage.osType, storage.region))
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
   * @param blobDir - CloudBlobDirectory to retrieve the content from
   * @param filter - extension of the files to be retrieved
   * @return List of URI strings corresponding to the files found
   */
  static List<String> getBlobsContent(CloudBlobDirectory blobDir, String filter) {
    def uriList = new ArrayList<String>()

    blobDir.listBlobs().each { ListBlobItem blob ->
      if (blob.uri.toString().toLowerCase().endsWith(filter)) {
        uriList.add(blob.uri.toString())
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
  static List<String> getBlobsContent(CloudBlobContainer container, String filter) {
    def uriList = new ArrayList<String>()

    container?.listBlobs()?.each { ListBlobItem blob ->
      if (blob.uri.toString().toLowerCase().endsWith(filter)) {
        uriList.add(blob.uri.toString())
      }
    }

    uriList
  }

  /**
   * Return list of files in a CloudBlobDirectory matching a filter recursively
   * @param blobDir - CloudBlobDirectory to retrieve the content from
   * @param filter - extension of the files to be retrieved
   * @return List of URI strings corresponding to the files found
   */
  static List<String> getBlobsContentAll(CloudBlobDirectory blobDir, String filter) {
    def uriList = new ArrayList<String>()

    blobDir?.listBlobs()?.each { ListBlobItem blob ->
      try {
        // try converting current blob item to a CloudBlobDirectory; if conversion fails an exception is thrown
        CloudBlobDirectory blobDirectory = blob as CloudBlobDirectory
        if (blobDirectory) {
          uriList.addAll(getBlobsContentAll(blobDirectory, filter))
        }
      } catch(Exception e) {
        // blob must be a regular item
        if (blob.uri.toString().toLowerCase().endsWith(filter)) {
          uriList.add(blob.uri.toString())
        }
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
  static List<String> getBlobsContentAll(CloudBlobContainer container, String filter) {
    def uriList = new ArrayList<String>()

    container?.listBlobs()?.each { ListBlobItem blob ->
      try {
        CloudBlobDirectory blobDirectory = blob as CloudBlobDirectory
        if (blobDirectory) {
          uriList.addAll(getBlobsContentAll(blobDirectory, filter))
        }
      } catch(Exception e) {
        // blob must be a regular item
        if (blob.uri.toString().toLowerCase().endsWith(filter)) {
          uriList.add(blob.uri.toString())
        }
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
