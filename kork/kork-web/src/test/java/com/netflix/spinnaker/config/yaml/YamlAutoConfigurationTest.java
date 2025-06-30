package com.netflix.spinnaker.config.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

@ExtendWith(SpringExtension.class)
@ImportAutoConfiguration(YamlAutoConfiguration.class)
@EnableConfigurationProperties(YamlParserProperties.class)
@TestPropertySource(
    properties = {
      "spinnaker.yaml.max-aliases-for-collections=55",
      "spinnaker.yaml.code-point-limit=1024"
    })
class YamlAutoConfigurationTest {

  @Autowired Yaml yaml;

  @Test
  public void aliasLimitIsEnforced() {
    String doc = yamlWithNAliases(56);
    assertThatThrownBy(() -> yaml.load(doc))
        .isInstanceOf(YAMLException.class)
        .hasMessage("Number of aliases for non-scalar nodes exceeds the specified max=55");
  }

  @Test
  public void aliasLimitIsNotExceeded() {
    String okString = yamlWithNAliases(50);
    Object result = yaml.load(okString);
    assertThat(result).isNotNull();
  }

  @Test
  public void codePointLimitIsEnforced() {
    // This string has more than 1024 characters
    String bigString = yamlWithNCodePoints(1025);
    assertThatThrownBy(() -> yaml.load(bigString))
        .isInstanceOf(YAMLException.class)
        .hasMessage("The incoming YAML document exceeds the limit: 1024 code points.");
  }

  @Test
  public void codePointLimitIsNotExceeded() {
    String okString = yamlWithNCodePoints(1000);
    Object result = yaml.load(okString);
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
}
