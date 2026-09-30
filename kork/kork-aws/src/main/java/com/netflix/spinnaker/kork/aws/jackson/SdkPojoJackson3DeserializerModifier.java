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

import software.amazon.awssdk.core.SdkPojo;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;

/** Jackson 3 twin of {@link SdkPojoDeserializerModifier}. */
public class SdkPojoJackson3DeserializerModifier extends ValueDeserializerModifier {
  @Override
  public ValueDeserializer<?> modifyDeserializer(
      DeserializationConfig config,
      BeanDescription.Supplier beanDesc,
      ValueDeserializer<?> deserializer) {
    Class<?> beanClass = beanDesc.getBeanClass();
    if (SdkPojo.class.isAssignableFrom(beanClass) && hasSerializableBuilder(beanClass)) {
      return new SdkPojoJackson3Deserializer(beanClass);
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
