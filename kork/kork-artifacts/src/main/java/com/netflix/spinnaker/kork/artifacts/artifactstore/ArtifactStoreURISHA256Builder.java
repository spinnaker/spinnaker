/*
 * Copyright 2023 Apple Inc.
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
package com.netflix.spinnaker.kork.artifacts.artifactstore;

import com.google.common.hash.Hashing;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to abstract away the need for other classes to know the {@link * #uriPrefix} format.
 */
public class ArtifactStoreURISHA256Builder extends ArtifactStoreURIBuilder {
  @Override
  public ArtifactReferenceURI buildArtifactURI(String context, Artifact artifact) {
    String ref = artifact.getReference();
    if (ref == null) {
      throw new NullPointerException("Artifact reference cannot be null");
    }

    List<String> uriPaths =
        List.of(
            context,
            Hashing.sha256()
                .hashBytes(artifact.getReference().getBytes(StandardCharsets.UTF_8))
                .toString());
    return ArtifactReferenceURI.builder().uriPaths(uriPaths).build();
  }

  @Override
  public ArtifactReferenceURI buildURIFromPaths(String context, String... paths) {
    List<String> uriPaths = new ArrayList<>();
    uriPaths.add(context);
    uriPaths.addAll(List.of(paths));

    return ArtifactReferenceURI.builder().uriPaths(uriPaths).build();
  }
}
