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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.ArrayList;
import java.util.Collections;
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
      return Collections.emptyList();
    } else {
      return artifactCredentialsRepository.getAllCredentials();
    }
  }

  // PUT because we need to send a body, which GET does not allow for spring/retrofit
  @RequestMapping(method = RequestMethod.PUT, value = "/fetch")
  StreamingResponseBody fetch(@RequestBody Artifact artifact) {
    if (artifactDownloader == null) {
      throw new IllegalStateException("Artifacts have not been enabled. Enable them using 'artifacts.enabled' in clouddriver");
    }

    return outputStream -> IOUtils.copy(artifactDownloader.download(artifact), outputStream);
  }

  @RequestMapping(method = RequestMethod.GET, value = "/account/{accountName}/names")
  List<String> getNames(@PathVariable("accountName") String accountName,
                        @RequestParam(value = "type") String type) {
    ArtifactCredentials credentials = artifactCredentialsRepository.getCredentials(accountName, type);
    return credentials.getArtifactNames();
  }

  @RequestMapping(method = RequestMethod.GET, value = "/account/{accountName}/versions")
  List<String> getVersions(@PathVariable("accountName") String accountName,
                           @RequestParam(value = "type") String type,
                           @RequestParam(value = "artifactName") String artifactName) {
    ArtifactCredentials credentials = artifactCredentialsRepository.getCredentials(accountName, type);
    return credentials.getArtifactVersions(artifactName);
  }
}
