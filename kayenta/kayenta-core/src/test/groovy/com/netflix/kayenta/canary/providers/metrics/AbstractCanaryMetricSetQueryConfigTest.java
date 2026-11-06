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

import com.netflix.kayenta.canary.CanaryConfig;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public class AbstractCanaryMetricSetQueryConfigTest {

  @Test
  public void jsonAlias_deserializesLegacyCustomInlineTemplateKeyIntoTemplateField()
      throws Exception {
    ObjectMapper objectMapper =
        JsonMapper.builder()
            .registerSubtypes(TestConcreteCanaryMetricSetQueryConfig.class)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    String legacyJson = "{\"type\":\"test-concrete\",\"customInlineTemplate\":\"legacy raw text\"}";

    TestConcreteCanaryMetricSetQueryConfig deserialized =
        objectMapper.readValue(legacyJson, TestConcreteCanaryMetricSetQueryConfig.class);

    assertThat(deserialized.getTemplate()).isEqualTo("legacy raw text");
  }

  @Test
  public void deserialization_acceptsTemplateKey() throws Exception {
    ObjectMapper objectMapper =
        JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    String json = "{\"type\":\"test-concrete\",\"template\":\"new text\"}";

    TestConcreteCanaryMetricSetQueryConfig deserialized =
        objectMapper.readValue(json, TestConcreteCanaryMetricSetQueryConfig.class);

    assertThat(deserialized.getTemplate()).isEqualTo("new text");
  }

  @Test
  public void deserialization_templateWinsOverLegacyKeyInEitherOrder() throws Exception {
    ObjectMapper objectMapper =
        JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    String templateFirst =
        "{\"type\":\"test-concrete\",\"template\":\"edited\",\"customInlineTemplate\":\"stale\"}";
    String legacyFirst =
        "{\"type\":\"test-concrete\",\"customInlineTemplate\":\"stale\",\"template\":\"edited\"}";

    assertThat(
            objectMapper
                .readValue(templateFirst, TestConcreteCanaryMetricSetQueryConfig.class)
                .getTemplate())
        .isEqualTo("edited");
    assertThat(
            objectMapper
                .readValue(legacyFirst, TestConcreteCanaryMetricSetQueryConfig.class)
                .getTemplate())
        .isEqualTo("edited");
  }

  @Test
  public void getEditTemplatePut_keepsEditedTemplateWhenStaleLegacyKeyIsEchoedBack()
      throws Exception {
    // Kayenta's mapper ignores unknown properties (e.g. the serialized serviceType).
    ObjectMapper objectMapper =
        JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    TestConcreteCanaryMetricSetQueryConfig original =
        TestConcreteCanaryMetricSetQueryConfig.builder().template("ORIGINAL").build();

    ObjectNode tree = (ObjectNode) objectMapper.valueToTree(original);
    tree.put("template", "EDITED");

    TestConcreteCanaryMetricSetQueryConfig result =
        objectMapper.treeToValue(tree, TestConcreteCanaryMetricSetQueryConfig.class);

    assertThat(result.getTemplate()).isEqualTo("EDITED");
  }

  @Test
  public void serialization_writesBothTemplateAndLegacyCustomInlineTemplateKeys() throws Exception {
    ObjectMapper objectMapper =
        JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    TestConcreteCanaryMetricSetQueryConfig query =
        TestConcreteCanaryMetricSetQueryConfig.builder().template("some text").build();

    String json = objectMapper.writeValueAsString(query);

    assertThat(json).contains("\"template\":\"some text\"");
    assertThat(json).contains("\"customInlineTemplate\":\"some text\"");

    // and the dual-written form round-trips
    TestConcreteCanaryMetricSetQueryConfig roundTripped =
        objectMapper.readValue(json, TestConcreteCanaryMetricSetQueryConfig.class);
    assertThat(roundTripped.getTemplate()).isEqualTo("some text");
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
