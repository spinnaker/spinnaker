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

package com.netflix.spinnaker.clouddriver.appengine.artifacts;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.StorageScopes;
import com.google.api.services.storage.model.Objects;
import com.google.api.services.storage.model.StorageObject;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GcsStorageService {
  private static final Logger log = LoggerFactory.getLogger(GcsStorageService.class);

  public static interface VisitorOperation {
    void visit(StorageObject storageObj) throws IOException;
  };

  public static class Factory {
    private String applicationName_;
    private HttpTransport transport_;
    private JsonFactory jsonFactory_;

    public Factory(String applicationName) throws IOException, GeneralSecurityException {
      applicationName_ = applicationName;
      transport_ = GoogleNetHttpTransport.newTrustedTransport();
      jsonFactory_ = JacksonFactory.getDefaultInstance();
    }

    public Factory(String applicationName, HttpTransport transport, JsonFactory jsonFactory) {
      applicationName_ = applicationName;
      transport_ = transport;
      jsonFactory_ = jsonFactory;
    }

    public GcsStorageService newForCredentials(String credentialsPath) throws IOException {
      GoogleCredentials credentials = loadCredentials(credentialsPath);
      HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);
      Storage storage =
          new Storage.Builder(transport_, jsonFactory_, requestInitializer)
              .setApplicationName(applicationName_)
              .build();

      return new GcsStorageService(storage);
    }

    private GoogleCredentials loadCredentials(String credentialsPath) throws IOException {
      GoogleCredentials credentials;
      if (credentialsPath != null && !credentialsPath.isEmpty()) {
        FileInputStream stream = new FileInputStream(credentialsPath);
        credentials =
            GoogleCredentials.fromStream(stream)
                .createScoped(Collections.singleton(StorageScopes.DEVSTORAGE_READ_ONLY));
        log.info("Loaded credentials from " + credentialsPath);
      } else {
        log.info(
            "spinnaker.gcs.enabled without spinnaker.gcs.jsonPath. "
                + "Using default application credentials. Using default credentials.");
        credentials = GoogleCredentials.getApplicationDefault();
      }
      return credentials;
    }
  };

  private Storage storage_;

  public GcsStorageService(Storage storage) {
    storage_ = storage;
  }

  public InputStream openObjectStream(String bucketName, String path, Long generation)
      throws IOException {
    Storage.Objects.Get get = storage_.objects().get(bucketName, path);
    if (generation != null) {
      get.setGeneration(generation);
    }
    return get.executeMediaAsInputStream();
  }

  public void visitObjects(String bucketName, String pathPrefix, VisitorOperation op)
      throws IOException {
    Storage.Objects.List listMethod = storage_.objects().list(bucketName);
    listMethod.setPrefix(pathPrefix);
    Objects objects;
    ExecutorService executor =
        Executors.newFixedThreadPool(
            8,
            new ThreadFactoryBuilder()
                .setNameFormat(GcsStorageService.class.getSimpleName() + "-%d")
                .build());

    do {
      objects = listMethod.execute();
      List<StorageObject> items = objects.getItems();
      if (items != null) {
        for (StorageObject obj : items) {
          executor.submit(
              () -> {
                try {
                  op.visit(obj);
                } catch (IOException ioex) {
                  throw new IllegalStateException(ioex);
                }
              });
        }
      }
      listMethod.setPageToken(objects.getNextPageToken());
    } while (objects.getNextPageToken() != null);
    executor.shutdown();

    try {
      if (!executor.awaitTermination(10, TimeUnit.MINUTES)) {
        throw new IllegalStateException("Timed out waiting to process StorageObjects.");
      }
    } catch (InterruptedException intex) {
      throw new IllegalStateException(intex);
    }
  }

  public void visitObjects(String bucketName, VisitorOperation op) throws IOException {
    visitObjects(bucketName, "", op);
  }

  public void downloadStorageObjectRelative(
      StorageObject obj, String ignorePrefix, String baseDirectory) throws IOException {
    String objPath = obj.getName();
    if (!ignorePrefix.isEmpty()) {
      ignorePrefix += File.separator;
      if (!objPath.startsWith(ignorePrefix)) {
        throw new IllegalArgumentException(objPath + " does not start with '" + ignorePrefix + "'");
      }
      objPath = objPath.substring(ignorePrefix.length());
    }

    // Ignore folder placeholder objects created by Google Console UI
    if (objPath.endsWith("/")) {
      return;
    }
    File target = new File(baseDirectory, objPath);
    try (InputStream stream =
        openObjectStream(obj.getBucket(), obj.getName(), obj.getGeneration())) {
      ArtifactUtils.writeStreamToFile(stream, target);
    }
    target.setLastModified(obj.getUpdated().getValue());
  }

  public void downloadStorageObject(StorageObject obj, String baseDirectory) throws IOException {
    downloadStorageObjectRelative(obj, "", baseDirectory);
  }
}
