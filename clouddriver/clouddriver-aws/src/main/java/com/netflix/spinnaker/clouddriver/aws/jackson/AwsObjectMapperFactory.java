/*
 * Copyright 2026 Spinnaker Authors
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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Replaces {@code com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer}, which pulled in the
 * entire AWS SDK v1 (2000+ Jackson mixins, one per v1 model class) just to reach these five generic
 * Jackson settings. Its custom {@code PropertyNamingStrategy} is dropped rather than ported: AWS
 * SDK v2 model objects ({@link software.amazon.awssdk.core.SdkPojo}) are (de)serialized by {@link
 * SdkPojoSerializer}/{@link SdkPojoDeserializer} via each field's own {@code memberName()},
 * bypassing the mapper's naming strategy entirely, so it only ever applied to AWS SDK v1 model
 * classes -- none of which this codebase still constructs.
 */
public final class AwsObjectMapperFactory {

  private AwsObjectMapperFactory() {}

  public static ObjectMapper createConfigured() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(MapperFeature.AUTO_DETECT_IS_GETTERS, false);
    objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    objectMapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    // Lets already-cached JSON written with fields no longer present on the current model
    // (e.g. from a since-removed AWS SDK v1 shape) keep deserializing instead of failing.
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return objectMapper;
  }
}
