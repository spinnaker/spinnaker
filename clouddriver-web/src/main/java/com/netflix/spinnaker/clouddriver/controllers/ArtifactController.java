/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.controllers;

import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository;
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactDownloader;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreURIBuilder;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.exceptions.MissingCredentialsException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Slf4j
@RestController
@RequestMapping("/artifacts")
public class ArtifactController {
  private ArtifactCredentialsRepository artifactCredentialsRepository;
  private ArtifactDownloader artifactDownloader;
  private final ArtifactStore storage;
  private final ArtifactStoreURIBuilder artifactStoreURIBuilder;

  @Autowired
  public ArtifactController(
      Optional<ArtifactCredentialsRepository> artifactCredentialsRepository,
      Optional<ArtifactDownloader> artifactDownloader,
      Optional<ArtifactStore> storage,
      Optional<ArtifactStoreURIBuilder> artifactStoreURIBuilder) {
    this.artifactCredentialsRepository = artifactCredentialsRepository.orElse(null);
    this.artifactDownloader = artifactDownloader.orElse(null);
    this.storage = storage.orElse(null);
    this.artifactStoreURIBuilder = artifactStoreURIBuilder.orElse(null);
  }

  @RequestMapping(method = RequestMethod.GET, value = "/credentials")
  List<ArtifactCredentials> list() {
    if (artifactCredentialsRepository == null) {
      return Collections.emptyList();
    } else {
      return artifactCredentialsRepository.getAllCredentials();
    }
  }

  // PUT because we need to send a body, which GET does not allow for spring/retrofit
  @RequestMapping(method = RequestMethod.PUT, value = "/fetch")
  StreamingResponseBody fetch(@RequestBody Artifact artifact) {
    if (artifactDownloader == null) {
      throw new IllegalStateException(
          "Artifacts have not been enabled. Enable them using 'artifacts.enabled' in clouddriver");
    }

    return outputStream -> {
      try (InputStream artifactStream = artifactDownloader.download(artifact)) {
        IOUtils.copy(artifactStream, outputStream);
      }
    };
  }

  @RequestMapping(method = RequestMethod.GET, value = "/content-address/{application}/{hash}")
  Artifact.StoredView getStoredArtifact(
      @PathVariable(value = "application") String application,
      @PathVariable(value = "hash") String hash) {
    Artifact artifact = storage.get(artifactStoreURIBuilder.buildURIFromPaths(application, hash));
    Artifact.StoredView view = new Artifact.StoredView(artifact.getReference());
    return view;
  }

  @RequestMapping(method = RequestMethod.GET, value = "/account/{accountName}/names")
  List<String> getNames(
      @PathVariable("accountName") String accountName,
      @RequestParam(value = "type") String artifactType) {
    ArtifactCredentials credentials =
        artifactCredentialsRepository.getCredentialsForType(accountName, artifactType);
    return credentials.getArtifactNames();
  }

  @RequestMapping(method = RequestMethod.GET, value = "/account/{accountName}/versions")
  List<String> getVersions(
      @PathVariable("accountName") String accountName,
      @RequestParam(value = "type") String artifactType,
      @RequestParam(value = "artifactName") String artifactName) {
    ArtifactCredentials credentials =
        artifactCredentialsRepository.getCredentialsForType(accountName, artifactType);
    return credentials.getArtifactVersions(artifactName);
  }

  @ExceptionHandler(MissingCredentialsException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public void handleMissingCredentials() {}
}
