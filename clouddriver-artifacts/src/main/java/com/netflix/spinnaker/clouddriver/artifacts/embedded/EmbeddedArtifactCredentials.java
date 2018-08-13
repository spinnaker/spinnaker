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
import java.util.Arrays;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

@Slf4j
@Data
public class EmbeddedArtifactCredentials implements ArtifactCredentials {
  private final String name;
  private final List<String> types = Arrays.asList("embedded/base64");

  @JsonIgnore
  private final Base64.Decoder base64Decoder;

  public EmbeddedArtifactCredentials(EmbeddedArtifactAccount account) {
    name = account.getName();
    base64Decoder = Base64.getDecoder();
  }

  public InputStream download(Artifact artifact) throws IOException {
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
