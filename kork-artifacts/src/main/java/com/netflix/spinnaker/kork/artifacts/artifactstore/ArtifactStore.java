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

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;

/**
 * ArtifactStore is an interface that allows for different types of artifact storage to be used
 * during runtime
 */
public abstract class ArtifactStore {
  /** ensures the singleton has only been set once */
  private static final AtomicBoolean singletonSet = new AtomicBoolean(false);

  @Getter private static ArtifactStore instance = null;

  public abstract Artifact store(Artifact artifact);
  /**
   * get is used to return an artifact with some id, while also decorating that artifact with any
   * necessary fields needed which should be then be returned by the artifact store.
   */
  public abstract Artifact get(ArtifactReferenceURI uri, ArtifactDecorator... decorators);

  public static void setInstance(ArtifactStore storage) {
    if (!singletonSet.compareAndSet(false, true)) {
      throw new IllegalStateException("Multiple attempts at setting ArtifactStore's singleton");
    }

    ArtifactStore.instance = storage;
  }

  public boolean isArtifactURI(String value) {
    return value.startsWith(ArtifactStoreURIBuilder.uriScheme + "://");
  }
}
