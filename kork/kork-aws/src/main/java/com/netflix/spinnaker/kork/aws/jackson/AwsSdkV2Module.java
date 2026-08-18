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

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.module.SimpleModule;
import software.amazon.awssdk.core.SdkPojo;

/**
 * Jackson module that enables serialization and deserialization of AWS SDK v2 model types ({@link
 * SdkPojo}). Register this module on any {@code ObjectMapper} that needs to handle v2 SDK objects.
 *
 * <p>Spring Boot applications should prefer {@link AwsSdkV2JacksonConfiguration}, which registers
 * this module as a bean and lets Spring auto-configure it onto the shared {@code ObjectMapper}.
 */
public class AwsSdkV2Module extends SimpleModule {

  public AwsSdkV2Module() {
    super("AwsSdkV2Module", Version.unknownVersion());
    addSerializer(SdkPojo.class, new SdkPojoSerializer());
    setDeserializerModifier(new SdkPojoDeserializerModifier());
  }
}
