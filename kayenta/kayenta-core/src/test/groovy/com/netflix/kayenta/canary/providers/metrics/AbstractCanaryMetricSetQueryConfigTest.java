/*
 * Copyright 2026 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.kayenta.canary.providers.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.canary.CanaryConfig;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class AbstractCanaryMetricSetQueryConfigTest {

  @Test
  public void jsonAlias_deserializesLegacyCustomInlineTemplateKeyIntoTemplateField()
      throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerSubtypes(TestConcreteCanaryMetricSetQueryConfig.class);
    String legacyJson = "{\"type\":\"test-concrete\",\"customInlineTemplate\":\"legacy raw text\"}";

    TestConcreteCanaryMetricSetQueryConfig deserialized =
        objectMapper.readValue(legacyJson, TestConcreteCanaryMetricSetQueryConfig.class);

    assertThat(deserialized.getTemplate()).isEqualTo("legacy raw text");
  }

  @Test
  public void serialization_writesNewTemplateKeyNotLegacyCustomInlineTemplateKey()
      throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    TestConcreteCanaryMetricSetQueryConfig query =
        TestConcreteCanaryMetricSetQueryConfig.builder().template("some text").build();

    String json = objectMapper.writeValueAsString(query);

    assertThat(json).contains("\"template\":\"some text\"");
    assertThat(json).doesNotContain("customInlineTemplate");
  }

  @Test
  public void getTemplate_returnsTemplateFieldWhenSet() {
    TestConcreteCanaryMetricSetQueryConfig query =
        TestConcreteCanaryMetricSetQueryConfig.builder().template("inline ${scope}").build();
    CanaryConfig canaryConfig = CanaryConfig.builder().build();

    assertThat(query.getTemplate(canaryConfig)).isEqualTo("inline ${scope}");
  }

  @Test
  public void getTemplate_unescapesTemplateField() {
    TestConcreteCanaryMetricSetQueryConfig query =
        TestConcreteCanaryMetricSetQueryConfig.builder().template("inline $\\{scope}").build();
    CanaryConfig canaryConfig = CanaryConfig.builder().build();

    assertThat(query.getTemplate(canaryConfig)).isEqualTo("inline ${scope}");
  }

  @Test
  public void getTemplate_fallsBackToCustomFilterTemplateWhenTemplateEmpty() {
    TestConcreteCanaryMetricSetQueryConfig query =
        TestConcreteCanaryMetricSetQueryConfig.builder().customFilterTemplate("named").build();
    CanaryConfig canaryConfig =
        CanaryConfig.builder()
            .templates(Collections.singletonMap("named", "named $\\{scope}"))
            .build();

    assertThat(query.getTemplate(canaryConfig)).isEqualTo("named ${scope}");
  }

  @Test
  public void getTemplate_templateTakesPrecedenceOverCustomFilterTemplate() {
    TestConcreteCanaryMetricSetQueryConfig query =
        TestConcreteCanaryMetricSetQueryConfig.builder()
            .template("inline ${scope}")
            .customFilterTemplate("named")
            .build();
    CanaryConfig canaryConfig =
        CanaryConfig.builder()
            .templates(Collections.singletonMap("named", "named ${scope}"))
            .build();

    assertThat(query.getTemplate(canaryConfig)).isEqualTo("inline ${scope}");
  }

  @Test
  public void getTemplate_returnsNullWhenNeitherFieldSet() {
    TestConcreteCanaryMetricSetQueryConfig query =
        TestConcreteCanaryMetricSetQueryConfig.builder().build();
    CanaryConfig canaryConfig = CanaryConfig.builder().build();

    assertThat(query.getTemplate(canaryConfig)).isNull();
  }

  @Test
  public void getTemplate_returnsNullWhenCustomFilterTemplateNameNotFoundInMap() {
    TestConcreteCanaryMetricSetQueryConfig query =
        TestConcreteCanaryMetricSetQueryConfig.builder().customFilterTemplate("missing").build();
    CanaryConfig canaryConfig =
        CanaryConfig.builder()
            .templates(Collections.singletonMap("named", "named ${scope}"))
            .build();

    assertThat(query.getTemplate(canaryConfig)).isNull();
  }

  @Test
  public void getTemplate_returnsNullWhenCustomFilterTemplateSetButNoTemplatesMapAtAll() {
    TestConcreteCanaryMetricSetQueryConfig query =
        TestConcreteCanaryMetricSetQueryConfig.builder().customFilterTemplate("named").build();
    CanaryConfig canaryConfig = CanaryConfig.builder().build();

    assertThat(query.getTemplate(canaryConfig)).isNull();
  }

  @Test
  public void getTemplate_returnsNullWhenCanaryConfigIsNullAndOnlyCustomFilterTemplateSet() {
    TestConcreteCanaryMetricSetQueryConfig query =
        TestConcreteCanaryMetricSetQueryConfig.builder().customFilterTemplate("named").build();

    assertThat(query.getTemplate(null)).isNull();
  }
}
