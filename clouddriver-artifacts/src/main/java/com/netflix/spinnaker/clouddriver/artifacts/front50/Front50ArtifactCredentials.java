/*
 * Copyright 2019 Google, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.artifacts.front50;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class Front50ArtifactCredentials implements ArtifactCredentials {
  public static final String ACCOUNT_NAME = "front50ArtifactCredentials";
  private static final String URL_PREFIX = "spinnaker://";

  @Getter
  private final String name = ACCOUNT_NAME;
  @Getter
  private final List<String> types = Collections.singletonList("front50/pipelineTemplate");

  @JsonIgnore
  private final Front50Service front50Service;
  @JsonIgnore
  private final ObjectMapper objectMapper;

  Front50ArtifactCredentials(ObjectMapper objectMapper,
                             Front50Service front50Service) {
    this.objectMapper = objectMapper;
    this.front50Service = front50Service;
  }

  @Override
  public InputStream download(Artifact artifact) throws IOException {
    String reference = artifact.getReference();
    if (StringUtils.isEmpty(reference) || !reference.startsWith(URL_PREFIX)) {
      throw new IllegalArgumentException(String.format("'front50/pipelineTemplate' artifacts must be specified with a " +
        "'reference' starting with %s, got: %s'", URL_PREFIX, artifact));
    }

    Map pipelineTemplate;
    reference = reference.substring(URL_PREFIX.length());
    if (reference.contains("@sha256:")) {
      SplitResult result = splitReferenceOnToken(reference, "@sha256:");
      pipelineTemplate = front50Service.getV2PipelineTemplate(result.pipelineTemplateId, "", result.version);
    } else if (reference.contains(":")) {
      SplitResult result = splitReferenceOnToken(reference, ":");
      pipelineTemplate = front50Service.getV2PipelineTemplate(result.pipelineTemplateId, result.version, "");
    } else {
      pipelineTemplate = front50Service.getV2PipelineTemplate(reference, "", "");
    }

    return new ByteArrayInputStream(objectMapper.writeValueAsBytes(pipelineTemplate));
  }

  @Override
  public List<String> getArtifactNames() {
    return front50Service.listV2PipelineTemplates(Collections.singletonList("global"))
      .stream()
      .map(t -> (String) t.get("id"))
      .distinct()
      .collect(Collectors.toList());
  }

  private SplitResult splitReferenceOnToken(String reference, String token) {
    String[] refParts = reference.split(token);
    if (refParts.length != 2) {
      throw new IllegalArgumentException("Malformed Front50 artifact reference: " + reference);
    }
    return new SplitResult(refParts[0], refParts[1]);
  }

  @Data
  @AllArgsConstructor
  private static class SplitResult {
    private String pipelineTemplateId;
    private String version;
  }

  // TODO(jacobkiefer): Implement getArtifactVersions()
}
