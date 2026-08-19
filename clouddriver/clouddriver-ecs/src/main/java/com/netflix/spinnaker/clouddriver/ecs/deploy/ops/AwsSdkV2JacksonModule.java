/*
 * Copyright 2026 spinnaker.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.deploy.ops;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.Deserializers;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.core.SdkField;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.core.protocol.MarshallingType;
import software.amazon.awssdk.core.traits.ListTrait;
import software.amazon.awssdk.core.traits.MapTrait;
import software.amazon.awssdk.utils.builder.Buildable;

/**
 * Jackson module that teaches an {@link com.fasterxml.jackson.databind.ObjectMapper} how to
 * deserialize AWS SDK v2 model objects (implementations of {@link SdkPojo}).
 *
 * <p>SDK v2 model classes are immutable and expose only a static {@code builder()} with fluent,
 * overloaded setters, so Jackson cannot construct them out of the box ("no Creators"). This module
 * bypasses Jackson's bean/builder machinery for those types and instead builds them from the SDK's
 * own field metadata ({@link SdkPojo#sdkFields()}), which also cleanly handles nested models,
 * lists, maps, and enum-valued fields.
 */
public class AwsSdkV2JacksonModule extends SimpleModule {

  @Override
  public void setupModule(SetupContext context) {
    context.addDeserializers(new SdkPojoDeserializers());
  }
}

/**
 * Supplies a {@link SdkPojoDeserializer} for any SDK v2 model type that exposes {@code builder()}.
 */
class SdkPojoDeserializers extends Deserializers.Base {
  @Override
  public JsonDeserializer<?> findBeanDeserializer(
      JavaType type, DeserializationConfig config, BeanDescription beanDesc) {
    Class<?> raw = type.getRawClass();
    if (SdkPojo.class.isAssignableFrom(raw) && hasStaticBuilder(raw)) {
      return new SdkPojoDeserializer(raw);
    }
    return null;
  }

  private static boolean hasStaticBuilder(Class<?> raw) {
    try {
      return java.lang.reflect.Modifier.isStatic(raw.getMethod("builder").getModifiers());
    } catch (NoSuchMethodException e) {
      return false;
    }
  }
}

/** Deserializes a single SDK v2 model type from JSON using the SDK builder + field metadata. */
class SdkPojoDeserializer extends JsonDeserializer<Object> {

  private final Class<?> modelClass;

  SdkPojoDeserializer(Class<?> modelClass) {
    this.modelClass = modelClass;
  }

  @Override
  public Object deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
    JsonNode node = parser.getCodec().readTree(parser);
    try {
      SdkPojo builder = (SdkPojo) modelClass.getMethod("builder").invoke(null);
      return SdkPojoBuilders.populate(builder, node);
    } catch (ReflectiveOperationException e) {
      throw new IOException("Unable to deserialize AWS SDK v2 model " + modelClass.getName(), e);
    }
  }
}

/** Helpers that build SDK v2 objects from a {@link JsonNode} using {@link SdkField} metadata. */
final class SdkPojoBuilders {

  private SdkPojoBuilders() {}

  /** Populates the given SDK builder from the JSON node and builds the immutable model object. */
  static Object populate(SdkPojo builder, JsonNode node) {
    for (SdkField<?> field : builder.sdkFields()) {
      JsonNode child = fieldNode(node, field);
      if (child == null || child.isNull()) {
        continue;
      }
      field.set(builder, convert(field, child));
    }
    return ((Buildable) builder).build();
  }

  private static JsonNode fieldNode(JsonNode node, SdkField<?> field) {
    JsonNode child = node.get(field.memberName());
    if (child == null && field.locationName() != null) {
      child = node.get(field.locationName());
    }
    return child;
  }

  private static Object convert(SdkField<?> field, JsonNode node) {
    MarshallingType<?> type = field.marshallingType();

    if (type == MarshallingType.STRING) {
      return node.asText();
    } else if (type == MarshallingType.INTEGER) {
      return node.asInt();
    } else if (type == MarshallingType.LONG) {
      return node.asLong();
    } else if (type == MarshallingType.SHORT) {
      return (short) node.asInt();
    } else if (type == MarshallingType.FLOAT) {
      return (float) node.asDouble();
    } else if (type == MarshallingType.DOUBLE) {
      return node.asDouble();
    } else if (type == MarshallingType.BIG_DECIMAL) {
      return node.decimalValue();
    } else if (type == MarshallingType.BOOLEAN) {
      return node.asBoolean();
    } else if (type == MarshallingType.INSTANT) {
      return node.isNumber()
          ? Instant.ofEpochMilli((long) (node.asDouble() * 1000))
          : Instant.parse(node.asText());
    } else if (type == MarshallingType.SDK_POJO) {
      return populate(field.constructor().get(), node);
    } else if (type == MarshallingType.LIST) {
      return convertList(field, node);
    } else if (type == MarshallingType.MAP) {
      return convertMap(field, node);
    }

    // Fallback for unhandled scalar types (DOCUMENT, SDK_BYTES, etc.)
    return node.asText();
  }

  private static List<Object> convertList(SdkField<?> field, JsonNode node) {
    ListTrait trait = field.getTrait(ListTrait.class);
    SdkField<?> memberField = trait.memberFieldInfo();
    List<Object> values = new ArrayList<>();
    for (JsonNode element : node) {
      values.add(element.isNull() ? null : convert(memberField, element));
    }
    return values;
  }

  private static Map<String, Object> convertMap(SdkField<?> field, JsonNode node) {
    MapTrait trait = field.getTrait(MapTrait.class);
    SdkField<?> valueField = trait.valueFieldInfo();
    Map<String, Object> values = new LinkedHashMap<>();
    for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
      Map.Entry<String, JsonNode> entry = it.next();
      JsonNode value = entry.getValue();
      values.put(entry.getKey(), value.isNull() ? null : convert(valueField, value));
    }
    return values;
  }
}
