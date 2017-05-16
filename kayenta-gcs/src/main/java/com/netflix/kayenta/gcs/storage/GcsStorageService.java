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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Bucket;
import com.google.api.services.storage.model.StorageObject;
import com.netflix.kayenta.google.security.GoogleNamedAccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import javax.validation.constraints.NotNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Builder
@Slf4j
public class GcsStorageService implements StorageService {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @NotNull
  @Singular
  @Getter
  private List<String> accountNames;

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository;

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  @Override
  public <T> T loadObject(String accountName, ObjectType objectType, String objectKey) {
    GoogleNamedAccountCredentials credentials = (GoogleNamedAccountCredentials)accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));
    Storage storage = credentials.getStorage();
    String bucketName = credentials.getBucket();
    String path = keyToPath(credentials, objectType, objectKey);

    try {
      StorageObject storageObject = storage.objects().get(bucketName, path).execute();

      return deserialize(storage, storageObject, (Class<T>)objectType.getClazz());
    } catch (IOException e) {
      if (e instanceof HttpResponseException) {
        HttpResponseException hre = (HttpResponseException)e;
        log.error("Failed to load {} {}: {} {}", objectType.getGroup(), objectKey, hre.getStatusCode(), hre.getStatusMessage());
        if (hre.getStatusCode() == 404) {
          throw new IllegalArgumentException("No file at path " + path + ".");
        }
      }
      throw new IllegalStateException(e);
    }
  }

  private <T> T deserialize(Storage storage, StorageObject object, Class<T> clazz) throws IOException {
    ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
    Storage.Objects.Get getter = storage.objects().get(object.getBucket(), object.getName());
    getter.executeMediaAndDownloadTo(output);
    String json = output.toString("UTF8");

    return objectMapper.readValue(json, clazz);
  }

  @Override
  public <T> void storeObject(String accountName, ObjectType objectType, String objectKey, T obj) {
    GoogleNamedAccountCredentials credentials = (GoogleNamedAccountCredentials)accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));
    Storage storage = credentials.getStorage();
    String bucketName = credentials.getBucket();
    String path = keyToPath(credentials, objectType, objectKey);

    ensureBucketExists(accountName);

    try {
      byte[] bytes = objectMapper.writeValueAsBytes(obj);
      StorageObject object = new StorageObject().setBucket(bucketName).setName(path);
      ByteArrayContent content = new ByteArrayContent("application/json", bytes);

      storage.objects().insert(bucketName, object, content).execute();
    } catch (IOException e) {
      log.error("Update failed on path {}: {}", path, e);
      throw new IllegalStateException(e);
    }
  }

  /**
   * Check to see if the bucket exists, creating it if it is not there.
   */
  public void ensureBucketExists(String accountName) {
    GoogleNamedAccountCredentials credentials = (GoogleNamedAccountCredentials)accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));
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
          bucket.setLocation(bucketLocation);
        }

        try {
          storage.buckets().insert(projectName, bucket).execute();
        } catch (IOException e2) {
          log.error("Could not create bucket {} in project {}: {}", bucketName, projectName, e2);
          throw new IllegalStateException(e2);
        }
      } else {
        log.error("Could not get bucket {}: {}", bucketName, e);
        throw new IllegalStateException(e);
      }
    } catch (IOException e) {
      log.error("Could not get bucket {}: {}", bucketName, e);
      throw new IllegalStateException(e);
    }
  }

  private String daoRoot(GoogleNamedAccountCredentials credentials, String daoTypeName) {
    return credentials.getRootFolder() + '/' + daoTypeName;
  }

  private String keyToPath(GoogleNamedAccountCredentials credentials, ObjectType objectType, String objectKey) {
    return daoRoot(credentials, objectType.getGroup()) + '/' + objectKey + '/' + objectType.getDefaultMetadataFilename();
  }
}
