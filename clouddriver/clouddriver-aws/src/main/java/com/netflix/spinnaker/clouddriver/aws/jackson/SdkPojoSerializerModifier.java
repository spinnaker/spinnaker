/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.StdDelegatingSerializer;
import com.fasterxml.jackson.databind.util.StdConverter;
import java.io.IOException;
import java.util.List;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.utils.builder.SdkBuilder;
import software.amazon.awssdk.utils.builder.ToCopyableBuilder;

/**
 * Serializes AWS SDK v2 model types as their builders, the mirror image of {@link
 * SdkPojoDeserializerModifier}. v2 model classes only have fluent accessors, so Jackson finds no
 * properties on them; their builders have bean-style getters and setters, so serializing and
 * deserializing through the builder gives the same property names in both directions (e.g. {@code
 * vpcZoneIdentifier}, {@code dnsName}) without any naming logic of our own.
 *
 * <p>Builder types also implement {@link SdkPojo} but have no {@code serializableBuilderClass()},
 * so they keep standard bean serialization, which is what the delegate relies on (and what prevents
 * infinite recursion).
 *
 * <p>Builder getters return null for a list or map AWS didn't return, where the model's accessor
 * returns an empty one. Those are written as empty, as the model accessors (and SDK v1) always did,
 * so consumers that expect e.g. {@code suspendedProcesses} to be present keep working.
 */
public class SdkPojoSerializerModifier extends BeanSerializerModifier {

  private static final StdDelegatingSerializer TO_BUILDER =
      new StdDelegatingSerializer(
          new StdConverter<ToCopyableBuilder<?, ?>, Object>() {
            @Override
            public Object convert(ToCopyableBuilder<?, ?> value) {
              return value.toBuilder();
            }
          });

  private static final JsonSerializer<Object> EMPTY_ARRAY =
      new JsonSerializer<>() {
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
          gen.writeStartArray();
          gen.writeEndArray();
        }
      };

  private static final JsonSerializer<Object> EMPTY_OBJECT =
      new JsonSerializer<>() {
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
          gen.writeStartObject();
          gen.writeEndObject();
        }
      };

  @Override
  public JsonSerializer<?> modifySerializer(
      SerializationConfig config, BeanDescription beanDesc, JsonSerializer<?> serializer) {
    Class<?> beanClass = beanDesc.getBeanClass();
    if (SdkPojo.class.isAssignableFrom(beanClass)
        && ToCopyableBuilder.class.isAssignableFrom(beanClass)
        && SdkPojoDeserializerModifier.hasSerializableBuilder(beanClass)) {
      return TO_BUILDER;
    }
    return serializer;
  }

  @Override
  public List<BeanPropertyWriter> changeProperties(
      SerializationConfig config,
      BeanDescription beanDesc,
      List<BeanPropertyWriter> beanProperties) {
    if (SdkBuilder.class.isAssignableFrom(beanDesc.getBeanClass())) {
      for (BeanPropertyWriter property : beanProperties) {
        JavaType type = property.getType();
        if (type.isCollectionLikeType() || type.isArrayType()) {
          property.assignNullSerializer(EMPTY_ARRAY);
        } else if (type.isMapLikeType()) {
          property.assignNullSerializer(EMPTY_OBJECT);
        }
      }
    }
    return beanProperties;
  }
}
