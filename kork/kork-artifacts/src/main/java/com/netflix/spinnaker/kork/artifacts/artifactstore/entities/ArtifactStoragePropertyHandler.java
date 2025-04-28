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

import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;

/**
 * An artifact storage property handler is a special type of handler specific of handling fields in
 * an object.
 *
 * <p>Below shows an example of how this handler is called: <code>
 *      @Data
 *      public class MyObject {
 *          private Map<String, Object> metadata;
 *          private Integer myInt;
 *      }
 *  </code> Assume we have this MyObject class, and when visiting during serialization, we can
 * handle fields by implementing a property handler like below <code>
 *      public class MyHandler implements ArtifactStoragePropertyHandler {
 *          public boolean canHandleProperty(BeanProperty property, Object v) {
 *              return "metadata".equals(property.getName()) && property.getType().isMapLikeType();
 *          }
 *
 *          public <T> T handleProperty(ArtifactStore store, BeanProperty property, T v, ObjectMapper objectMapper) {
 *              String ref = Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(v));
 *              Artifact toStore = Artifact.builder().name("metadata-entity").type("embedded/customtype/base64").reference(ref).build();
 *              Artifact stored = store.store(toStore, ArtifactTypeDecorator.toRemote(artifact));
 *              return (T) EntityHelper.toMap(stored);
 *          }
 *      }
 *  </code> This example will store the metadata field that is a map on any object.
 */
public interface ArtifactStoragePropertyHandler extends ArtifactHandler {
  /** Called to check if this handler can handle a bean property. */
  boolean canHandleProperty(BeanProperty property, Object v);

  <T> T handleProperty(ArtifactStore store, BeanProperty property, T v, ObjectMapper objectMapper);
}
