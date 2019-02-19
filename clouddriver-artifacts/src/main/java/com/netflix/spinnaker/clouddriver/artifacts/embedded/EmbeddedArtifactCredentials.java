/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.embedded;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

@Slf4j
public class EmbeddedArtifactCredentials implements ArtifactCredentials {
  @Getter
  private final String name;
  @Getter
  private final List<String> types = Collections.singletonList("embedded/base64");

  @JsonIgnore
  private final Base64.Decoder base64Decoder;

  EmbeddedArtifactCredentials(EmbeddedArtifactAccount account) {
    name = account.getName();
    base64Decoder = Base64.getDecoder();
  }

  public InputStream download(Artifact artifact) {
    String type = artifact.getType();
    if (type.equals("embedded/base64")) {
      return fromBase64(artifact);
    } else {
      throw new NotImplementedException("Embedded type '" + type + "' is not handled.");
    }
  }

  private InputStream fromBase64(Artifact artifact) {
    String reference = artifact.getReference();
    return new ByteArrayInputStream(base64Decoder.decode(reference));
  }

  @Override
  public boolean handlesType(String type) {
    return type.startsWith("embedded/");
  }
}
