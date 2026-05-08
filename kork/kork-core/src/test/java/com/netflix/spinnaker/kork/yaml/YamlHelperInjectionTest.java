package com.netflix.spinnaker.kork.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;
import java.util.Map;
import javax.script.ScriptEngineManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.yaml.snakeyaml.constructor.ConstructorException;
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
   * Demonstrates arbitrary object instantiation attack using YAML tags.
   *
   * <p>This test shows that newYaml() allows instantiation of arbitrary Java classes using YAML
   * tags (!!java.net.URL syntax). An attacker could use this to:
   *
   * <ul>
   *   <li>Instantiate dangerous classes like ScriptEngineManager, URLClassLoader
   *   <li>Trigger SSRF attacks by instantiating URL with attacker-controlled endpoints
   *   <li>Execute arbitrary code through deserialization gadgets
   * </ul>
   *
   * <p>This is a critical security vulnerability (CVE-2022-1471 class).
   */
  @Test
  public void demonstratesArbitraryObjectInstantiationWithNewYaml() {
    // YAML that instantiates a java.net.URL object
    String maliciousYaml =
        """
            !!java.net.URL ["http://malicious.example.com/"]
            """;

    // newYaml() allows arbitrary object instantiation - VULNERABLE
    Object result = YamlHelper.newYaml().load(maliciousYaml);

    // Attacker successfully instantiated a URL object
    assertThat(result).isInstanceOf(URL.class);
    assertThat(result.toString()).isEqualTo("http://malicious.example.com/");
  }

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
        .isInstanceOf(ConstructorException.class)
        .hasMessageContaining(
            "could not determine a constructor for the tag tag:yaml.org,2002:java.net.URL");
  }

  /**
   * Demonstrates ScriptEngineManager instantiation attack.
   *
   * <p>ScriptEngineManager is a particularly dangerous class to instantiate from untrusted YAML
   * because:
   *
   * <ul>
   *   <li>Its constructor triggers service discovery via ServiceLoader
   *   <li>This can lead to arbitrary code execution if attacker controls the classpath
   *   <li>Part of common RCE gadget chains in Java deserialization attacks
   * </ul>
   */
  @Test
  public void demonstratesScriptEngineManagerInstantiation() {
    String maliciousYaml = """
            !!javax.script.ScriptEngineManager []
            """;

    // newYaml() allows instantiation of ScriptEngineManager - VULNERABLE
    Object result = YamlHelper.newYaml().load(maliciousYaml);

    assertThat(result).isInstanceOf(ScriptEngineManager.class);
  }

  /** Demonstrates that SafeConstructor blocks ScriptEngineManager instantiation. */
  @Test
  public void safeConstructorBlocksScriptEngineManagerInstantiation() {
    String maliciousYaml = """
            !!javax.script.ScriptEngineManager []
            """;

    // newYamlSafeConstructor() blocks ScriptEngineManager instantiation - SECURE
    assertThatThrownBy(() -> YamlHelper.newYamlSafeConstructor().load(maliciousYaml))
        .isInstanceOf(ConstructorException.class)
        .hasMessageContaining(
            "could not determine a constructor for the tag tag:yaml.org,2002:javax.script.ScriptEngineManager");
  }

  /**
   * Demonstrates nested object instantiation attack.
   *
   * <p>Attackers can nest malicious object instantiation within seemingly innocent YAML structures
   * to bypass simple pattern matching or WAF rules.
   */
  @Test
  public void demonstratesNestedObjectInstantiationAttack() {
    String maliciousYaml =
        """
            application:
              name: myapp
              config:
                url: !!java.net.URL ["http://attacker.com/exfiltrate"]
            """;

    // newYaml() allows nested arbitrary objects - VULNERABLE
    Object result = YamlHelper.newYaml().load(maliciousYaml);

    assertThat(result).isInstanceOf(Map.class);
    Map<?, ?> map = (Map<?, ?>) result;

    // Extract nested malicious object
    @SuppressWarnings("unchecked")
    Map<String, Object> application = (Map<String, Object>) map.get("application");
    @SuppressWarnings("unchecked")
    Map<String, Object> config = (Map<String, Object>) application.get("config");
    Object url = config.get("url");

    // Attacker successfully instantiated URL deep in the structure
    assertThat(url).isInstanceOf(URL.class);
    assertThat(url.toString()).isEqualTo("http://attacker.com/exfiltrate");
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
        .isInstanceOf(ConstructorException.class)
        .hasMessageContaining(
            "could not determine a constructor for the tag tag:yaml.org,2002:java.net.URL");
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
        .isInstanceOf(ConstructorException.class)
        .hasMessageContaining(
            "could not determine a constructor for the tag tag:yaml.org,2002:java.net.URL");
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

    // Both methods should block this due to alias limits
    assertThatThrownBy(() -> YamlHelper.newYaml().load(bomb))
        .isInstanceOf(YAMLException.class)
        .hasMessageContaining("Number of aliases for non-scalar nodes exceeds the specified max");

    assertThatThrownBy(() -> YamlHelper.newYamlSafeConstructor().load(bomb))
        .isInstanceOf(YAMLException.class)
        .hasMessageContaining("Number of aliases for non-scalar nodes exceeds the specified max");
  }

  /**
   * Demonstrates tag injection in map keys.
   *
   * <p>Attackers can use YAML tags in unexpected places like map keys to trigger object
   * instantiation.
   */
  @Test
  public void demonstratesTagInjectionInMapKeys() {
    String maliciousYaml =
        """
            ? !!java.net.URL ["http://evil.com/"]
            : value
            """;

    // newYaml() allows object instantiation in map keys - VULNERABLE
    Object result = YamlHelper.newYaml().load(maliciousYaml);

    assertThat(result).isInstanceOf(Map.class);
    Map<?, ?> map = (Map<?, ?>) result;

    // First key should be a URL object
    Object firstKey = map.keySet().iterator().next();
    assertThat(firstKey).isInstanceOf(URL.class);
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
        .isInstanceOf(ConstructorException.class)
        .hasMessageContaining(
            "could not determine a constructor for the tag tag:yaml.org,2002:java.net.URL");
  }

  @Configuration
  @EnableConfigurationProperties(YamlParserProperties.class)
  @ComponentScan(basePackageClasses = YamlHelper.class)
  static class TestConfig {}
}
