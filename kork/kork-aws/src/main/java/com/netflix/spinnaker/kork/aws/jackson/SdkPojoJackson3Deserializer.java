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

import software.amazon.awssdk.utils.builder.SdkBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Jackson 3 twin of {@link SdkPojoDeserializer}. */
public class SdkPojoJackson3Deserializer extends ValueDeserializer<Object> {
  private final Class<?> pojoType;

  public SdkPojoJackson3Deserializer(Class<?> pojoType) {
    this.pojoType = pojoType;
  }

  @Override
  public Object deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
    Class<?> builderClass;
    try {
      builderClass = (Class<?>) pojoType.getMethod("serializableBuilderClass").invoke(null);
    } catch (ReflectiveOperationException e) {
      throw DatabindException.from(
          p, "Unable to resolve serializable builder for " + pojoType.getName(), e);
    }
    Object builder = ctxt.readValue(p, builderClass);
    return ((SdkBuilder<?, ?>) builder).build();
  }
}
