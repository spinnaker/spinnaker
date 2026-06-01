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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class JacksonStreamReadConstraintsCustomizerTest {

  @Test
  void customizerAppliesRelaxedStreamReadConstraints() {
    Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
    Jackson2ObjectMapperBuilderCustomizer customizer = new JacksonStreamReadConstraintsCustomizer();
    customizer.customize(builder);

    ObjectMapper mapper = builder.build();

    StreamReadConstraints constraints = mapper.getFactory().streamReadConstraints();
    assertThat(constraints.getMaxNameLength()).isEqualTo(200_000);
    assertThat(constraints.getMaxStringLength()).isEqualTo(50_000_000);
    assertThat(constraints.getMaxNestingDepth()).isEqualTo(2_000);
    assertThat(constraints.getMaxNumberLength()).isEqualTo(5_000);
    assertThat(constraints.getMaxDocumentLength()).isEqualTo(-1);
  }

  @Test
  void customizerAllowsDeserializationOfLargeKeys() throws Exception {
    Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
    Jackson2ObjectMapperBuilderCustomizer customizer = new JacksonStreamReadConstraintsCustomizer();
    customizer.customize(builder);

    ObjectMapper mapper = builder.build();

    String largeKey = "x".repeat(60_000);
    String json = "{\"" + largeKey + "\":\"value\"}";

    java.util.Map<String, Object> result = mapper.readValue(json, java.util.Map.class);

    assertThat(result).containsKey(largeKey);
    assertThat(result.get(largeKey)).isEqualTo("value");
  }
}
