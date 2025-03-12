/*
 * Copyright 2019 Microsoft Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.kayenta.blobs.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.BlobProperties;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.blob.ListBlobItem;
import com.netflix.kayenta.index.CanaryConfigIndex;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestableBlobsStorageService extends BlobsStorageService {

  public HashMap<String, String> blobStored = new HashMap<>();

  TestableBlobsStorageService(
      List<String> accountNames,
      ObjectMapper kayentaObjectMapper,
      AccountCredentialsRepository accountCredentialsRepository,
      CanaryConfigIndex canaryConfigIndex) {
    super(accountNames, kayentaObjectMapper, accountCredentialsRepository, canaryConfigIndex);
  }

  @Override
  protected Iterable<ListBlobItem> listBlobs(
      CloudBlobContainer container, String prefix, boolean useFlatBlobListing, boolean isFolder) {
    String value = blobStored.get("exceptionKey");
    if (value != null && value.equals("1")) {
      throw new IllegalArgumentException("Item not found at " + prefix);
    } else {
      Iterable<ListBlobItem> mockBlobItems = new ArrayList<>();
      String filename = "canary_test.json";
      try {
        if (isFolder) {
          for (int folderItem = 1; folderItem <= 6; folderItem++) {
            URI uri =
                new URI(
                    "http://cloudblob.blob/sample-container/"
                        + prefix
                        + "/(GUID"
                        + folderItem
                        + ")/"
                        + filename);
            CloudBlockBlob fakeBlobItem = new CloudBlockBlob(uri);
            ((ArrayList<ListBlobItem>) mockBlobItems).add(fakeBlobItem);
            blobStored.put(
                String.format("deleteIfexists(%s)", prefix + "/" + filename), "not_invoked");
          }
        } else {
          URI uri = new URI("http://cloudblob.blob/sample-container/" + prefix + "/" + filename);
          CloudBlockBlob fakeBlobItem = new CloudBlockBlob(uri);
          ((ArrayList<ListBlobItem>) mockBlobItems).add(fakeBlobItem);
          blobStored.put(
              String.format("deleteIfexists(%s)", prefix + "/" + filename), "not_invoked");
        }

        return mockBlobItems;
      } catch (StorageException | URISyntaxException e) {
        log.error("Failed to initialiaze, Test Blob" + e.getMessage());
      }

      return mockBlobItems;
    }
  }

  @Override
  protected String downloadText(CloudBlockBlob blob) {
    String downloadedTextExample;

    if (blobStored.get("exceptionKey").equals("2")) {
      downloadedTextExample = "{\"applications\":[ + blobStored.get(\"application\") + ]}";
    } else {
      downloadedTextExample = "{\"applications\":[\"" + blobStored.get("application") + "\"]}";
    }

    return downloadedTextExample;
  }

  @Override
  public CloudBlockBlob getBlockBlobReference(CloudBlobContainer container, final String blobName)
      throws URISyntaxException, StorageException {
    URI uri = new URI("http://cloudblob.blob/sample-container/" + blobName);
    return new CloudBlockBlob(uri);
  }

  @Override
  public void uploadFromByteArray(
      CloudBlockBlob blob, final byte[] bytes, final int offset, final int length) {
    blobStored.put("blob", blob.getName());
    blobStored.put("length", Integer.toString(length));
  }

  @Override
  public void deleteIfExists(CloudBlockBlob blob) {
    blobStored.put(String.format("deleteIfexists(%s)", blob.getName()), "invoked");
  }

  @Override
  public Date getLastModified(BlobProperties properties) {
    return new Date();
  }

  @Override
  public void createIfNotExists(CloudBlobContainer container) {}
}
