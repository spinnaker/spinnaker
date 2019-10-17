/*
 * Copyright 2019 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.kork.secrets.engines;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.storage.Storage;
import com.google.auth.oauth2.GoogleCredentials;
import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import com.netflix.spinnaker.kork.secrets.InvalidSecretFormatException;
import com.netflix.spinnaker.kork.secrets.SecretException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class GcsSecretEngine extends AbstractStorageSecretEngine {
  private static final String IDENTIFIER = "gcs";
  private static final String APPLICATION_NAME = "Spinnaker";

  private final AtomicReference<Storage> googleStorage = new AtomicReference();

  public String identifier() {
    return IDENTIFIER;
  }

  @Override
  public void validate(EncryptedSecret encryptedSecret) throws InvalidSecretFormatException {
    Set<String> paramNames = encryptedSecret.getParams().keySet();
    if (!paramNames.contains(STORAGE_BUCKET)) {
      throw new InvalidSecretFormatException(
          "Storage bucket parameter is missing (" + STORAGE_BUCKET + "=...)");
    }
    if (!paramNames.contains(STORAGE_FILE_URI)) {
      throw new InvalidSecretFormatException(
          "Storage file parameter is missing (" + STORAGE_FILE_URI + "=...)");
    }
  }

  @Override
  protected InputStream downloadRemoteFile(EncryptedSecret encryptedSecret) {

    String bucket = encryptedSecret.getParams().get(STORAGE_BUCKET);
    String objName = encryptedSecret.getParams().get(STORAGE_FILE_URI);

    log.info("Getting contents of object {} from bucket {}", objName, bucket);

    try {
      Storage storage = getStorage();

      return storage.objects().get(bucket, objName).executeMediaAsInputStream();
    } catch (IOException e) {
      throw new SecretException(
          String.format(
              "Error reading contents of GCS. Bucket: %s, Object: %s.\nError: %s",
              bucket, objName, e.toString()));
    }
  }

  private Storage getStorage() throws IOException {
    Storage storage = googleStorage.get();

    if (storage == null) {
      HttpTransport httpTransport = GoogleUtils.buildHttpTransport();
      JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
      GoogleCredentials credentials = GoogleUtils.buildGoogleCredentials();
      HttpRequestInitializer requestInitializer =
          GoogleUtils.setTimeoutsAndRetryBehavior(credentials);

      storage =
          new Storage.Builder(httpTransport, jsonFactory, requestInitializer)
              .setApplicationName(APPLICATION_NAME)
              .build();

      googleStorage.compareAndSet(null, storage);
    }

    return storage;
  }
}
