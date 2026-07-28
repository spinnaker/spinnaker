/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.jackson;

import com.fasterxml.jackson.core.StreamReadConstraints;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Customizes every Spring Boot {@code ObjectMapper} with relaxed {@link StreamReadConstraints} to
 * avoid deserialization failures on large JSON payloads that exceed Jackson 2.15+ defaults.
 *
 * <p>This customizer overrides the global Jackson default {@link StreamReadConstraints} instead of
 * replacing the {@code JsonFactory} on the builder. That avoids a {@code ClassCastException} when
 * Spring Boot auto-configures {@code MappingJackson2XmlHttpMessageConverter} (which requires an
 * {@code XmlFactory}) in services that have {@code jackson-dataformat-xml} on the classpath.
 */
public class JacksonStreamReadConstraintsCustomizer
    implements Jackson2ObjectMapperBuilderCustomizer {

  private static final int DEFAULT_MAX_NAME_LENGTH = 200_000;
  private static final int DEFAULT_MAX_STRING_LENGTH = 50_000_000;
  private static final int DEFAULT_MAX_NESTING_DEPTH = 2_000;
  private static final int DEFAULT_MAX_NUMBER_LENGTH = 5_000;
  private static final long DEFAULT_MAX_DOCUMENT_LENGTH = -1;

  static {
    StreamReadConstraints constraints =
        StreamReadConstraints.builder()
            .maxNameLength(DEFAULT_MAX_NAME_LENGTH)
            .maxStringLength(DEFAULT_MAX_STRING_LENGTH)
            .maxNestingDepth(DEFAULT_MAX_NESTING_DEPTH)
            .maxNumberLength(DEFAULT_MAX_NUMBER_LENGTH)
            .maxDocumentLength(DEFAULT_MAX_DOCUMENT_LENGTH)
            .build();

    StreamReadConstraints.overrideDefaultStreamReadConstraints(constraints);
  }

  @Override
  public void customize(Jackson2ObjectMapperBuilder builder) {
    // No per-builder customization needed; global defaults are applied via the static initializer.
  }
}
