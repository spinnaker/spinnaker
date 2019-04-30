/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.igor.gcb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.cloudbuild.v1.model.Build;
import com.google.api.services.cloudbuild.v1.model.BuiltImage;
import com.google.api.services.cloudbuild.v1.model.Results;
import com.netflix.spinnaker.igor.gcb.model.GoogleCloudBuildArtifact;
import com.netflix.spinnaker.igor.gcb.model.GoogleCloudStorageObject;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class GoogleCloudBuildArtifactFetcher {
  private final GoogleCloudBuildClient client;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public List<Artifact> getArtifacts(Build build) {
    List<Artifact> results = new ArrayList<>();

    results.addAll(getDockerArtifacts(build));
    results.addAll(getGoogleCloudStorageArtifacts(build));

    return results;
  }

  private List<Artifact> getDockerArtifacts(Build build) {
    Results results = build.getResults();
    if (results == null) {
      return Collections.emptyList();
    }

    List<BuiltImage> images = results.getImages();
    if (images == null) {
      return Collections.emptyList();
    }

    return images.stream().map(this::parseBuiltImage).collect(Collectors.toList());
  }

  private Artifact parseBuiltImage(BuiltImage image) {
    String[] parts = image.getName().split(":");
    return Artifact.builder()
        .name(parts[0])
        .version(image.getDigest())
        .reference(String.format("%s@%s", parts[0], image.getDigest()))
        .type("docker/image")
        .build();
  }

  private List<Artifact> getGoogleCloudStorageArtifacts(Build build) {
    GoogleCloudStorageObject manifest = getGoogleCloudStorageManifest(build);
    if (manifest == null) {
      return Collections.emptyList();
    }

    try {
      return readGoogleCloudStorageManifest(manifest).stream()
          .map(this::parseGoogleCloudBuildArtifact)
          .distinct()
          .collect(Collectors.toList());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Artifact parseGoogleCloudBuildArtifact(GoogleCloudBuildArtifact artifact) {
    String location = artifact.getLocation();
    GoogleCloudStorageObject object = GoogleCloudStorageObject.fromReference(location);
    return Artifact.builder()
        .name(object.getName())
        .version(object.getVersionString())
        .reference(location)
        .type("gcs/object")
        .build();
  }

  private GoogleCloudStorageObject getGoogleCloudStorageManifest(Build build) {
    Results results = build.getResults();
    if (results == null) {
      return null;
    }

    String artifactManifest = results.getArtifactManifest();
    if (artifactManifest == null) {
      return null;
    }

    return GoogleCloudStorageObject.fromReference(artifactManifest);
  }

  private List<GoogleCloudBuildArtifact> readGoogleCloudStorageManifest(
      GoogleCloudStorageObject manifest) throws IOException {
    List<GoogleCloudBuildArtifact> results = new ArrayList<>();
    InputStream is = client.fetchStorageObject(manifest.getBucket(), manifest.getObject());
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
      String line;
      while ((line = reader.readLine()) != null) {
        results.add(objectMapper.readValue(line, GoogleCloudBuildArtifact.class));
      }
    }
    return results;
  }
}
