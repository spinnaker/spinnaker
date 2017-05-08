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
 */

package com.netflix.spinnaker.halyard.core.registry.v1;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.StorageScopes;
import com.google.api.services.storage.model.StorageObject;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemBuilder;
import com.netflix.spinnaker.halyard.core.provider.v1.google.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;

@Slf4j
public class WriteableProfileRegistry {
  private Storage storage;
  private WriteableProfileRegistryProperties properties;

  @Autowired
  String spinconfigBucket;

  WriteableProfileRegistry(WriteableProfileRegistryProperties properties) {
    HttpTransport httpTransport = GoogleCredentials.buildHttpTransport();
    JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
    GoogleCredential credential;
    try {
      credential = loadCredential(httpTransport, jsonFactory, properties.getJsonPath());
    } catch (IOException e) {
      throw new RuntimeException("Failed to load json credential", e);
    }

    this.storage = new Storage.Builder(httpTransport, jsonFactory, GoogleCredentials.setHttpTimeout(credential))
        .setApplicationName("halyard")
        .build();
    this.properties = properties;
  }

  private GoogleCredential loadCredential(HttpTransport transport, JsonFactory factory, String jsonPath) throws IOException {
    GoogleCredential credential;
    if (!jsonPath.isEmpty()) {
      FileInputStream stream = new FileInputStream(jsonPath);
      credential = GoogleCredential.fromStream(stream, transport, factory)
          .createScoped(Collections.singleton(StorageScopes.DEVSTORAGE_FULL_CONTROL));
      log.info("Loaded credentials from " + jsonPath);
    } else {
      log.info("Using default application credentials.");
      credential = GoogleCredential.getApplicationDefault();
    }
    return credential;
  }

  public void writeBom(String version, String contents) {
    String name = ProfileRegistry.bomPath(version);
    writeTextObject(name, contents);
  }

  public void writeArtifactConfig(BillOfMaterials bom, String artifactName, String profileName, String contents) {
    String version = bom.getArtifactVersion(artifactName);
    String name = ProfileRegistry.profilePath(artifactName, version, profileName);
    writeTextObject(name, contents);
  }

  public void writeVersions(String versions) {
    writeTextObject("versions.yml", versions);
  }

  private void writeTextObject(String name, String contents) {
    try {
      byte[] bytes = contents.getBytes();
      StorageObject object = new StorageObject()
          .setBucket(spinconfigBucket)
          .setName(name);

      ByteArrayContent content = new ByteArrayContent("application/text", bytes);
      storage.objects().insert(spinconfigBucket, object, content).execute();
    } catch (IOException e) {
      log.error("Failed to write new object " + name, e);
      throw new HalException(new ProblemBuilder(Severity.FATAL, "Failed to write to " + name + ": " + e.getMessage()).build());
    }
  }
}
