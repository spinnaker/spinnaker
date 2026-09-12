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
import tools.jackson.core.Version;
import tools.jackson.databind.module.SimpleModule;

/**
 * Jackson 3 twin of {@link AwsSdkV2Module}: (de)serializes AWS SDK v2 models via their protocol
 * shape. Required because Spring Framework 7 serves HTTP JSON with Jackson 3, which ignores Jackson
 * 2 modules.
 */
public class AwsSdkV2Jackson3Module extends SimpleModule {

  public AwsSdkV2Jackson3Module() {
    super("AwsSdkV2Jackson3Module", Version.unknownVersion());
    addSerializer(SdkPojo.class, new SdkPojoJackson3Serializer());
    setDeserializerModifier(new SdkPojoJackson3DeserializerModifier());
  }
}
