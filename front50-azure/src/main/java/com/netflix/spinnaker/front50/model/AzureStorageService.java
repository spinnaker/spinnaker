/*
 * Copyright 2017 Microsoft, Inc.
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

package com.netflix.spinnaker.front50.model;

import static net.logstash.logback.argument.StructuredArguments.value;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.ResultContinuation;
import com.microsoft.azure.storage.ResultSegment;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.*;
import com.netflix.spinnaker.front50.exception.NotFoundException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.stream.Collectors;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AzureStorageService implements StorageService {
  private static final Logger log = LoggerFactory.getLogger(AzureStorageService.class);
  private String containerName;
  private CloudStorageAccount storageAccount = null;
  private CloudBlobClient blobClient = null;
  private CloudBlobContainer blobContainer = null;
  private ObjectMapper objectMapper = new ObjectMapper();

  private static final String LAST_MODIFIED_FILENAME = "last_modified";
  private static final String LAST_MODIFIED_METADATA_NAME = "lastmodifydate";
  private static final int NUM_OF_SNAPSHOT_LIMITATION = 5000;

  private CloudBlobClient getBlobClient() {
    if (storageAccount != null && blobClient == null) {
      blobClient = storageAccount.createCloudBlobClient();
    }
    return blobClient;
  }

  private CloudBlobContainer getBlobContainer() {
    if (storageAccount != null && blobContainer == null) {
      try {
        blobContainer = getBlobClient().getContainerReference(this.containerName);
        blobContainer.createIfNotExists();
        BlobContainerPermissions permissions = new BlobContainerPermissions();
        permissions.setPublicAccess(BlobContainerPublicAccessType.CONTAINER);
        blobContainer.uploadPermissions(permissions);
      } catch (Exception e) {
        // log exception
        blobContainer = null;
      }
    }
    return blobContainer;
  }

  public AzureStorageService(String connectionString, String containerName) {
    this.containerName = containerName;
    try {
      this.storageAccount = CloudStorageAccount.parse(connectionString);
    } catch (Exception e) {
      // Log the exception
      this.storageAccount = null;
    }
  }

  @Override
  public void ensureBucketExists() {
    // do nothing
  }

  @Override
  public boolean supportsVersioning() {
    return true;
  }

  @Override
  public <T extends Timestamped> T loadObject(ObjectType objectType, String objectKey) {
    String key = buildKeyPath(objectType.group, objectKey, objectType.defaultMetadataFilename);
    try {
      CloudBlockBlob blob = getBlobContainer().getBlockBlobReference(key);
      if (blob.exists()) {
        return deserialize(blob, (Class<T>) objectType.clazz);
      }
      throw new NotFoundException(
          "Object not found (key: " + objectKey + ", group: " + objectType.group + ")");
    } catch (StorageException se) {
      logStorageException(se, key);
      if (se.getHttpStatusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
        throw new NotFoundException(
            "Object not found (key: " + objectKey + ", group: " + objectType.group + ")");
      }
      throw new RuntimeException(se);
    } catch (Exception e) {
      throw new IllegalStateException(
          "Unable to fetch object (key: " + objectKey + ", group: " + objectType.group + ")");
    }
  }

  @Override
  public void deleteObject(ObjectType objectType, String objectKey) {
    String key = buildKeyPath(objectType.group, objectKey, objectType.defaultMetadataFilename);
    try {
      CloudBlockBlob blob = getBlobContainer().getBlockBlobReference(key);
      if (blob.deleteIfExists(DeleteSnapshotsOption.INCLUDE_SNAPSHOTS, null, null, null)) {
        log.info(
            "{} object {} has been successfully deleted",
            value("group", objectType.group),
            value("key", key));
      }
      writeLastModified(objectType.group);
    } catch (StorageException se) {
      logStorageException(se, key);
    } catch (Exception e) {
      log.error(
          "Error encountered attempting to delete {} from storage: {}",
          value("key", key),
          value("exception", e.getMessage()));
    }
  }

  @Override
  public <T extends Timestamped> void storeObject(ObjectType objectType, String objectKey, T item) {

    String key = buildKeyPath(objectType.group, objectKey, objectType.defaultMetadataFilename);
    try {
      item.setLastModifiedBy(AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"));
      byte[] bytes = objectMapper.writeValueAsBytes(item);

      CloudBlockBlob blob = this.getBlobContainer().getBlockBlobReference(key);
      if (blob.exists() && supportsVersioning()) {
        blob.createSnapshot();
      }
      blob.uploadFromByteArray(bytes, 0, bytes.length);
      writeLastModified(objectType.group);
      log.info(
          "{} object {} for  has been successfully uploaded.",
          value("group", objectType.group),
          value("key", key));
    } catch (StorageException se) {
      logStorageException(se, key);
    } catch (Exception e) {
      log.error(
          "Error encountered attempting to store {}: {}",
          value("key", key),
          value("exception", e.getMessage()));
    }
  }

  @Override
  public Map<String, Long> listObjectKeys(ObjectType objectType) {
    Map<String, Long> objectKeys = new HashMap<>();
    try {
      ResultContinuation token = null;
      do {
        ResultSegment<ListBlobItem> result =
            getBlobContainer()
                .listBlobsSegmented(objectType.group, true, null, 10000, token, null, null);
        token = result.getContinuationToken();

        result.getResults().stream()
            .filter(item -> match(item, objectType.defaultMetadataFilename))
            .forEach(
                item -> {
                  CloudBlob blob = (CloudBlob) item;
                  DateTime modDate = new DateTime(blob.getProperties().getLastModified());
                  objectKeys.put(
                      getBlobKey(objectType, blob.getUri().toString()), modDate.getMillis());
                });

      } while (token != null);
    } catch (StorageException se) {
      logStorageException(se, "");
    } catch (Exception e) {
      log.error(
          "Failed to retrieve objects from {}: {}",
          value("group", objectType.group),
          value("exception", e.getMessage()));
    }
    return objectKeys;
  }

  @Override
  public <T extends Timestamped> Collection<T> listObjectVersions(
      ObjectType objectType, String objectKey, int maxResults) throws NotFoundException {
    Set<T> results = new HashSet<>();
    String fullKey = buildKeyPath(objectType.group, objectKey, objectType.defaultMetadataFilename);
    try {
      ResultContinuation token = null;
      EnumSet<BlobListingDetails> listDetails = EnumSet.of(BlobListingDetails.SNAPSHOTS);
      do {
        ResultSegment<ListBlobItem> result =
            getBlobContainer()
                .listBlobsSegmented(
                    fullKey, true, listDetails, NUM_OF_SNAPSHOT_LIMITATION, token, null, null);
        token = result.getContinuationToken();

        // By default, listBlobsSegmented with maxResult parameter will return oldest blob snapshots
        // But here we want the latest blob snapshots, and storage sdk doesn't provide a way to get
        // latest number of snapshots
        // So fetch all and then do sort and filter work (SnapshotID == null means the latest one)
        List<CloudBlockBlob> filteredResults =
            result.getResults().stream()
                .map(item -> (CloudBlockBlob) item)
                .sorted(
                    (a, b) -> {
                      if (a.getSnapshotID() == null) return -1;
                      if (b.getSnapshotID() == null) return 1;
                      return b.getSnapshotID().compareTo(a.getSnapshotID());
                    })
                .limit(maxResults)
                .collect(Collectors.toList());
        for (ListBlobItem item : filteredResults) {
          CloudBlockBlob blob = (CloudBlockBlob) item;
          T blobObject = deserialize(blob, (Class<T>) objectType.clazz);
          blobObject.setLastModified(blob.getProperties().getLastModified().getTime());
          results.add(blobObject);
        }

      } while (token != null);
    } catch (StorageException se) {
      logStorageException(se, fullKey);
    } catch (Exception e) {
      log.error(
          "Error retrieving versions for {} object: {}",
          value("key", fullKey),
          value("exception", e.getMessage()));
    }
    return results;
  }

  @Override
  public long getLastModified(ObjectType objectType) {
    try {
      CloudBlockBlob lastMod =
          getBlobContainer().getBlockBlobReference(getLastModifiedFile(objectType.group));
      if (lastMod.exists()) {
        String dateInMillis = lastMod.getMetadata().get(LAST_MODIFIED_METADATA_NAME);
        return Long.parseLong(dateInMillis);
      }
    } catch (StorageException se) {
      logStorageException(se, "");
    } catch (Exception e) {
      log.error(
          "Exception occurred retrieving last modifed for {}: {}",
          value("group", objectType.group),
          value("exception", e.getMessage()));
    }
    return 0;
  }

  private void writeLastModified(String group) {
    try {
      CloudBlockBlob blob = getBlobContainer().getBlockBlobReference(getLastModifiedFile(group));
      HashMap<String, String> metadata = blob.getMetadata();
      String dateinMillis = String.valueOf(System.currentTimeMillis());
      metadata.put(LAST_MODIFIED_METADATA_NAME, dateinMillis);
      blob.uploadText(group + ":" + dateinMillis);
      blob.setMetadata(metadata);
    } catch (StorageException se) {
      logStorageException(se, "");
    } catch (Exception e) {
      log.error(
          "Exception occurred setting last modified date/time: {} ",
          value("exception", e.getMessage()));
    }
  }

  private <T extends Timestamped> T deserialize(CloudBlob blobOject, Class<T> clazz)
      throws IOException, StorageException {
    byte[] data = new byte[(int) blobOject.getProperties().getLength()];
    for (int i = 0; i < data.length; i++) {
      data[i] = 0x00;
    }
    blobOject.downloadToByteArray(data, 0);
    return objectMapper.readValue(data, clazz);
  }

  private void logStorageException(StorageException storageException, String key) {
    String errorMsg = storageException.getExtendedErrorInformation().getErrorMessage();
    String errorCode = storageException.getExtendedErrorInformation().getErrorCode();
    if (key.isEmpty()) {
      log.error(
          "Exception occurred accessing object(s) from storage: HTTPStatusCode {} ErrorCode: {} {}",
          value("responseStatus", storageException.getHttpStatusCode()),
          value("errorCode", errorCode),
          value("errorMsg", errorMsg));
    } else {
      log.error(
          "Exception occurred accessing object(s) from storage: Key {} HTTPStatusCode {} ErrorCode: {} {}",
          value("key", key),
          value("responseStatus", storageException.getHttpStatusCode()),
          value("errorCode", errorCode),
          value("errorMsg", errorMsg));
    }
  }

  // This builds the path under the container to the blob based on the object "key"
  private String buildKeyPath(String type, String objectKey, String metadatafilename) {
    if (objectKey.endsWith(metadatafilename)) {
      return objectKey;
    }
    String key = type + "/" + objectKey.toLowerCase();
    if (!metadatafilename.isEmpty()) {
      key += "/" + metadatafilename.replace("//", "/");
    }
    return key;
  }

  private String getBlobKey(ObjectType type, String storageKey) {
    return storageKey
        .replace(getBlobContainer().getUri().toString() + "/" + type.group + "/", "")
        .replace("/" + type.defaultMetadataFilename, "");
  }

  private String getLastModifiedFile(String group) {
    return group + "/'" + LAST_MODIFIED_FILENAME;
  }

  // URI of Blob:
  // http://yourstorageaccount.blob.core.windows.net/container/type/key/metadatafilename
  private boolean match(ListBlobItem item, String compValue) {
    CloudBlob blob = (CloudBlob) item;
    return blob.getUri().toString().endsWith(compValue);
  }
}
