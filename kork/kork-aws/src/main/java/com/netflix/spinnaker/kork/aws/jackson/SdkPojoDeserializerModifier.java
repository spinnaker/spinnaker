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

package com.netflix.spinnaker.kork.aws.jackson;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import software.amazon.awssdk.core.SdkPojo;

public class SdkPojoDeserializerModifier extends BeanDeserializerModifier {

  @Override
  public JsonDeserializer<?> modifyDeserializer(
      DeserializationConfig config, BeanDescription beanDesc, JsonDeserializer<?> deserializer) {
    Class<?> beanClass = beanDesc.getBeanClass();
    if (SdkPojo.class.isAssignableFrom(beanClass) && hasSerializableBuilder(beanClass)) {
      return new SdkPojoDeserializer(beanClass);
    }
    return deserializer;
  }

  private static boolean hasSerializableBuilder(Class<?> type) {
    try {
      type.getMethod("serializableBuilderClass");
      return true;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }
}
