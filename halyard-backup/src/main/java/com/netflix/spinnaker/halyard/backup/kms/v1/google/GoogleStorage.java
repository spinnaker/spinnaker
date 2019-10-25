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
 *
 *
 */

package com.netflix.spinnaker.halyard.backup.kms.v1.google;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.StorageScopes;
import com.google.api.services.storage.model.Bucket;
import com.google.api.services.storage.model.StorageObject;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import java.io.FileInputStream;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
class GoogleStorage {
  private final Storage storage;
  private final String projectId;
  private final String locationId;
  private final String bucketId;
  private final String rootFolder;

  GoogleStorage(GoogleSecureStorageProperties properties) {
    storage = buildCredentials(properties);
    projectId = properties.getProject();
    locationId = properties.getBucketLocation();
    bucketId = properties.getBucket();
    rootFolder =
        StringUtils.isEmpty(properties.getRootFolder())
            ? "halyard-backup"
            : properties.getRootFolder();

    ensureBucketExistsAndVersioned(storage, projectId, locationId, bucketId);
  }

  void writeBytes(String name, byte[] contents) {
    name = String.join("/", rootFolder, name);
    try {
      StorageObject object = new StorageObject().setBucket(bucketId).setName(name);

      ByteArrayContent content = new ByteArrayContent("application/text", contents);
      storage.objects().insert(bucketId, object, content).execute();
    } catch (IOException e) {
      throw new HalException(
          Problem.Severity.FATAL, "Failed to write to " + name + ": " + e.getMessage(), e);
    }
  }

  private Storage buildCredentials(GoogleSecureStorageProperties properties) {
    HttpTransport transport = new NetHttpTransport();
    JsonFactory jsonFactory = new JacksonFactory();
    GoogleCredentials credentials;
    try {
      credentials = loadStorageCredential(properties.getJsonPath());
    } catch (IOException e) {
      throw new RuntimeException("Unable to load KMS credentials: " + e.getMessage(), e);
    }

    return new Storage.Builder(transport, jsonFactory, new HttpCredentialsAdapter(credentials))
        .setApplicationName("halyard")
        .build();
  }

  private static void ensureBucketExistsAndVersioned(
      Storage storage, String projectId, String locationId, String bucketId) {
    Bucket bucket;
    try {
      bucket = storage.buckets().get(bucketId).execute();
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 404) {
        bucket = createBucket(storage, projectId, locationId, bucketId);
      } else {
        throw new RuntimeException("Can't retrieve bucket", e);
      }
    } catch (IOException e) {
      throw new RuntimeException("Can't retrieve bucket", e);
    }

    if (!bucket.getVersioning().getEnabled()) {
      throw new RuntimeException("Bucket " + bucketId + " is not versioned. Aborting.");
    }
  }

  private static Bucket createBucket(
      Storage storage, String projectId, String locationId, String bucketId) {
    try {
      Bucket bucket =
          new Bucket()
              .setLocation(locationId)
              .setName(bucketId)
              .setVersioning(new Bucket.Versioning().setEnabled(true));

      if (!StringUtils.isEmpty(locationId)) {
        bucket.setLocation(locationId);
      }

      return storage.buckets().insert(projectId, bucket).execute();
    } catch (IOException e) {
      throw new RuntimeException("Unable to create bucket", e);
    }
  }

  private static GoogleCredentials loadStorageCredential(String jsonPath) throws IOException {
    GoogleCredentials credentials;
    if (!jsonPath.isEmpty()) {
      FileInputStream stream = new FileInputStream(jsonPath);
      credentials = GoogleCredentials.fromStream(stream);
      log.info("Loaded storage credentials from " + jsonPath);
    } else {
      log.info("Using storage default application credentials.");
      credentials = GoogleCredentials.getApplicationDefault();
    }

    if (credentials.createScopedRequired()) {
      credentials = credentials.createScoped(StorageScopes.all());
    }

    return credentials;
  }
}
