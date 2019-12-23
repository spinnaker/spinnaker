/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.gcs.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Bucket;
import com.google.api.services.storage.model.Objects;
import com.google.api.services.storage.model.StorageObject;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.google.security.GoogleNamedAccountCredentials;
import com.netflix.kayenta.index.CanaryConfigIndex;
import com.netflix.kayenta.index.config.CanaryConfigIndexAction;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
public class GcsStorageService implements StorageService {

  @Autowired private ObjectMapper kayentaObjectMapper;

  @NotNull @Singular @Getter private List<String> accountNames;

  @Autowired private AccountCredentialsRepository accountCredentialsRepository;

  @Autowired private CanaryConfigIndex canaryConfigIndex;

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  /** Check to see if the bucket exists, creating it if it is not there. */
  public void ensureBucketExists(String accountName) {
    GoogleNamedAccountCredentials credentials =
        (GoogleNamedAccountCredentials)
            accountCredentialsRepository
                .getOne(accountName)
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "Unable to resolve account " + accountName + "."));
    Storage storage = credentials.getStorage();
    String projectName = credentials.getProject();
    String bucketName = credentials.getBucket();
    String bucketLocation = credentials.getBucketLocation();

    try {
      storage.buckets().get(bucketName).execute();
    } catch (HttpResponseException e) {
      if (e.getStatusCode() == 404) {
        log.warn("Bucket {} does not exist. Creating it in project {}.", bucketName, projectName);

        Bucket.Versioning versioning = new Bucket.Versioning().setEnabled(true);
        Bucket bucket = new Bucket().setName(bucketName).setVersioning(versioning);

        if (!StringUtils.isEmpty(bucketLocation)) {
          log.warn("Using location {} for bucket {}.", bucket, projectName);

          bucket.setLocation(bucketLocation);
        }

        try {
          storage.buckets().insert(projectName, bucket).execute();
        } catch (IOException e2) {
          log.error("Could not create bucket {} in project {}: {}", bucketName, projectName, e2);
          throw new IllegalArgumentException(e2);
        }
      } else if (e.getStatusCode() == 403) {
        log.error(
            "Account does not have permissions to get bucket metadata {}. Please see the FAQ for details: https://github.com/spinnaker/kayenta/blob/master/docs/faq.md#why-doesnt-my-google-account-have-access-to-get-bucket-metadata: {}",
            bucketName,
            e);
        throw new IllegalArgumentException(e);
      } else {
        log.error("Could not get bucket {}: {}", bucketName, e);
        throw new IllegalArgumentException(e);
      }
    } catch (IOException e) {
      log.error("Could not get bucket {}: {}", bucketName, e);
      throw new IllegalArgumentException(e);
    }
  }

  @Override
  public <T> T loadObject(String accountName, ObjectType objectType, String objectKey)
      throws IllegalArgumentException, NotFoundException {
    GoogleNamedAccountCredentials credentials =
        (GoogleNamedAccountCredentials)
            accountCredentialsRepository
                .getOne(accountName)
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "Unable to resolve account " + accountName + "."));
    Storage storage = credentials.getStorage();
    String bucketName = credentials.getBucket();
    StorageObject item;

    try {
      item = resolveSingularItem(objectType, objectKey, credentials, storage, bucketName);
    } catch (IllegalArgumentException e) {
      throw new NotFoundException(e.getMessage());
    }

    try {
      StorageObject storageObject = storage.objects().get(bucketName, item.getName()).execute();
      return deserialize(storage, storageObject, (TypeReference<T>) objectType.getTypeReference());
    } catch (IOException e) {
      if (e instanceof HttpResponseException) {
        HttpResponseException hre = (HttpResponseException) e;
        log.error(
            "Failed to load {} {}: {} {}",
            objectType.getGroup(),
            objectKey,
            hre.getStatusCode(),
            hre.getStatusMessage());
        if (hre.getStatusCode() == 404) {
          throw new NotFoundException("No file at path " + item.getName() + ".");
        }
      }
      throw new IllegalStateException(e);
    }
  }

  private StorageObject resolveSingularItem(
      ObjectType objectType,
      String objectKey,
      GoogleNamedAccountCredentials credentials,
      Storage storage,
      String bucketName) {
    String rootFolder = daoRoot(credentials, objectType.getGroup()) + "/" + objectKey;

    try {
      Storage.Objects.List objectsList = storage.objects().list(bucketName).setPrefix(rootFolder);
      Objects objects = objectsList.execute();
      List<StorageObject> items = objects.getItems();

      if (items != null && items.size() == 1) {
        return items.get(0);
      } else {
        throw new IllegalArgumentException(
            "Unable to resolve singular "
                + objectType
                + " at "
                + daoRoot(credentials, objectType.getGroup())
                + '/'
                + objectKey
                + ".");
      }
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "Could not fetch items from Google Cloud Storage: " + e.getMessage(), e);
    }
  }

  private <T> T deserialize(Storage storage, StorageObject object, TypeReference<T> typeReference)
      throws IOException {
    ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
    Storage.Objects.Get getter = storage.objects().get(object.getBucket(), object.getName());
    getter.executeMediaAndDownloadTo(output);
    String json = output.toString("UTF8");

    return kayentaObjectMapper.readValue(json, typeReference);
  }

  @Override
  public <T> void storeObject(
      String accountName,
      ObjectType objectType,
      String objectKey,
      T obj,
      String filename,
      boolean isAnUpdate) {
    GoogleNamedAccountCredentials credentials =
        (GoogleNamedAccountCredentials)
            accountCredentialsRepository
                .getOne(accountName)
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "Unable to resolve account " + accountName + "."));
    Storage storage = credentials.getStorage();
    String bucketName = credentials.getBucket();
    String path = keyToPath(credentials, objectType, objectKey, filename);

    ensureBucketExists(accountName);

    long updatedTimestamp = -1;
    String correlationId = null;
    String canaryConfigSummaryJson = null;
    StorageObject originalItem = null;

    if (objectType == ObjectType.CANARY_CONFIG) {
      updatedTimestamp = canaryConfigIndex.getRedisTime();

      CanaryConfig canaryConfig = (CanaryConfig) obj;

      checkForDuplicateCanaryConfig(canaryConfig, objectKey, credentials);

      if (isAnUpdate) {
        // Storing a canary config while not checking for naming collisions can only be a PUT (i.e.
        // an update to an existing config).
        originalItem = resolveSingularItem(objectType, objectKey, credentials, storage, bucketName);
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
      StorageObject object = new StorageObject().setBucket(bucketName).setName(path);
      ByteArrayContent content = new ByteArrayContent("application/json", bytes);

      storage.objects().insert(bucketName, object, content).execute();

      if (objectType == ObjectType.CANARY_CONFIG) {
        // This will be true if the canary config is renamed.
        if (originalItem != null && !originalItem.getName().equals(path)) {
          storage.objects().delete(bucketName, originalItem.getName()).execute();
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
    }
  }

  private void checkForDuplicateCanaryConfig(
      CanaryConfig canaryConfig, String canaryConfigId, GoogleNamedAccountCredentials credentials) {
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
    GoogleNamedAccountCredentials credentials =
        (GoogleNamedAccountCredentials)
            accountCredentialsRepository
                .getOne(accountName)
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "Unable to resolve account " + accountName + "."));
    Storage storage = credentials.getStorage();
    String bucketName = credentials.getBucket();
    StorageObject item =
        resolveSingularItem(objectType, objectKey, credentials, storage, bucketName);

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
      storage.objects().delete(bucketName, item.getName()).execute();

      if (correlationId != null) {
        canaryConfigIndex.finishPendingUpdate(
            credentials, CanaryConfigIndexAction.DELETE, correlationId);
      }
    } catch (HttpResponseException e) {
      if (e.getStatusCode() == 404) {
        if (correlationId != null) {
          canaryConfigIndex.finishPendingUpdate(
              credentials, CanaryConfigIndexAction.DELETE, correlationId);
        }

        return;
      }

      if (correlationId != null) {
        canaryConfigIndex.removeFailedPendingUpdate(
            credentials,
            updatedTimestamp + "",
            CanaryConfigIndexAction.DELETE,
            correlationId,
            canaryConfigSummaryJson);
      }

      throw new IllegalArgumentException(e);
    } catch (IOException ioex) {
      log.error("Failed to delete path {}: {}", item.getName(), ioex);

      if (correlationId != null) {
        canaryConfigIndex.removeFailedPendingUpdate(
            credentials,
            updatedTimestamp + "",
            CanaryConfigIndexAction.DELETE,
            correlationId,
            canaryConfigSummaryJson);
      }

      throw new IllegalArgumentException(ioex);
    }
  }

  @Override
  public List<Map<String, Object>> listObjectKeys(
      String accountName, ObjectType objectType, List<String> applications, boolean skipIndex) {
    GoogleNamedAccountCredentials credentials =
        (GoogleNamedAccountCredentials)
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
      Storage storage = credentials.getStorage();
      String bucketName = credentials.getBucket();
      String rootFolder = daoRoot(credentials, objectType.getGroup());

      ensureBucketExists(accountName);

      int skipToOffset = rootFolder.length() + 1; // + Trailing slash
      List<Map<String, Object>> result = new ArrayList<>();

      log.debug("Listing {}", objectType.getGroup());

      try {
        Storage.Objects.List objectsList = storage.objects().list(bucketName).setPrefix(rootFolder);
        Objects objects;

        do {
          objects = objectsList.execute();
          List<StorageObject> items = objects.getItems();

          if (items != null) {
            for (StorageObject item : items) {
              String itemName = item.getName();
              int indexOfLastSlash = itemName.lastIndexOf("/");
              Map<String, Object> objectMetadataMap = new HashMap<>();
              long updatedTimestamp = item.getUpdated().getValue();

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

          objectsList.setPageToken(objects.getNextPageToken());
        } while (objects.getNextPageToken() != null);
      } catch (IOException e) {
        log.error("Could not fetch items from Google Cloud Storage: {}", e);
      }

      return result;
    }
  }

  private String daoRoot(GoogleNamedAccountCredentials credentials, String daoTypeName) {
    return credentials.getRootFolder() + '/' + daoTypeName;
  }

  private String keyToPath(
      GoogleNamedAccountCredentials credentials,
      ObjectType objectType,
      String objectKey,
      String filename) {
    if (filename == null) {
      filename = objectType.getDefaultFilename();
    }

    return daoRoot(credentials, objectType.getGroup()) + '/' + objectKey + '/' + filename;
  }
}
