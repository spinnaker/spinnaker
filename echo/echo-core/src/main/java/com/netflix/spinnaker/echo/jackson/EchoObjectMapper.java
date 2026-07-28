/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.echo.jackson;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.jackson.mixin.EventMixin;

public class EchoObjectMapper {
  private EchoObjectMapper() {}

  private static final int DEFAULT_MAX_NAME_LENGTH = 200_000;
  private static final int DEFAULT_MAX_STRING_LENGTH = 50_000_000;
  private static final int DEFAULT_MAX_NESTING_DEPTH = 2_000;
  private static final int DEFAULT_MAX_NUMBER_LENGTH = 5_000;
  private static final long DEFAULT_MAX_DOCUMENT_LENGTH = -1;

  private static final ObjectMapper INSTANCE = newInstance();

  public static ObjectMapper newInstance() {
    StreamReadConstraints constraints =
        StreamReadConstraints.builder()
            .maxNameLength(DEFAULT_MAX_NAME_LENGTH)
            .maxStringLength(DEFAULT_MAX_STRING_LENGTH)
            .maxNestingDepth(DEFAULT_MAX_NESTING_DEPTH)
            .maxNumberLength(DEFAULT_MAX_NUMBER_LENGTH)
            .maxDocumentLength(DEFAULT_MAX_DOCUMENT_LENGTH)
            .build();

    return new ObjectMapper(JsonFactory.builder().streamReadConstraints(constraints).build())
        .addMixIn(Event.class, EventMixin.class)
        .registerModule(new Jdk8Module())
        .registerModule(new JavaTimeModule())
        .disable(FAIL_ON_UNKNOWN_PROPERTIES)
        .setSerializationInclusion(NON_NULL);
  }

  /**
   * Return an ObjectMapper instance that can be reused. Do not change the configuration of this
   * instance as it will be shared across the entire application, use {@link #newInstance()}
   * instead.
   *
   * @return Reusable ObjectMapper instance
   */
  public static ObjectMapper getInstance() {
    return INSTANCE;
  }
}
