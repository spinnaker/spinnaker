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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.microsoft.azure.storage.*;
import com.microsoft.azure.storage.blob.*;
import com.netflix.kayenta.azure.security.AzureNamedAccountCredentials;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.index.CanaryConfigIndex;
import com.netflix.kayenta.index.config.CanaryConfigIndexAction;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.*;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

@Builder
@Slf4j
public class BlobsStorageService implements StorageService {

  @NotNull @Singular @Getter private List<String> accountNames;

  @Autowired private ObjectMapper kayentaObjectMapper;

  @Autowired AccountCredentialsRepository accountCredentialsRepository;

  @Autowired CanaryConfigIndex canaryConfigIndex;

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  @Override
  public <T> T loadObject(String accountName, ObjectType objectType, String objectKey)
      throws IllegalArgumentException, NotFoundException {
    AzureNamedAccountCredentials credentials =
        (AzureNamedAccountCredentials)
            accountCredentialsRepository
                .getOne(accountName)
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "Unable to resolve account " + accountName + "."));
    CloudBlobContainer azureContainer = credentials.getAzureContainer();
    CloudBlockBlob blobItem;
    try {
      blobItem = resolveSingularBlob(objectType, objectKey, credentials, azureContainer);
    } catch (IllegalArgumentException e) {
      throw new NotFoundException(e.getMessage());
    }

    try {
      return deserialize(blobItem, (TypeReference<T>) objectType.getTypeReference());
    } catch (IOException | StorageException e) {
      throw new IllegalStateException("Unable to deserialize object (key: " + objectKey + ")", e);
    }
  }

  private CloudBlockBlob resolveSingularBlob(
      ObjectType objectType,
      String objectKey,
      AzureNamedAccountCredentials credentials,
      CloudBlobContainer azureContainer) {
    String rootFolder = daoRoot(credentials, objectType.getGroup()) + "/" + objectKey;

    try {
      Iterable<ListBlobItem> blobItems = listBlobs(azureContainer, rootFolder, true, false);
      CloudBlockBlob foundBlockItem = null;

      int size = 0;
      for (ListBlobItem blobItem : blobItems) {
        if (size > 1) {
          throw new IllegalArgumentException(
              "Unable to resolve singular "
                  + objectType
                  + " at "
                  + daoRoot(credentials, objectType.getGroup())
                  + '/'
                  + objectKey
                  + ".");
        }

        if (blobItem instanceof CloudBlockBlob) {
          foundBlockItem = (CloudBlockBlob) blobItem;
          size++;
        }
      }
      if ((foundBlockItem != null) && size == 1) {
        return foundBlockItem;
      } else {
        throw new IllegalArgumentException(
            "No "
                + objectType
                + " found at "
                + daoRoot(credentials, objectType.getGroup())
                + '/'
                + objectKey
                + ".{Null value}");
      }
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Could not fetch items from Azure Cloud Storage: " + e.getMessage(), e);
    }
  }

  @Override
  public <T> void storeObject(
      String accountName,
      ObjectType objectType,
      String objectKey,
      T obj,
      String filename,
      boolean isAnUpdate) {
    AzureNamedAccountCredentials credentials =
        (AzureNamedAccountCredentials)
            accountCredentialsRepository
                .getOne(accountName)
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "Unable to resolve account " + accountName + "."));
    CloudBlobContainer azureContainer = credentials.getAzureContainer();
    String path = keyToPath(credentials, objectType, objectKey, filename);

    try {
      createIfNotExists(azureContainer);
    } catch (StorageException e) {
      log.error("Unable to create cloud container", e);
    }

    long updatedTimestamp = -1;
    String correlationId = null;
    String canaryConfigSummaryJson = null;
    CloudBlockBlob originalItem = null;

    if (objectType == ObjectType.CANARY_CONFIG) {
      updatedTimestamp = canaryConfigIndex.getRedisTime();

      CanaryConfig canaryConfig = (CanaryConfig) obj;
      checkForDuplicateCanaryConfig(canaryConfig, objectKey, credentials);

      if (isAnUpdate) {
        // Storing a canary config while not checking for naming collisions can only be a PUT (i.e.
        // an update to an existing config).
        originalItem = resolveSingularBlob(objectType, objectKey, credentials, azureContainer);
      }

      correlationId = UUID.randomUUID().toString();

      Map<String, Object> canaryConfigSummary =
          new ImmutableMap.Builder<String, Object>()
              .put("id", objectKey)
              .put("name", canaryConfig.getName())
              .put("updatedTimestamp", updatedTimestamp)
              .put("updatedTimestampIso", Instant.ofEpochMilli(updatedTimestamp).toString())
              .put("applications", canaryConfig.getApplications())
              .build();

      try {
        canaryConfigSummaryJson = kayentaObjectMapper.writeValueAsString(canaryConfigSummary);
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException(
            "Problem serializing canaryConfigSummary -> " + canaryConfigSummary, e);
      }

      canaryConfigIndex.startPendingUpdate(
          credentials,
          updatedTimestamp + "",
          CanaryConfigIndexAction.UPDATE,
          correlationId,
          canaryConfigSummaryJson);
    }

    try {
      byte[] bytes = kayentaObjectMapper.writeValueAsBytes(obj);
      CloudBlockBlob blob = getBlockBlobReference(azureContainer, path);
      blob.getProperties().setContentType("application/json");
      uploadFromByteArray(blob, bytes, 0, bytes.length);

      if (objectType == ObjectType.CANARY_CONFIG) {
        // This will be true if the canary config is renamed.
        if (originalItem != null && !originalItem.getName().equals(path)) {
          deleteIfExists(originalItem);
        }

        canaryConfigIndex.finishPendingUpdate(
            credentials, CanaryConfigIndexAction.UPDATE, correlationId);
      }
    } catch (IOException e) {
      log.error("Update failed on path {}: {}", path, e);

      if (objectType == ObjectType.CANARY_CONFIG) {
        canaryConfigIndex.removeFailedPendingUpdate(
            credentials,
            updatedTimestamp + "",
            CanaryConfigIndexAction.UPDATE,
            correlationId,
            canaryConfigSummaryJson);
      }

      throw new IllegalArgumentException(e);

    } catch (URISyntaxException e) {
      log.error("URI Syntax Exception on path {}: {}", path, e);
    } catch (StorageException e) {
      log.error("Storage Exception on path {}: {}", path, e);
    }
  }

  private void checkForDuplicateCanaryConfig(
      CanaryConfig canaryConfig, String canaryConfigId, AzureNamedAccountCredentials credentials) {
    String canaryConfigName = canaryConfig.getName();
    List<String> applications = canaryConfig.getApplications();
    String existingCanaryConfigId =
        canaryConfigIndex.getIdFromName(credentials, canaryConfigName, applications);

    // We want to avoid creating a naming collision due to the renaming of an existing canary
    // config.
    if (!StringUtils.isEmpty(existingCanaryConfigId)
        && !existingCanaryConfigId.equals(canaryConfigId)) {
      throw new IllegalArgumentException(
          "Canary config with name '"
              + canaryConfigName
              + "' already exists in the scope of applications "
              + applications
              + ".");
    }
  }

  @Override
  public void deleteObject(String accountName, ObjectType objectType, String objectKey) {
    AzureNamedAccountCredentials credentials =
        (AzureNamedAccountCredentials)
            accountCredentialsRepository
                .getOne(accountName)
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "Unable to resolve account " + accountName + "."));
    CloudBlobContainer azureContainer = credentials.getAzureContainer();
    CloudBlockBlob item = resolveSingularBlob(objectType, objectKey, credentials, azureContainer);

    long updatedTimestamp = -1;
    String correlationId = null;
    String canaryConfigSummaryJson = null;

    if (objectType == ObjectType.CANARY_CONFIG) {
      updatedTimestamp = canaryConfigIndex.getRedisTime();

      Map<String, Object> existingCanaryConfigSummary =
          canaryConfigIndex.getSummaryFromId(credentials, objectKey);

      if (existingCanaryConfigSummary != null) {
        String canaryConfigName = (String) existingCanaryConfigSummary.get("name");
        List<String> applications = (List<String>) existingCanaryConfigSummary.get("applications");

        correlationId = UUID.randomUUID().toString();

        Map<String, Object> canaryConfigSummary =
            new ImmutableMap.Builder<String, Object>()
                .put("id", objectKey)
                .put("name", canaryConfigName)
                .put("updatedTimestamp", updatedTimestamp)
                .put("updatedTimestampIso", Instant.ofEpochMilli(updatedTimestamp).toString())
                .put("applications", applications)
                .build();

        try {
          canaryConfigSummaryJson = kayentaObjectMapper.writeValueAsString(canaryConfigSummary);
        } catch (JsonProcessingException e) {
          throw new IllegalArgumentException(
              "Problem serializing canaryConfigSummary -> " + canaryConfigSummary, e);
        }

        canaryConfigIndex.startPendingUpdate(
            credentials,
            updatedTimestamp + "",
            CanaryConfigIndexAction.DELETE,
            correlationId,
            canaryConfigSummaryJson);
      }
    }

    try {
      deleteIfExists(item);
      if (correlationId != null) {
        canaryConfigIndex.finishPendingUpdate(
            credentials, CanaryConfigIndexAction.DELETE, correlationId);
      }
    } catch (StorageException e) {
      log.error("Failed to delete path {}: {}", item.getName(), e);

      if (correlationId != null) {
        canaryConfigIndex.removeFailedPendingUpdate(
            credentials,
            updatedTimestamp + "",
            CanaryConfigIndexAction.DELETE,
            correlationId,
            canaryConfigSummaryJson);
      }
    }
  }

  @Override
  public List<Map<String, Object>> listObjectKeys(
      String accountName, ObjectType objectType, List<String> applications, boolean skipIndex) {
    AzureNamedAccountCredentials credentials =
        (AzureNamedAccountCredentials)
            accountCredentialsRepository
                .getOne(accountName)
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "Unable to resolve account " + accountName + "."));

    if (!skipIndex && objectType == ObjectType.CANARY_CONFIG) {
      Set<Map<String, Object>> canaryConfigSet =
          canaryConfigIndex.getCanaryConfigSummarySet(credentials, applications);
      return Lists.newArrayList(canaryConfigSet);
    } else {
      CloudBlobContainer azureContainer = credentials.getAzureContainer();
      String rootFolder = daoRoot(credentials, objectType.getGroup());

      try {
        createIfNotExists(azureContainer);
      } catch (StorageException e) {
        log.error("Unable to create cloud container", e);
      }

      int skipToOffset = rootFolder.length() + 1; // + Trailing slash
      List<Map<String, Object>> result = new ArrayList<>();

      log.debug("Listing {}", objectType.getGroup());

      Iterable<ListBlobItem> blobItems = listBlobs(azureContainer, rootFolder, true, true);

      if (blobItems != null) {
        for (ListBlobItem blobItem : blobItems) {
          if (blobItem instanceof CloudBlockBlob) {
            CloudBlockBlob blob = (CloudBlockBlob) blobItem;
            String itemName = blob.getName();
            int indexOfLastSlash = itemName.lastIndexOf("/");
            Map<String, Object> objectMetadataMap = new HashMap<>();
            BlobProperties properties = blob.getProperties();
            long updatedTimestamp = (getLastModified(properties)).getTime();
            objectMetadataMap.put("id", itemName.substring(skipToOffset, indexOfLastSlash));
            objectMetadataMap.put("updatedTimestamp", updatedTimestamp);
            objectMetadataMap.put(
                "updatedTimestampIso", Instant.ofEpochMilli(updatedTimestamp).toString());

            if (objectType == ObjectType.CANARY_CONFIG) {
              String name = itemName.substring(indexOfLastSlash + 1);

              if (name.endsWith(".json")) {
                name = name.substring(0, name.length() - 5);
              }

              objectMetadataMap.put("name", name);
            }

            result.add(objectMetadataMap);
          }
        }
      }

      return result;
    }
  }

  private String daoRoot(AzureNamedAccountCredentials credentials, String daoTypeName) {
    return credentials.getRootFolder() + '/' + daoTypeName;
  }

  private <T> T deserialize(CloudBlockBlob blob, TypeReference<T> typeReference)
      throws IOException, StorageException {
    String json = downloadText(blob);
    return kayentaObjectMapper.readValue(json, typeReference);
  }

  private String keyToPath(
      AzureNamedAccountCredentials credentials,
      ObjectType objectType,
      String objectKey,
      String filename) {
    if (filename == null) {
      filename = objectType.getDefaultFilename();
    }

    return daoRoot(credentials, objectType.getGroup()) + '/' + objectKey + '/' + filename;
  }

  protected Iterable<ListBlobItem> listBlobs(
      CloudBlobContainer container, String prefix, boolean useFlatBlobListing, boolean isFolder) {
    return container.listBlobs(prefix, useFlatBlobListing);
  }

  public CloudBlockBlob getBlockBlobReference(CloudBlobContainer container, final String blobName)
      throws URISyntaxException, StorageException {
    return container.getBlockBlobReference(blobName);
  }

  protected String downloadText(CloudBlockBlob blob) throws StorageException, IOException {
    return blob.downloadText();
  }

  public Date getLastModified(BlobProperties properties) {
    return properties.getLastModified();
  }

  public void createIfNotExists(CloudBlobContainer container) throws StorageException {
    container.createIfNotExists();
  }

  public void deleteIfExists(CloudBlockBlob blob) throws StorageException {
    blob.deleteIfExists();
  }

  public void uploadFromByteArray(
      CloudBlockBlob blob, final byte[] bytes, final int offset, final int length)
      throws StorageException, IOException {
    blob.uploadFromByteArray(bytes, offset, length);
  }
}
