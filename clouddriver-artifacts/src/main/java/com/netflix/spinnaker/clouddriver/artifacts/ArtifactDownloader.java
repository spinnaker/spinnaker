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

package com.netflix.spinnaker.clouddriver.artifacts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class ArtifactDownloader {
  final private ArtifactCredentialsRepository artifactCredentialsRepository;

  @Autowired
  public ArtifactDownloader(ArtifactCredentialsRepository artifactCredentialsRepository, ObjectMapper objectMapper) {
    this.artifactCredentialsRepository = artifactCredentialsRepository;
  }

  public InputStream download(Artifact artifact) throws IOException {
    String artifactAccount = artifact.getArtifactAccount();
    if (StringUtils.isEmpty(artifactAccount)) {
      throw new IllegalArgumentException("An artifact account must be supplied to download this artifact: " + artifactAccount);
    }

    ArtifactCredentials credentials = artifactCredentialsRepository.getAllCredentials()
        .stream()
        .filter(c -> c.getName().equals(artifactAccount))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No credentials with name '" + artifactAccount + "' could be found."));

    if (!credentials.handlesType(artifact.getType())) {
      throw new IllegalArgumentException("Artifact credentials '" + artifactAccount + "' cannot handle artifacts of type '" + artifact.getType() + "'");
    }

    return credentials.download(artifact);
  }

}
