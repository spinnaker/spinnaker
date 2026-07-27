/*
 * Copyright 2024 Netflix, Inc.
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
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Optional;
import software.amazon.awssdk.core.SdkField;
import software.amazon.awssdk.core.SdkPojo;

public class SdkPojoSerializer extends JsonSerializer<SdkPojo> {

  @Override
  public void serialize(SdkPojo value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    gen.writeStartObject();
    for (SdkField<?> field : value.sdkFields()) {
      String memberName = field.memberName();
      Object fieldValue = getValueForField(value, memberName);
      if (fieldValue != null) {
        gen.writeFieldName(toCamelCase(memberName));
        serializers.defaultSerializeValue(fieldValue, gen);
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
