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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.MapDeserializer;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * DeserializerVisitorHook just wraps any deserializer. This is much different than how
 * serialization is handled, due to the need of using a MapSerializer instead. The reason for this
 * is the flow of deserialization is much simpler and requires little bootstrapping unlike
 * serialization. With serialization, we need to resolve and provide contextual serialization
 * information. In this case, all that information is already stored on the passed in deserializer.
 */
public class MapDeserializerHook extends MapDeserializer {
  @Autowired private final ArtifactStore storage;
  private final List<ArtifactHandler> handlers;
  private final MapDeserializer deserializer;

  public MapDeserializerHook(
      ArtifactStore storage, List<ArtifactHandler> handlers, MapDeserializer deserializer) {
    super(deserializer);
    this.storage = storage;
    this.handlers = handlers;
    this.deserializer = deserializer;
  }

  @Override
  public Map<Object, Object> deserialize(JsonParser jsonParser, DeserializationContext ctxt)
      throws IOException {
    Map<Object, Object> m = this.deserializer.deserialize(jsonParser, ctxt);
    return (Map<Object, Object>) visit(m, (ObjectMapper) jsonParser.getCodec());
  }

  @Override
  public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property)
      throws JsonMappingException {
    return new MapDeserializerHook(
        this.storage,
        this.handlers,
        (MapDeserializer) this.deserializer.createContextual(ctxt, property));
  }

  private Object visit(Map<Object, Object> m, ObjectMapper objectMapper) {
    ArtifactExpandHandler handler =
        this.handlers.stream()
            .filter(h -> h instanceof ArtifactExpandHandler)
            .map(h -> (ArtifactExpandHandler) h)
            .filter(h -> h.canHandle(m))
            .findFirst()
            .orElse(null);
    if (handler != null) {
      Object temp = handler.handle(this.storage, m, Map.class, objectMapper);
      return temp;
    }

    for (Map.Entry<Object, Object> entry : m.entrySet()) {
      Object keyObj = entry.getKey();
      if (!(keyObj instanceof String)) {
        continue;
      }

      Object v = entry.getValue();
      if (v instanceof Map) {
        Object temp = this.visit((Map) v, objectMapper);
        if (temp != v) {
          m.put(keyObj, temp);
        }
      }
    }
    return m;
  }
}
