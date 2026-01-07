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
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.CollectionSerializer;
import com.fasterxml.jackson.databind.ser.std.MapSerializer;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.MapType;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * This is where modifiers get registered for handling various "entities". These modifiers will act
 * as adapters for some object where it may do some transformation or storage like manifests keys in
 * a map
 *
 * <p>It is important to note that these modifiers/serializers are cached. Meaning the object
 * mappers will call this once per class. So if any filtering is done at this layer, it will not
 * appropriately call the modifier if the filtering is dynamic.
 */
@Component
@ConditionalOnProperty("artifact-store.entities.enabled")
public class SerializerHookRegistry extends BeanSerializerModifier {
  private final ArtifactStore storage;
  private final ArtifactHandlerLists handlers;
  @Getter private static SerializerHookRegistry INSTANCE;
  /** Used to not rebuild the same object mapper every time we need to remove the hook. */
  @Autowired
  public SerializerHookRegistry(
      ArtifactStore storage, @Qualifier("serializerEntityHandlers") ArtifactHandlerLists handlers) {
    this.storage = storage;
    this.handlers = handlers;
    SerializerHookRegistry.INSTANCE = this;
  }

  @Override
  public JsonSerializer<?> modifyMapSerializer(
      SerializationConfig config,
      MapType valueType,
      BeanDescription beanDesc,
      JsonSerializer<?> serializer) {
    if (serializer instanceof MapSerializer) {
      return new MapSerializerHook(
          this.storage, this.handlers.getMapHandlers(), (MapSerializer) serializer);
    }
    return serializer;
  }

  @Override
  public JsonSerializer<?> modifyCollectionSerializer(
      SerializationConfig config,
      CollectionType valueType,
      BeanDescription beanDesc,
      JsonSerializer<?> serializer) {
    if (serializer instanceof CollectionSerializer) {
      return new CollectionSerializerHook(
          this.storage, this.handlers.getCollectionHandlers(), (CollectionSerializer) serializer);
    }
    return serializer;
  }
}
