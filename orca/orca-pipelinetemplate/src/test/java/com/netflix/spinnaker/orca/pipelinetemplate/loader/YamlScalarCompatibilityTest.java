/*
 * Copyright 2026 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipelinetemplate.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netflix.spinnaker.kork.yaml.YamlHelper;
import com.netflix.spinnaker.kork.yaml.YamlParserProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pipeline templates are authored as YAML and loaded untyped (Map&lt;String, Object&gt;). Jackson
 * 3's YAML module follows YAML 1.2 where Jackson 2 followed 1.1, so plain scalars that used to
 * become booleans, nulls and integers stay strings without the compatibility factory. The expected
 * values in {@link #yaml11ScalarsReadLikeJackson2()} were produced by a Jackson 2.21 {@code
 * YAMLMapper}.
 */
class YamlScalarCompatibilityTest {

  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final ObjectMapper source = JsonMapper.builder().build();

  private ObjectMapper yaml() {
    return YamlObjectMapperFactory.create(source, new YamlHelper(new YamlParserProperties()));
  }

  private Object scalar(String literal) throws Exception {
    return yaml().readValue("v: " + literal, MAP).get("v");
  }

  @Test
  void jackson2CompatibleScalarsStillParse() throws Exception {
    assertThat(scalar("true")).isEqualTo(true);
    assertThat(scalar("false")).isEqualTo(false);
    assertThat(scalar("null")).isNull();
    assertThat(scalar("")).isNull();
    assertThat(scalar("42")).isEqualTo(42);
    assertThat(scalar("1.5")).isEqualTo(1.5);
    assertThat(scalar("'yes'")).isEqualTo("yes");
  }

  /** Jackson 3 alone leaves these as strings; the factory keeps the Jackson 2 values. */
  @Test
  void yaml11ScalarsReadLikeJackson2() throws Exception {
    assertThat(scalar("yes")).isEqualTo(true);
    assertThat(scalar("on")).isEqualTo(true);
    assertThat(scalar("No")).isEqualTo(false);
    assertThat(scalar("True")).isEqualTo(true);
    assertThat(scalar("TRUE")).isEqualTo(true);
    assertThat(scalar("~")).isNull();
    assertThat(scalar("Null")).isNull();
    assertThat(scalar("0644")).isEqualTo(420);
    assertThat(scalar("0xFF")).isEqualTo(255);
    assertThat(scalar("1_000")).isEqualTo(1000);
    assertThat(scalar("'yes'")).isEqualTo("yes");
  }

  /**
   * Jackson's YAML module streams parser events and never composes nodes, so it does not expand
   * anchors and aliases (jackson-dataformats-text#98, same under Jackson 2): the alias reads back
   * as the anchor's name. Templates that need real alias support must go through SnakeYAML first,
   * as Keel's DeliveryConfigYamlParsingFilter does.
   */
  @Test
  void aliasesAreNotExpanded() throws Exception {
    Map<String, Object> doc = yaml().readValue("a: &a [1]\nb: *a\n", MAP);

    assertThat(doc.get("b")).isEqualTo("a");
  }

  @Test
  void emptyValuesAndDuplicateKeysMatchTheDefaultYamlMapper() throws Exception {
    assertThat(yaml().readValue("v:\n", MAP).get("v")).isNull();
    assertThat(yaml().readValue("k: 1\nk: 2\n", MAP).get("k")).isEqualTo(2);
  }

  @Test
  void codePointLimitFromYamlHelperIsEnforced() {
    YamlParserProperties props = new YamlParserProperties();
    props.setCodePointLimit(16);
    ObjectMapper limited = YamlObjectMapperFactory.create(source, new YamlHelper(props));

    assertThatThrownBy(() -> limited.readValue("key: " + "x".repeat(64), MAP))
        .isInstanceOf(JacksonException.class);
  }

  @Test
  void featureFlagsAreInheritedFromTheJsonMapper() {
    ObjectMapper strict =
        source.rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    ObjectMapper derived = YamlObjectMapperFactory.create(strict);

    assertThat(derived.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isTrue();
  }
}
