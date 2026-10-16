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

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

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
    return JsonMapper.builder()
        // Jackson 3 removed MapperFeature.AUTO_DETECT_IS_GETTERS; AWS SDK v2 model
        // objects are (de)serialized by SdkPojoSerializer/SdkPojoDeserializer via each
        // field's own memberName(), bypassing bean introspection entirely, so there is
        // nothing to port here.
        .enable(SerializationFeature.INDENT_OUTPUT)
        // Jackson 3 removed SerializationFeature.WRITE_NULL_MAP_VALUES. NON_NULL content
        // inclusion preserves the previous behavior (no null map entries).
        .changeDefaultPropertyInclusion(
            value -> value.withContentInclusion(JsonInclude.Include.NON_NULL))
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        // Lets already-cached JSON written with fields no longer present on the current model
        // (e.g. from a since-removed AWS SDK v1 shape) keep deserializing instead of failing.
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();
  }
}
