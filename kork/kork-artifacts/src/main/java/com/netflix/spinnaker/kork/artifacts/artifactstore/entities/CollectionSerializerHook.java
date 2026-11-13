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

import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
import java.util.Collection;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * CollectionSerializerHook will hook into the collection serializers to match a collection element
 * to a handler.
 *
 * <p>This class is similar to the MapSerializerHook in that it will not handle any serialization,
 * but only modify or adapt elements that are matched to a specific handler.
 */
public class CollectionSerializerHook extends StdSerializer<Collection<?>> {
  private final ArtifactStore storage;
  private final List<ArtifactHandler> handlers;
  private final ValueSerializer<Object> defaultSerializer;
  private final BeanProperty property;

  public CollectionSerializerHook(
      ArtifactStore storage, List<ArtifactHandler> handlers, ValueSerializer<?> serializer) {
    this(storage, handlers, serializer, null);
  }

  @SuppressWarnings("unchecked")
  private CollectionSerializerHook(
      ArtifactStore storage,
      List<ArtifactHandler> handlers,
      ValueSerializer<?> serializer,
      BeanProperty property) {
    super(Collection.class);
    this.storage = storage;
    this.handlers = handlers != null ? handlers : List.of();
    this.defaultSerializer = (ValueSerializer<Object>) serializer;
    this.property = property;
  }

  @Override
  public void serialize(Collection<?> value, JsonGenerator g, SerializationContext context)
      throws JacksonException {
    defaultSerializer.serialize(visit(value, context), g, context);
  }

  @Override
  public void serializeWithType(
      Collection<?> value,
      JsonGenerator g,
      SerializationContext context,
      TypeSerializer typeSerializer)
      throws JacksonException {
    defaultSerializer.serializeWithType(visit(value, context), g, context, typeSerializer);
  }

  @Override
  public boolean isEmpty(SerializationContext context, Collection<?> value) {
    return defaultSerializer.isEmpty(context, value);
  }

  @Override
  public ValueSerializer<?> createContextual(SerializationContext context, BeanProperty property) {
    ValueSerializer<?> resolved = defaultSerializer.createContextual(context, property);
    return new CollectionSerializerHook(this.storage, this.handlers, resolved, property);
  }

  private Collection<?> visit(Collection<?> value, SerializationContext context) {
    if (this.property != null) {
      ArtifactStoragePropertyHandler handler =
          this.handlers.stream()
              .filter(h -> h instanceof ArtifactStoragePropertyHandler)
              .map(h -> (ArtifactStoragePropertyHandler) h)
              .filter(h -> h.canHandleProperty(this.property, value))
              .findFirst()
              .orElse(null);
      if (handler != null) {
        return (Collection<?>) handler.handleProperty(this.storage, this.property, value, context);
      }
    }

    ArtifactStorageHandler handler =
        this.handlers.stream()
            .filter(h -> h instanceof ArtifactStorageHandler)
            .map(h -> (ArtifactStorageHandler) h)
            .filter(h -> h.canHandle(value))
            .findFirst()
            .orElse(null);
    if (handler == null) {
      return value;
    }

    return (Collection<?>) handler.handle(this.storage, value, context);
  }
}
