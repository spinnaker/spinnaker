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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.module.SimpleModule;
import software.amazon.awssdk.utils.builder.SdkBuilder;

/**
 * (De)serializes AWS SDK v2 model types through their builders. See {@link
 * SdkPojoSerializerModifier} and {@link SdkPojoDeserializerModifier}.
 */
public class AwsSdkV2Module extends SimpleModule {

  public AwsSdkV2Module() {
    super("AwsSdkV2Module", Version.unknownVersion());
    setSerializerModifier(new SdkPojoSerializerModifier());
    setDeserializerModifier(new SdkPojoDeserializerModifier());
    // Builder getters return null for every unset scalar member, so omit those whatever the host
    // mapper's inclusion setting is. Unset lists and maps are written empty; see
    // SdkPojoSerializerModifier.
    setMixInAnnotation(SdkBuilder.class, OmitNullMembers.class);
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private interface OmitNullMembers {}
}
