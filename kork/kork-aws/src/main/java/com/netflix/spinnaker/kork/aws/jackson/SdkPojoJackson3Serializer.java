/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.kork.aws.jackson;

import java.lang.reflect.Method;
import java.util.Optional;
import software.amazon.awssdk.core.SdkField;
import software.amazon.awssdk.core.SdkPojo;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Jackson 3 twin of {@link SdkPojoSerializer}: serializes AWS SDK v2 models via their {@code
 * sdkFields()} protocol shape, since Spring Framework 7 serves HTTP JSON with Jackson 3 (which
 * ignores Jackson 2 modules).
 */
public class SdkPojoJackson3Serializer extends ValueSerializer<SdkPojo> {
  @Override
  public void serialize(SdkPojo value, JsonGenerator gen, SerializationContext serializers)
      throws JacksonException {
    gen.writeStartObject();
    for (SdkField<?> field : value.sdkFields()) {
      String memberName = field.memberName();
      Object fieldValue = getValueForField(value, memberName);
      if (fieldValue != null) {
        gen.writeName(toCamelCase(memberName));
        serializers.writeValue(gen, fieldValue);
      }
    }
    gen.writeEndObject();
  }

  private static Object getValueForField(SdkPojo pojo, String fieldName) {
    try {
      Method method = pojo.getClass().getMethod("getValueForField", String.class, Class.class);
      Optional<?> value = (Optional<?>) method.invoke(pojo, fieldName, Object.class);
      return value.orElse(null);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static String toCamelCase(String name) {
    if (name == null || name.isEmpty()) {
      return name;
    }
    return Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }
}
