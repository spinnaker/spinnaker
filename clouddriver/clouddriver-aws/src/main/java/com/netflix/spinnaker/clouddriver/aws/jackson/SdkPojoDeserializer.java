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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.io.IOException;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.utils.builder.SdkBuilder;

/**
 * Deserializes an AWS SDK v2 model type (an {@link SdkPojo}) from JSON. v2 model classes are
 * immutable and builder-only, so Jackson cannot bind them directly. This deserializer instead
 * deserializes into the model's mutable builder — obtained via the static {@code
 * serializableBuilderClass()} method every v2 model exposes — and then calls {@link
 * SdkBuilder#build()}.
 *
 * <p>Nested {@link SdkPojo} fields are handled recursively because the modifier that installs this
 * deserializer applies to every model type (but not to the builder itself, which has no {@code
 * serializableBuilderClass()} and thus uses standard bean deserialization).
 */
public class SdkPojoDeserializer extends JsonDeserializer<Object> {

  private final Class<?> pojoType;

  public SdkPojoDeserializer(Class<?> pojoType) {
    this.pojoType = pojoType;
  }

  @Override
  public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    Class<?> builderClass;
    try {
      builderClass = (Class<?>) pojoType.getMethod("serializableBuilderClass").invoke(null);
    } catch (ReflectiveOperationException e) {
      throw new JsonMappingException(
          p, "Unable to resolve serializable builder for " + pojoType.getName(), e);
    }
    Object builder = ctxt.readValue(p, builderClass);
    return ((SdkBuilder<?, ?>) builder).build();
  }
}
