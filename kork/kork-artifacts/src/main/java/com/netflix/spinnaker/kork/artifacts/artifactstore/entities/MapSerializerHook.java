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
import com.fasterxml.jackson.databind.ser.std.MapSerializer;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MapSerializerHook will hook into the map serializers to match a map to a handler.
 *
 * <p>This class will not serialize directly, and all it will do is prep the map to be serialized.
 * The reasoning for this is there are a lot of ways to plug into jackson, and especially object
 * mappers. To retain the customized behavior, we can only affect the object itself before it needs
 * to be serialized.
 */
public class MapSerializerHook extends MapSerializer {
  /**
   * This is the serializer that we maintain and that was passed during the modifyMapSerializer. It
   * is important to note that MapSerializers are handled VERY differently than StdSerializer or
   * JsonSerializer. If we were to instead use the inappropriate serializer as a wrapper, the
   * serializing workflow will not bootstrap the map serializer to properly serialize the incoming
   * object. A good example of this is when a MapSerializer is passed in, the key serializer may be
   * null. The serializer workflow will pass in the key serializer by using the withResolved method.
   */
  private final MapSerializer defaultSerializer;

  private final ArtifactStore storage;
  /** Handlers to handle specific keys in the JSON. */
  private final List<ArtifactHandler> handlers;

  public MapSerializerHook(
      ArtifactStore storage, List<ArtifactHandler> handlers, MapSerializer defaultSerializer) {
    // These constructor values do not matter in that we will rely on the default serializer which
    // already have everything necessary to be set
    super(defaultSerializer, null, false);
    this.defaultSerializer = defaultSerializer;
    this.storage = storage;
    this.handlers = handlers != null ? handlers : List.of();
  }

  protected MapSerializerHook(
      ArtifactStore storage,
      List<ArtifactHandler> handlers,
      MapSerializer defaultSerializer,
      BeanProperty property,
      JsonSerializer<?> keySerializer,
      JsonSerializer<?> valueSerializer,
      Set<String> ignoredEntries,
      Set<String> includedEntries) {
    super(
        defaultSerializer,
        property,
        keySerializer,
        valueSerializer,
        ignoredEntries,
        includedEntries);
    this.storage = storage;
    this.defaultSerializer = defaultSerializer;
    this.handlers = handlers != null ? handlers : List.of();
  }

  @Override
  public void serialize(Map<?, ?> value, JsonGenerator gen, SerializerProvider provider)
      throws IOException {
    ObjectMapper objectMapper = (ObjectMapper) gen.getCodec();
    value = visit(value, objectMapper);
    this.defaultSerializer.serialize(value, gen, provider);
  }

  private <K, V> Map<K, V> visit(Map<K, V> value, ObjectMapper objectMapper) {
    // Maps, among collection-like types, are a little interesting. Other generic serializers do not
    // have a separate handling of properties.
    // Generic serializers are wrapped in a BeanSerializer which handles that.
    // However, this oddity requires us to inspect the property name in the event we want our custom
    // handlers to handle that.
    // If we do not handle the property, we will continue to visit to ensure there's no key in the
    // map that we want to handle.
    Map<K, V> finalValue = value;
    if (this._property != null) {
      ArtifactStoragePropertyHandler handler =
          this.handlers.stream()
              .filter(h -> h instanceof ArtifactStoragePropertyHandler)
              .map(h -> (ArtifactStoragePropertyHandler) h)
              .filter(h -> h.canHandleProperty(this._property, finalValue))
              .findFirst()
              .orElse(null);
      if (handler != null) {
        return handler.handleProperty(this.storage, this._property, value, objectMapper);
      }
    }

    ArtifactStorageHandler handler =
        this.handlers.stream()
            .filter(h -> h instanceof ArtifactStorageHandler)
            .map(h -> (ArtifactStorageHandler) h)
            .filter(h -> h.canHandle(finalValue))
            .findFirst()
            .orElse(null);
    if (handler != null) {
      return handler.handle(this.storage, value, objectMapper);
    }

    return value;
  }

  /**
   * These methods need to be overwritten due to the limitations of Jackson. Jackson calls an
   * internal method of _ensureOverrides which looks at the class calling this method and matches
   * the class to MapSerializer, and if it doesn't match, throw an exception. So we cannot call
   *
   * <pre>super.withContentInclusion</pre>
   *
   * otherwise we get that exception.
   */
  @Override
  public MapSerializer withContentInclusion(Object suppressableValue, boolean suppressNulls) {
    if ((suppressableValue == this._suppressableValue) && (suppressNulls == this._suppressNulls)) {
      return this;
    }
    return new MapSerializerHook(
        this.storage,
        this.handlers,
        this.defaultSerializer.withContentInclusion(suppressableValue, suppressNulls));
  }

  @Override
  public MapSerializer withContentInclusion(Object suppressableValue) {
    return new MapSerializerHook(
        this.storage,
        this.handlers,
        this.defaultSerializer.withContentInclusion(suppressableValue));
  }

  @Override
  public MapSerializer withResolved(
      BeanProperty property,
      JsonSerializer<?> keySerializer,
      JsonSerializer<?> valueSerializer,
      Set<String> ignored,
      Set<String> included,
      boolean sortKeys) {
    return new MapSerializerHook(
        this.storage,
        this.handlers,
        this.defaultSerializer.withResolved(
            property, keySerializer, valueSerializer, ignored, included, sortKeys),
        property,
        keySerializer,
        valueSerializer,
        ignored,
        included);
  }
}
