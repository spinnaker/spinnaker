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
 * ArtifactExpandHandler is a handler that handles expansion of some object.
 *
 * <p>It is important to note that the same type MUST be returned as the original parameter v's
 * type. This ensures some type-safety around casting, and also ensures that anything we are
 * returning when we finish deserialization is of the proper type.
 */
public interface ArtifactExpandHandler extends ArtifactHandler {
  boolean canHandle(Object v);

  <T> T handle(ArtifactStore store, Object v, Class<T> clazz, ObjectMapper objectMapper);
}
