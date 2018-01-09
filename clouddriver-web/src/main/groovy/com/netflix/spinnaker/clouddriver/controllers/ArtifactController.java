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
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/artifacts")
public class ArtifactController {
  private ArtifactCredentialsRepository artifactCredentialsRepository;
  private ArtifactDownloader artifactDownloader;

  @Autowired
  public ArtifactController(Optional<ArtifactCredentialsRepository> artifactCredentialsRepository,
      Optional<ArtifactDownloader> artifactDownloader) {
    this.artifactCredentialsRepository = artifactCredentialsRepository.orElse(null);
    this.artifactDownloader = artifactDownloader.orElse(null);
  }

  @RequestMapping(method = RequestMethod.GET, value = "/credentials")
  List<ArtifactCredentials> list() {
    if (artifactCredentialsRepository == null) {
      return new ArrayList<>();
    } else {
      return artifactCredentialsRepository.getAllCredentials();
    }
  }

  @RequestMapping(method = RequestMethod.GET, value = "/fetch")
  String fetch(@RequestParam("artifactAccount") String artifactAccount,
      @RequestParam("type") String type,
      @RequestParam("reference") String reference) {
    if (artifactDownloader == null) {
      throw new IllegalStateException("Artifacts have not been enabled. Enable them using 'artifacts.enabled' in clouddriver");
    }

    Artifact artifact = Artifact.builder()
        .type(type)
        .artifactAccount(artifactAccount)
        .reference(reference)
        .build();

    try {
      return IOUtils.toString(artifactDownloader.download(artifact));
    } catch (IOException e) {
      throw new RuntimeException("Failure fetching '" + artifact + "': " + e.getMessage(), e);
    }
  }
}
