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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.CollectionSerializer;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * CollectionSerializerHook will hook into the collection serializers to match a collection element
 * to a handler.
 *
 * <p>This class is similar to the MapSerializerHook in that it will not handle any serialization,
 * but only modify or adapt elements that are matched to a specific handler.
 */
public class CollectionSerializerHook extends CollectionSerializer {
  private final ArtifactStore storage;
  private final List<ArtifactHandler> handlers;
  private final CollectionSerializer defaultSerializer;

  public CollectionSerializerHook(
      ArtifactStore storage, List<ArtifactHandler> handlers, CollectionSerializer serializer) {
    this(storage, handlers, serializer, null, null, null, null);
  }

  @Override
  public void serializeContents(Collection<?> value, JsonGenerator g, SerializerProvider provider)
      throws IOException {
    final Collection<?> tempValue = value;
    ObjectMapper mapper = (ObjectMapper) g.getCodec();
    if (this._property != null) {
      ArtifactStoragePropertyHandler handler =
          this.handlers.stream()
              .filter(h -> h instanceof ArtifactStoragePropertyHandler)
              .map(h -> (ArtifactStoragePropertyHandler) h)
              .filter(h -> h.canHandleProperty(this._property, tempValue))
              .findFirst()
              .orElse(null);
      if (handler != null) {
        this.defaultSerializer.serializeContents(
            handler.handleProperty(this.storage, this._property, value, mapper), g, provider);
        return;
      }
    }

    ArtifactStorageHandler handler =
        this.handlers.stream()
            .filter(h -> h instanceof ArtifactStorageHandler)
            .map(h -> (ArtifactStorageHandler) h)
            .filter(h -> h.canHandle(tempValue))
            .findFirst()
            .orElse(null);
    if (handler != null) {
      value = handler.handle(this.storage, value, mapper);
    }

    this.defaultSerializer.serializeContents(value, g, provider);
  }

  public CollectionSerializerHook(
      ArtifactStore storage,
      List<ArtifactHandler> handlers,
      CollectionSerializer serializer,
      BeanProperty property,
      TypeSerializer vts,
      JsonSerializer<?> elementSerializer,
      Boolean unwrapSingle) {
    super(serializer, property, vts, elementSerializer, unwrapSingle);
    this.storage = storage;
    this.handlers = handlers;
    this.defaultSerializer = serializer;
  }

  public CollectionSerializer withResolved(
      BeanProperty property,
      TypeSerializer vts,
      JsonSerializer<?> elementSerializer,
      Boolean unwrapSingle) {
    return new CollectionSerializerHook(
        this.storage,
        this.handlers,
        this.defaultSerializer.withResolved(property, vts, elementSerializer, unwrapSingle),
        property,
        vts,
        elementSerializer,
        unwrapSingle);
  }
}
