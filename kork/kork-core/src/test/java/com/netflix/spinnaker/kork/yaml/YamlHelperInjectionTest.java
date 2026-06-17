package com.netflix.spinnaker.kork.yaml;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Security tests demonstrating YAML injection vulnerabilities.
 *
 * <p>These tests demonstrate potential injection attacks against YamlHelper, specifically showing
 * how the regular Constructor allows arbitrary object instantiation which can lead to Remote Code
 * Execution (RCE) vulnerabilities.
 *
 * <p>The tests compare the behavior of:
 *
 * <ul>
 *   <li>{@code newYaml()} - Uses Constructor, vulnerable to arbitrary object instantiation
 *   <li>{@code newYamlSafeConstructor()} - Uses SafeConstructor, blocks arbitrary object
 *       instantiation
 * </ul>
 */
@SpringBootTest(classes = YamlHelperInjectionTest.TestConfig.class)
@TestPropertySource(
    properties = {"snakeyaml.max-aliases-for-collections=50", "snakeyaml.code-point-limit=10000"})
class YamlHelperInjectionTest {

  /**
   * Demonstrates that SafeConstructor blocks arbitrary object instantiation.
   *
   * <p>This test shows that newYamlSafeConstructor() correctly prevents the same attack by
   * rejecting YAML tags that attempt to instantiate arbitrary classes.
   */
  @Test
  public void safeConstructorBlocksArbitraryObjectInstantiation() {
    String maliciousYaml =
        """
            !!java.net.URL ["http://malicious.example.com/"]
            """;

    // newYamlSafeConstructor() blocks arbitrary object instantiation - SECURE
    assertThatThrownBy(() -> YamlHelper.newYamlSafeConstructor().load(maliciousYaml))
        .isInstanceOf(YAMLException.class)
        .satisfies(
            ex ->
                org.assertj.core.api.Assertions.assertThat(ex.getMessage())
                    .matches(
                        msg ->
                            msg.contains(
                                    "Global tag is not allowed: tag:yaml.org,2002:java.net.URL")
                                || msg.contains(
                                    "could not determine a constructor for the tag tag:yaml.org,2002:java.net.URL")));
  }

  /** Demonstrates that SafeConstructor blocks ScriptEngineManager instantiation. */
  @Test
  public void safeConstructorBlocksScriptEngineManagerInstantiation() {
    String maliciousYaml = """
            !!javax.script.ScriptEngineManager []
            """;

    // newYamlSafeConstructor() blocks ScriptEngineManager instantiation - SECURE
    assertThatThrownBy(() -> YamlHelper.newYamlSafeConstructor().load(maliciousYaml))
        .isInstanceOf(YAMLException.class)
        .satisfies(
            ex ->
                org.assertj.core.api.Assertions.assertThat(ex.getMessage())
                    .matches(
                        msg ->
                            msg.contains(
                                    "Global tag is not allowed: tag:yaml.org,2002:javax.script.ScriptEngineManager")
                                || msg.contains(
                                    "could not determine a constructor for the tag tag:yaml.org,2002:javax.script.ScriptEngineManager")));
  }

  /** Demonstrates that SafeConstructor blocks nested object instantiation. */
  @Test
  public void safeConstructorBlocksNestedObjectInstantiationAttack() {
    String maliciousYaml =
        """
            application:
              name: myapp
              config:
                url: !!java.net.URL ["http://attacker.com/exfiltrate"]
            """;

    // newYamlSafeConstructor() blocks nested arbitrary objects - SECURE
    assertThatThrownBy(() -> YamlHelper.newYamlSafeConstructor().load(maliciousYaml))
        .isInstanceOf(YAMLException.class)
        .satisfies(
            ex ->
                org.assertj.core.api.Assertions.assertThat(ex.getMessage())
                    .matches(
                        msg ->
                            msg.contains(
                                    "Global tag is not allowed: tag:yaml.org,2002:java.net.URL")
                                || msg.contains(
                                    "could not determine a constructor for the tag tag:yaml.org,2002:java.net.URL")));
  }

  /**
   * Demonstrates that newYamlDumperOptions also uses SafeConstructor and is secure.
   *
   * <p>Verifies that the security-enhanced methods consistently use SafeConstructor.
   */
  @Test
  public void newYamlDumperOptionsBlocksArbitraryObjectInstantiation() {
    String maliciousYaml =
        """
            !!java.net.URL ["http://malicious.example.com/"]
            """;

    // newYamlDumperOptions() uses SafeConstructor - SECURE
    assertThatThrownBy(
            () ->
                YamlHelper.newYamlDumperOptions(new org.yaml.snakeyaml.DumperOptions())
                    .load(maliciousYaml))
        .isInstanceOf(YAMLException.class)
        .satisfies(
            ex ->
                org.assertj.core.api.Assertions.assertThat(ex.getMessage())
                    .matches(
                        msg ->
                            msg.contains(
                                    "Global tag is not allowed: tag:yaml.org,2002:java.net.URL")
                                || msg.contains(
                                    "could not determine a constructor for the tag tag:yaml.org,2002:java.net.URL")));
  }

  /**
   * Demonstrates YAML bomb (Billion Laughs) attack vector.
   *
   * <p>While YamlHelper does have alias limits, this test shows the attack pattern. A YAML bomb
   * uses entity expansion to cause exponential memory consumption:
   *
   * <pre>
   * a: &a ["lol","lol","lol","lol","lol","lol","lol","lol","lol"]
   * b: &b [*a,*a,*a,*a,*a,*a,*a,*a,*a]
   * c: &c [*b,*b,*b,*b,*b,*b,*b,*b,*b]
   * </pre>
   *
   * <p>This creates 9^3 = 729 copies of the string "lol". With more levels, memory usage grows
   * exponentially.
   */
  @Test
  public void demonstratesYamlBombAttackIsBlocked() {
    // Create a YAML bomb that exceeds the 50 alias limit configured in test properties
    StringBuilder yamlBomb = new StringBuilder();
    yamlBomb.append("a: &a [1,2,3,4,5]\n");

    // Create 51 references to exceed the limit of 50
    for (int i = 0; i < 51; i++) {
      yamlBomb.append("b").append(i).append(": *a\n");
    }

    String bomb = yamlBomb.toString();

    assertThatThrownBy(() -> YamlHelper.newYamlSafeConstructor().load(bomb))
        .isInstanceOf(YAMLException.class)
        .hasMessageContaining("Number of aliases for non-scalar nodes exceeds the specified max");
  }

  /** Demonstrates that SafeConstructor blocks tag injection in map keys. */
  @Test
  public void safeConstructorBlocksTagInjectionInMapKeys() {
    String maliciousYaml =
        """
            ? !!java.net.URL ["http://evil.com/"]
            : value
            """;

    // newYamlSafeConstructor() blocks object instantiation in keys - SECURE
    assertThatThrownBy(() -> YamlHelper.newYamlSafeConstructor().load(maliciousYaml))
        .isInstanceOf(YAMLException.class)
        .satisfies(
            ex ->
                org.assertj.core.api.Assertions.assertThat(ex.getMessage())
                    .matches(
                        msg ->
                            msg.contains(
                                    "Global tag is not allowed: tag:yaml.org,2002:java.net.URL")
                                || msg.contains(
                                    "could not determine a constructor for the tag tag:yaml.org,2002:java.net.URL")));
  }

  @Configuration
  @EnableConfigurationProperties(YamlParserProperties.class)
  @ComponentScan(basePackageClasses = YamlHelper.class)
  static class TestConfig {}
}
