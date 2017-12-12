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
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;

@Component
public class ArtifactDownloader {
  final private ArtifactCredentialsRepository artifactCredentialsRepository;

  final private ObjectMapper objectMapper;

  final Yaml yamlParser;

  @Autowired
  public ArtifactDownloader(ArtifactCredentialsRepository artifactCredentialsRepository, ObjectMapper objectMapper) {
    this.artifactCredentialsRepository = artifactCredentialsRepository;
    this.objectMapper = objectMapper;
    this.yamlParser = new Yaml();
  }

  public InputStream download(Artifact artifact) throws IOException {
    ArtifactCredentials credentials = artifactCredentialsRepository.getAllCredentials()
        .stream()
        // todo(lwander) remove isEmpty once the UI properly supports fetching artifact accounts
        .filter(c -> (StringUtils.isEmpty(artifact.getArtifactAccount()) || c.getName().equals(artifact.getArtifactAccount()))
            && c.handlesType(artifact.getType()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No credentials registered to handle " + artifact));

    return credentials.download(artifact);
  }

  public <T> T downloadAsYaml(Artifact artifact, Class<T> clazz) throws IOException {
    InputStream is = download(artifact);
    Object parsed = yamlParser.load(is);
    return objectMapper.convertValue(parsed, clazz);
  }
}
