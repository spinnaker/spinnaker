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

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.std.MapDeserializer;
import com.fasterxml.jackson.databind.type.MapType;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty("artifact-store.entities.expand")
public class DeserializerHookRegistry extends BeanDeserializerModifier {
  private final ArtifactStore storage;
  private final ArtifactHandlerLists handlers;

  @Autowired
  public DeserializerHookRegistry(
      ArtifactStore storage,
      @Qualifier("deserializerEntityHandlers") ArtifactHandlerLists handlers) {
    this.storage = storage;
    this.handlers = handlers;
  }

  @Override
  public JsonDeserializer<?> modifyMapDeserializer(
      DeserializationConfig config,
      MapType type,
      BeanDescription beanDesc,
      JsonDeserializer<?> deserializer) {
    if (deserializer instanceof MapDeserializer) {
      // In the event that visited was NOT set, we will wrap the deserializer to modify the AST.
      return new MapDeserializerHook(
          this.storage, this.handlers.getMapHandlers(), (MapDeserializer) deserializer);
    }

    return deserializer;
  }
}
