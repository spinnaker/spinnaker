/*
 * Copyright 2025 Netflix, Inc.
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

package com.netflix.spinnaker.echo.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EchoObjectMapperTest {

  @Test
  void canDeserializeMapWithKeyExceedingDefaultJacksonNameLengthLimit() throws Exception {
    ObjectMapper mapper = EchoObjectMapper.newInstance();

    String largeKey = "x".repeat(60_000);
    String json = "{\"" + largeKey + "\":\"value\"}";

    Map<String, Object> result = mapper.readValue(json, Map.class);

    assertThat(result).containsKey(largeKey);
    assertThat(result.get(largeKey)).isEqualTo("value");
  }

  @Test
  void defaultStreamReadConstraintsAreRelaxed() {
    ObjectMapper mapper = EchoObjectMapper.newInstance();
    StreamReadConstraints constraints = mapper.getFactory().streamReadConstraints();

    assertThat(constraints.getMaxNameLength()).isEqualTo(200_000);
    assertThat(constraints.getMaxStringLength()).isEqualTo(50_000_000);
    assertThat(constraints.getMaxNestingDepth()).isEqualTo(2_000);
    assertThat(constraints.getMaxNumberLength()).isEqualTo(5_000);
    assertThat(constraints.getMaxDocumentLength()).isEqualTo(-1);
  }
}
