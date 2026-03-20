/*
 * Copyright 2025 Apple Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.kork.artifacts.artifactstore.entities;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactReferenceURI;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
import com.netflix.spinnaker.kork.artifacts.artifactstore.exceptions.ArtifactStoreHandlerException;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

/** Handler to handle simple expansion of artifacts to the appropriate map */
public class ExpandToMapHandler implements ArtifactExpandHandler {
  @Override
  public boolean canHandle(Object v) {
    return EntityHelper.isArtifactLike(v)
        && ArtifactTypes.REMOTE_MAP_BASE64.getMimeType().equals(((Map) v).get("type"));
  }

  @Override
  public <T> T handle(ArtifactStore store, Object v, Class<T> clazz, ObjectMapper objectMapper) {
    Map m = (Map) v;
    String uri = (String) m.get("reference");
    Artifact artifact = store.get(ArtifactReferenceURI.parse(uri));
    String reference = artifact.getReference();
    byte[] b = Base64.getDecoder().decode(reference);
    try {
      return objectMapper.readValue(b, clazz);
    } catch (IOException e) {
      throw new ArtifactStoreHandlerException("Failed to handle expansion", e);
    }
  }
}
