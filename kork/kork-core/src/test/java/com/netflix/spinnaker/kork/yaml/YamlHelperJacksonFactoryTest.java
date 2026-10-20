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
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.LoadSettings;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
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
  void goldenScalarsMatchTheJackson2ReferenceMapper() throws Exception {
    com.fasterxml.jackson.dataformat.yaml.YAMLMapper jackson2 =
        new com.fasterxml.jackson.dataformat.yaml.YAMLMapper();
    for (String[] row : jackson2Golden()) {
      String actual;
      try {
        Object value = jackson2.readValue("v: " + row[0], Map.class).get("v");
        actual = value == null ? "null" : value.getClass().getSimpleName() + ":" + value;
      } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        actual = "ERR";
      }
      assertThat(actual).as(row[0]).isEqualTo(row[1]);
    }
  }

  @Test
  void numericFormsMatchJackson2AcrossFactoriesAndInputSources() throws Exception {
    com.fasterxml.jackson.dataformat.yaml.YAMLMapper jackson2 =
        new com.fasterxml.jackson.dataformat.yaml.YAMLMapper();
    for (String scalar :
        List.of("+1.", "-1.", "01e3", "+01e3", "-01e-3", "1e_3", "1.5e1_0", ".5e_3")) {
      String doc = "v: " + scalar;
      Map<?, ?> expected = jackson2.readValue(doc, Map.class);
      byte[] bytes = doc.getBytes(StandardCharsets.UTF_8);
      for (ObjectMapper mapper : compatibilityMappers()) {
        assertThat(mapper.readValue(doc, Map.class)).as(scalar).isEqualTo(expected);
        assertThat(mapper.readValue(new StringReader(doc), Map.class))
            .as(scalar)
            .isEqualTo(expected);
        assertThat(mapper.readValue(bytes, Map.class)).as(scalar).isEqualTo(expected);
        assertThat(mapper.readValue(new ByteArrayInputStream(bytes), Map.class))
            .as(scalar)
            .isEqualTo(expected);
      }
    }
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
    assertThat(read(helperMapper, "v: '._'")).isEqualTo("{v=._}");
    assertThat(read(helperMapper, "v: !!str ._")).isEqualTo("{v=._}");
  }

  @Test
  void typedStringsMatchJackson2WithoutChangingTheirSpelling() throws Exception {
    com.fasterxml.jackson.dataformat.yaml.YAMLMapper jackson2 =
        new com.fasterxml.jackson.dataformat.yaml.YAMLMapper();
    for (String scalar :
        List.of(
            "yes",
            "True",
            "0644",
            "0xFF",
            ".5",
            "+1",
            "1_000",
            "07777777777777777777777",
            "+1.",
            "01e3",
            "1e_3")) {
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

  @Test
  void numericConstraintsUseTheOriginalRepresentation() throws Exception {
    com.fasterxml.jackson.dataformat.yaml.YAMLMapper jackson2 =
        new com.fasterxml.jackson.dataformat.yaml.YAMLMapper(
            com.fasterxml.jackson.dataformat.yaml.YAMLFactory.builder()
                .streamReadConstraints(
                    com.fasterxml.jackson.core.StreamReadConstraints.builder()
                        .maxNumberLength(16)
                        .build())
                .build());
    ObjectMapper limited =
        YAMLMapper.builder(
                new Yaml11CompatYAMLFactory(
                    YAMLFactory.builder()
                        .streamReadConstraints(
                            StreamReadConstraints.builder().maxNumberLength(16).build())))
            .build();
    for (String scalar :
        List.of("0x" + "f".repeat(16), "0b" + "1".repeat(16), "0" + "7".repeat(16))) {
      String doc = "v: " + scalar;
      Object expected = jackson2.readValue(doc, Map.class).get("v");
      assertScalarInputs(limited, scalar, expected, jackson2.readValue(doc, StringValue.class).v);
    }
    String doc = "v: " + "0".repeat(16) + ".5";
    assertThatThrownBy(() -> jackson2.readValue(doc, Map.class))
        .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    assertRejectedInputs(limited.readerFor(Map.class), doc);
  }

  @Test
  void radixAndFractionBoundariesMatchJackson2AcrossInputSources() throws Exception {
    com.fasterxml.jackson.dataformat.yaml.YAMLMapper jackson2 =
        new com.fasterxml.jackson.dataformat.yaml.YAMLMapper();
    for (String scalar :
        List.of(
            "0x_", "+0x__", "-0x___", "0b_", "+0b__", "-0b___", "0x_1", "+0b_1", "._5", "-._0")) {
      String doc = "v: " + scalar;
      Object expected = jackson2.readValue(doc, Map.class).get("v");
      String text = jackson2.readValue(doc, StringValue.class).v;
      for (ObjectMapper mapper : compatibilityMappers()) {
        assertScalarInputs(mapper, scalar, expected, text);
      }
    }
  }

  @Test
  void invalidFractionsFailForUntypedAndStringReadsAcrossInputSources() {
    com.fasterxml.jackson.dataformat.yaml.YAMLMapper jackson2 =
        new com.fasterxml.jackson.dataformat.yaml.YAMLMapper();
    for (String scalar : List.of("._", "+._e3", "-.__E03")) {
      String doc = "v: " + scalar;
      assertThatThrownBy(() -> jackson2.readValue(doc, Map.class))
          .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
      assertThatThrownBy(() -> jackson2.readValue(doc, StringValue.class))
          .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
      for (ObjectMapper mapper : compatibilityMappers()) {
        assertRejectedInputs(mapper.readerFor(Map.class), doc);
        assertRejectedInputs(mapper.readerFor(StringValue.class), doc);
      }
    }
  }

  @Test
  void longNonOctalStringsParseWithinTheRuntimeBudget() {
    String scalar = "0".repeat(100_000) + "8";
    List<ObjectMapper> mappers = compatibilityMappers();
    assertTimeoutPreemptively(
        Duration.ofSeconds(10),
        () -> {
          for (ObjectMapper mapper : mappers) {
            assertScalarInputs(mapper, scalar, scalar, scalar);
          }
        });
  }

  @Test
  void implicitScalarResolutionRetainsTheJackson2LengthBoundary() throws Exception {
    com.fasterxml.jackson.dataformat.yaml.YAMLMapper jackson2 =
        new com.fasterxml.jackson.dataformat.yaml.YAMLMapper();
    for (String scalar :
        List.of(
            "0" + "_".repeat(1021) + "1",
            "0" + "_".repeat(1022) + "1",
            "0" + "_".repeat(1023) + "1",
            "0".repeat(1023) + ".5",
            "+" + "0".repeat(1022) + "e3",
            "1".repeat(1025),
            "1".repeat(1023) + ".5",
            "0x" + "f".repeat(1023),
            "0b" + "1".repeat(1023),
            "." + "_".repeat(1024))) {
      String doc = "v: " + scalar;
      Object expected = jackson2.readValue(doc, Map.class).get("v");
      String text = jackson2.readValue(doc, StringValue.class).v;
      for (ObjectMapper mapper : compatibilityMappers()) {
        assertScalarInputs(mapper, scalar, expected, text);
      }
    }
  }

  private List<ObjectMapper> compatibilityMappers() {
    return List.of(
        helperMapper,
        helperMapper.rebuild().build(),
        YAMLMapper.builder(YamlHelper.newYamlFactory()).build());
  }

  private static void assertScalarInputs(
      ObjectMapper mapper, String scalar, Object expected, String text) throws Exception {
    String doc = "v: " + scalar;
    byte[] bytes = doc.getBytes(StandardCharsets.UTF_8);
    assertThat(mapper.readValue(doc, Map.class).get("v")).as(scalar).isEqualTo(expected);
    assertThat(mapper.readValue(new StringReader(doc), Map.class).get("v"))
        .as(scalar)
        .isEqualTo(expected);
    assertThat(mapper.readValue(bytes, Map.class).get("v")).as(scalar).isEqualTo(expected);
    assertThat(mapper.readValue(new ByteArrayInputStream(bytes), Map.class).get("v"))
        .as(scalar)
        .isEqualTo(expected);
    assertThat(mapper.readValue(doc, StringValue.class).v).as(scalar).isEqualTo(text);
    assertThat(mapper.readValue(new StringReader(doc), StringValue.class).v)
        .as(scalar)
        .isEqualTo(text);
    assertThat(mapper.readValue(bytes, StringValue.class).v).as(scalar).isEqualTo(text);
    assertThat(mapper.readValue(new ByteArrayInputStream(bytes), StringValue.class).v)
        .as(scalar)
        .isEqualTo(text);
  }

  private static void assertRejectedInputs(ObjectReader reader, String doc) {
    byte[] bytes = doc.getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(() -> reader.readValue(doc)).as(doc).isInstanceOf(JacksonException.class);
    assertThatThrownBy(() -> reader.readValue(new StringReader(doc)))
        .as(doc)
        .isInstanceOf(JacksonException.class);
    assertThatThrownBy(() -> reader.readValue(bytes)).as(doc).isInstanceOf(JacksonException.class);
    assertThatThrownBy(() -> reader.readValue(new ByteArrayInputStream(bytes)))
        .as(doc)
        .isInstanceOf(JacksonException.class);
  }

  static class StringValue {
    public String v;
  }
}
