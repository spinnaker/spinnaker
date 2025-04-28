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
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;

/**
 * ArtifactStorageHandler is used to hook into the serializers to handle a particular class, e.g. a
 * Map, List, etc. This allows us to easily extend the functionality by providing a handler to hook
 * into rather than writing a custom serializer for each use case.
 *
 * <p>It is very important that ANY handling MUST return the same type as the original parameter, v.
 * The reasoning for this is there may be custom serializers that handle some other serialization,
 * and we want to maintain that behavior.
 */
public interface ArtifactStorageHandler extends ArtifactHandler {
  /** Called to see if v is a type we can handle based on the matching criteria */
  boolean canHandle(Object v);

  <V> V handle(ArtifactStore store, V v, ObjectMapper objectMapper);
}
