package com.netflix.spinnaker.kork.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.representer.Representer;

@SpringBootTest(classes = YamlHelperTest.TestConfig.class)
@TestPropertySource(
    properties = {
      "spinnaker.yaml.max-aliases-for-collections=55",
      "spinnaker.yaml.code-point-limit=1024"
    })
class YamlHelperTest {

  static DumperOptions DUMPER_OPTIONS;

  static LoaderOptions LOADER_OPTIONS;

  @BeforeAll
  public static void setUp() {
    DUMPER_OPTIONS = new DumperOptions();
    DUMPER_OPTIONS.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    DUMPER_OPTIONS.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);

    LOADER_OPTIONS = new LoaderOptions();
    LOADER_OPTIONS.setMaxAliasesForCollections(1000);
  }

  @Test
  public void aliasLimitIsEnforced() {
    String doc = yamlWithNAliases(56);
    assertThatThrownBy(() -> YamlHelper.newYaml().load(doc))
        .isInstanceOf(YAMLException.class)
        .hasMessage("Number of aliases for non-scalar nodes exceeds the specified max=55");
  }

  @Test
  public void aliasLimitIsNotExceeded() {
    String okString = yamlWithNAliases(50);
    Object result = YamlHelper.newYaml().load(okString);
    assertThat(result).isNotNull();
  }

  @Test
  public void codePointLimitIsEnforced() {
    // This string has more than 1024 characters
    String bigString = yamlWithNCodePoints(1025);
    assertThatThrownBy(() -> YamlHelper.newYaml().load(bigString))
        .isInstanceOf(YAMLException.class)
        .hasMessage("The incoming YAML document exceeds the limit: 1024 code points.");
  }

  @Test
  public void codePointLimitIsNotExceeded() {
    String okString = yamlWithNCodePoints(1000);
    Object result = YamlHelper.newYaml().load(okString);
    assertThat(result).isNotNull();
  }

  @Test
  public void aliasLimitIsEnforcedYamlSafeConstructor() {
    String doc = yamlWithNAliases(56);
    assertThatThrownBy(() -> YamlHelper.newYamlSafeConstructor().load(doc))
        .isInstanceOf(YAMLException.class)
        .hasMessage("Number of aliases for non-scalar nodes exceeds the specified max=55");
  }

  @Test
  public void aliasLimitIsNotExceededYamlSafeConstructor() {
    String okString = yamlWithNAliases(50);
    Object result = YamlHelper.newYamlSafeConstructor().load(okString);
    assertThat(result).isNotNull();
  }

  @Test
  public void codePointLimitIsEnforcedYamlSafeConstructor() {
    // This string has more than 1024 characters
    String bigString = yamlWithNCodePoints(1025);
    assertThatThrownBy(() -> YamlHelper.newYamlSafeConstructor().load(bigString))
        .isInstanceOf(YAMLException.class)
        .hasMessage("The incoming YAML document exceeds the limit: 1024 code points.");
  }

  @Test
  public void codePointLimitIsNotExceededYamlSafeConstructor() {
    String okString = yamlWithNCodePoints(1000);
    Object result = YamlHelper.newYamlSafeConstructor().load(okString);
    assertThat(result).isNotNull();
  }

  @Test
  public void aliasLimitIsEnforcedYamlDumperOptions() {
    String doc = yamlWithNAliases(56);
    assertThatThrownBy(() -> YamlHelper.newYamlDumperOptions(DUMPER_OPTIONS).load(doc))
        .isInstanceOf(YAMLException.class)
        .hasMessage("Number of aliases for non-scalar nodes exceeds the specified max=55");
  }

  @Test
  public void aliasLimitIsNotExceededYamlDumperOptions() {
    String okString = yamlWithNAliases(50);
    Object result = YamlHelper.newYamlDumperOptions(DUMPER_OPTIONS).load(okString);
    assertThat(result).isNotNull();
  }

  @Test
  public void codePointLimitIsEnforcedYamlDumperOptions() {
    // This string has more than 1024 characters
    String bigString = yamlWithNCodePoints(1025);
    assertThatThrownBy(() -> YamlHelper.newYamlDumperOptions(DUMPER_OPTIONS).load(bigString))
        .isInstanceOf(YAMLException.class)
        .hasMessage("The incoming YAML document exceeds the limit: 1024 code points.");
  }

  @Test
  public void codePointLimitIsNotExceededYamlDumperOptions() {
    String okString = yamlWithNCodePoints(1000);
    Object result = YamlHelper.newYamlDumperOptions(DUMPER_OPTIONS).load(okString);
    assertThat(result).isNotNull();
  }

  @Test
  public void aliasLimitIsEnforcedYamlLoaderOptions() {
    String doc = yamlWithNAliases(56);
    assertThatThrownBy(() -> YamlHelper.newYamlLoaderOptions(LOADER_OPTIONS).load(doc))
        .isInstanceOf(YAMLException.class)
        .hasMessage("Number of aliases for non-scalar nodes exceeds the specified max=55");
  }

  @Test
  public void aliasLimitIsNotExceededYamlLoaderOptions() {
    String okString = yamlWithNAliases(50);
    Object result = YamlHelper.newYamlLoaderOptions(LOADER_OPTIONS).load(okString);
    assertThat(result).isNotNull();
  }

  @Test
  public void codePointLimitIsEnforcedYamlLoaderOptions() {
    // This string has more than 1024 characters
    String bigString = yamlWithNCodePoints(1025);
    assertThatThrownBy(() -> YamlHelper.newYamlLoaderOptions(LOADER_OPTIONS).load(bigString))
        .isInstanceOf(YAMLException.class)
        .hasMessage("The incoming YAML document exceeds the limit: 1024 code points.");
  }

  @Test
  public void codePointLimitIsNotExceededYamlLoaderOptions() {
    String okString = yamlWithNCodePoints(1000);
    Object result = YamlHelper.newYamlLoaderOptions(LOADER_OPTIONS).load(okString);
    assertThat(result).isNotNull();
  }

  @Test
  public void aliasLimitIsEnforcedYamlRepresenter() {
    String doc = yamlWithNAliases(56);
    assertThatThrownBy(
            () ->
                YamlHelper.newYamlRepresenter(new Constructor(Object.class), new Representer())
                    .load(doc))
        .isInstanceOf(YAMLException.class)
        .hasMessage("Number of aliases for non-scalar nodes exceeds the specified max=55");
  }

  @Test
  public void aliasLimitIsNotExceededYamlRepresenter() {
    String okString = yamlWithNAliases(50);
    Object result =
        YamlHelper.newYamlRepresenter(new Constructor(Object.class), new Representer())
            .load(okString);
    assertThat(result).isNotNull();
  }

  @Test
  public void codePointLimitIsEnforcedYamlRepresenter() {
    // This string has more than 1024 characters
    String bigString = yamlWithNCodePoints(1025);
    assertThatThrownBy(
            () ->
                YamlHelper.newYamlRepresenter(new Constructor(Object.class), new Representer())
                    .load(bigString))
        .isInstanceOf(YAMLException.class)
        .hasMessage("The incoming YAML document exceeds the limit: 1024 code points.");
  }

  @Test
  public void codePointLimitIsNotExceededYamlRepresenter() {
    String okString = yamlWithNCodePoints(1000);
    Object result =
        YamlHelper.newYamlRepresenter(new Constructor(Object.class), new Representer())
            .load(okString);
    assertThat(result).isNotNull();
  }

  private String yamlWithNAliases(int nAliases) {
    StringBuilder sb = new StringBuilder();
    sb.append("defaults: &default\n  a: 1\n  b: 2\n");

    for (int i = 0; i < nAliases; i++) {
      sb.append("alias").append(i).append(": *default\n");
    }

    return sb.toString();
  }

  private String yamlWithNCodePoints(int nCodePoints) {
    StringBuilder sb = new StringBuilder();
    sb.append("value: ");
    for (int i = 0; i < nCodePoints; i++) {
      sb.append("a");
    }
    return sb.toString();
  }

  @Configuration
  @EnableConfigurationProperties(YamlParserProperties.class)
  @ComponentScan(basePackageClasses = YamlHelper.class)
  static class TestConfig {}
}
