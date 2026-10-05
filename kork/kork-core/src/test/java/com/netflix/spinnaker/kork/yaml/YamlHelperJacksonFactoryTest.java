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
package com.netflix.spinnaker.kork.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.LoadSettings;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;

class YamlHelperJacksonFactoryTest {

  private static final List<String> DOCUMENTS =
      List.of(
          "v:",
          "v: ",
          "v: null",
          "v: true",
          "v: 1e3",
          "dup: 1\ndup: 2",
          "list:\n- a\n-\n- c",
          "nested:\n  empty:\n  other: x");

  private final ObjectMapper helperMapper =
      YAMLMapper.builder(new YamlHelper(new YamlParserProperties()).yamlFactory()).build();

  private final ObjectMapper plainMapper = YAMLMapper.builder().build();

  private static String read(ObjectMapper mapper, String doc) {
    try {
      return String.valueOf(mapper.readValue(doc, Object.class));
    } catch (JacksonException e) {
      return "ERROR " + e.getClass().getSimpleName();
    }
  }

  /** The reason this helper exists: hand-built LoadSettings change these two cases. */
  @Test
  void handBuiltLoadSettingsDiffersFromThePlainMapper() {
    ObjectMapper handBuilt =
        YAMLMapper.builder(
                YAMLFactory.builder()
                    .loadSettings(
                        LoadSettings.builder()
                            .setMaxAliasesForCollections(50)
                            .setCodePointLimit(3_145_728)
                            .build())
                    .build())
            .build();

    assertThat(read(handBuilt, "v:")).isEqualTo("{v=}");
    assertThat(read(handBuilt, "dup: 1\ndup: 2")).startsWith("ERROR");
  }

  @Test
  void helperFactoryReadsLikeThePlainMapper() {
    for (String doc : DOCUMENTS) {
      assertThat(read(helperMapper, doc)).as(doc).isEqualTo(read(plainMapper, doc));
    }
    assertThat(read(helperMapper, "v:")).isEqualTo("{v=null}");
    assertThat(read(helperMapper, "dup: 1\ndup: 2")).isEqualTo("{dup=2}");
  }

  @Test
  void codePointLimitIsStillEnforced() {
    YamlParserProperties props = new YamlParserProperties();
    props.setCodePointLimit(16);
    ObjectMapper limited = YAMLMapper.builder(new YamlHelper(props).yamlFactory()).build();

    assertThatThrownBy(() -> limited.readValue("key: " + "x".repeat(64), Object.class))
        .isInstanceOf(JacksonException.class);
  }

  private static String describe(ObjectMapper mapper, String scalar) {
    try {
      Object value = ((Map<?, ?>) mapper.readValue("v: " + scalar, Object.class)).get("v");
      return value == null ? "null" : value.getClass().getSimpleName() + ":" + value;
    } catch (JacksonException e) {
      return "ERR";
    }
  }

  private static List<String[]> jackson2Golden() throws Exception {
    List<String[]> rows = new ArrayList<>();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                YamlHelperJacksonFactoryTest.class.getResourceAsStream(
                    "/yaml/jackson2-scalar-golden.tsv"),
                StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.isEmpty() && !line.startsWith("#")) {
          rows.add(line.split("\t", 2));
        }
      }
    }
    return rows;
  }

  /**
   * The golden file holds what a stock Jackson 2.21 YAMLMapper returned for each plain scalar: YAML
   * 1.1 booleans and nulls, octal/hex/binary, underscores, signs and short floats.
   */
  @Test
  void plainScalarsReadLikeJackson2() throws Exception {
    List<String[]> golden = jackson2Golden();
    assertThat(golden).hasSizeGreaterThan(100);

    List<String> mismatches = new ArrayList<>();
    for (String[] row : golden) {
      String actual = describe(helperMapper, row[0]);
      if (!actual.equals(row[1])) {
        mismatches.add(row[0] + ": expected " + row[1] + " but was " + actual);
      }
    }
    assertThat(mismatches).isEmpty();
  }

  @Test
  void jackson3AloneDisagreesWithJackson2OnMany() throws Exception {
    long different =
        jackson2Golden().stream().filter(r -> !describe(plainMapper, r[0]).equals(r[1])).count();

    assertThat(different).isGreaterThan(40);
  }

  @Test
  void quotedAndTaggedScalarsAreNotRewritten() {
    assertThat(read(helperMapper, "v: 'yes'")).isEqualTo("{v=yes}");
    assertThat(read(helperMapper, "v: \"0644\"")).isEqualTo("{v=0644}");
    assertThat(read(helperMapper, "v: !!str ~")).isEqualTo("{v=~}");
    assertThat(read(helperMapper, "yes: x")).isEqualTo("{yes=x}");
  }

  @Test
  void typedStringsMatchJackson2WithoutChangingTheirSpelling() throws Exception {
    com.fasterxml.jackson.dataformat.yaml.YAMLMapper jackson2 =
        new com.fasterxml.jackson.dataformat.yaml.YAMLMapper();
    for (String scalar :
        List.of("yes", "True", "0644", "0xFF", ".5", "+1", "1_000", "07777777777777777777777")) {
      String doc = "v: " + scalar;
      assertThat(helperMapper.readValue(doc, StringValue.class).v)
          .as(scalar)
          .isEqualTo(jackson2.readValue(doc, StringValue.class).v);
    }
  }

  @Test
  void readerAndByteInputsAndRebuiltMappersKeepScalarCompatibility() throws Exception {
    byte[] bytes = "v: 0644".getBytes(StandardCharsets.UTF_8);
    assertThat(read(helperMapper.rebuild().build(), "v: 0644")).isEqualTo("{v=420}");
    assertThat(helperMapper.readValue(bytes, Map.class).get("v")).isEqualTo(420);
    assertThat(helperMapper.readValue(new ByteArrayInputStream(bytes), Map.class).get("v"))
        .isEqualTo(420);
    assertThat(helperMapper.readValue(new StringReader("v: yes"), Map.class).get("v"))
        .isEqualTo(true);
  }

  @Test
  void radixNumbersRespectNumberLengthLimitsBeforeConversion() {
    ObjectMapper limited =
        YAMLMapper.builder(
                new Yaml11CompatYAMLFactory(
                    YAMLFactory.builder()
                        .streamReadConstraints(
                            StreamReadConstraints.builder().maxNumberLength(16).build())))
            .build();
    assertThatThrownBy(() -> limited.readValue("v: 0x" + "f".repeat(1000), Object.class))
        .isInstanceOf(JacksonException.class);
  }

  static class StringValue {
    public String v;
  }

  @Test
  void yaml11ScalarsCanBeDisabled() {
    YamlParserProperties props = new YamlParserProperties();
    props.setYaml11Scalars(false);
    ObjectMapper yaml12 = YAMLMapper.builder(new YamlHelper(props).yamlFactory()).build();

    assertThat(read(yaml12, "v: yes")).isEqualTo("{v=yes}");
  }
}
