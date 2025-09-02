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

import com.netflix.spinnaker.kork.artifacts.artifactstore.filters.ApplicationStorageFilter;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

/** ArtifactStore allows for different types of artifact storage to be used during runtime */
@Log4j2
public class ArtifactStore implements ArtifactStoreGetter, ArtifactStoreStorer {
  @Getter private static volatile ArtifactStore instance = null;

  private final ArtifactStoreGetter artifactStoreGetter;

  private final ArtifactStoreStorer artifactStoreStorer;

  private final Map<String, List<ApplicationStorageFilter>> exclude;

  public ArtifactStore(
      ArtifactStoreGetter artifactStoreGetter,
      ArtifactStoreStorer artifactStoreStorer,
      Map<String, List<ApplicationStorageFilter>> exclude) {
    this.artifactStoreGetter = artifactStoreGetter;
    this.artifactStoreStorer = artifactStoreStorer;
    this.exclude = exclude;
  }

  public boolean shouldExclude(String type, String application) {
    return application == null
        || this.exclude.containsKey(type)
            && this.exclude.get(type).stream().anyMatch((filter) -> filter.filter(application));
  }

  /**
   * Store an artifact in the artifact store.
   *
   * <p>This method checks if the artifact should be excluded based on the current application
   * context and the configured exclusion filters. If the artifact should be excluded, it will be
   * returned unchanged. Otherwise, it will be stored using the configured artifact store
   * implementation.
   *
   * <p>If the application context is null (i.e., no authenticated application is available), the
   * original artifact will be returned without storing it, and a warning will be logged.
   *
   * @param artifact The artifact to store
   * @param decorators Optional decorators to apply to the artifact before storing
   * @return The stored artifact, or the original artifact if storage was skipped due to filtering
   *     or if no application context is available
   */
  public Artifact store(Artifact artifact, ArtifactDecorator... decorators) {
    String application = AuthenticatedRequest.getSpinnakerApplication().orElse(null);
    if (application == null) {
      log.warn("failed to retrieve application from request artifact={}", artifact.getName());
      return artifact;
    }

    String type = artifact.getType();
    if (this.exclude.containsKey(type)
        && this.exclude.get(type).stream().anyMatch((filter) -> filter.filter(application))) {
      log.debug(
          "excluding artifact type for application type={} application={}", type, application);
      return artifact;
    }
    return artifactStoreStorer.store(artifact, decorators);
  }

  /**
   * get is used to return an artifact with some id, while also decorating that artifact with any
   * necessary fields needed that should then be returned by the artifact store.
   */
  public Artifact get(ArtifactReferenceURI uri, ArtifactDecorator... decorators) {
    return artifactStoreGetter.get(uri, decorators);
  }

  public static void setInstance(ArtifactStore storage) {
    synchronized (ArtifactStore.class) {
      if (instance == null) {
        instance = storage;
        return;
      }

      log.warn("Multiple attempts in setting the singleton artifact store");
    }
  }
}
