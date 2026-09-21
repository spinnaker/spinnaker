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

import org.junit.jupiter.api.Test;
import org.springframework.boot.jackson.autoconfigure.JsonFactoryBuilderCustomizer;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.JsonFactoryBuilder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class JacksonStreamReadConstraintsCustomizerTest {

  @Test
  void customizerAppliesRelaxedStreamReadConstraints() {
    JsonFactoryBuilder builder = JsonFactory.builder();
    JsonFactoryBuilderCustomizer customizer = new JacksonStreamReadConstraintsCustomizer();
    customizer.customize(builder);

    JsonFactory factory = builder.build();
    ObjectMapper mapper = JsonMapper.builder(factory).build();

    StreamReadConstraints constraints = mapper.tokenStreamFactory().streamReadConstraints();
    assertThat(constraints.getMaxNameLength()).isEqualTo(200_000);
    assertThat(constraints.getMaxStringLength()).isEqualTo(50_000_000);
    assertThat(constraints.getMaxNestingDepth()).isEqualTo(2_000);
    assertThat(constraints.getMaxNumberLength()).isEqualTo(5_000);
    assertThat(constraints.getMaxDocumentLength()).isEqualTo(-1);
  }

  @Test
  void customizerAllowsDeserializationOfLargeKeys() throws Exception {
    JsonFactoryBuilder builder = JsonFactory.builder();
    JsonFactoryBuilderCustomizer customizer = new JacksonStreamReadConstraintsCustomizer();
    customizer.customize(builder);

    ObjectMapper mapper = JsonMapper.builder(builder.build()).build();

    String largeKey = "x".repeat(60_000);
    String json = "{\"" + largeKey + "\":\"value\"}";

    java.util.Map<String, Object> result = mapper.readValue(json, java.util.Map.class);

    assertThat(result).containsKey(largeKey);
    assertThat(result.get(largeKey)).isEqualTo("value");
  }
}
