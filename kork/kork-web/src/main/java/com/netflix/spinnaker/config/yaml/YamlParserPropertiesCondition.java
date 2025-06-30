package com.netflix.spinnaker.config.yaml;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * A custom {@link org.springframework.context.annotation.Condition} that determines whether the
 * {@link org.yaml.snakeyaml.Yaml} bean should be created based on the values of {@link
 * YamlParserProperties}.
 *
 * <p>This condition returns {@code true} only if at least one of the following properties is
 * greater than zero:
 *
 * <ul>
 *   <li>{@code maxAliasesForCollections}
 *   <li>{@code codePointLimit}
 * </ul>
 *
 * <p>This allows conditional creation of the SnakeYAML {@code Yaml} bean only when it is configured
 * with meaningful parsing limits.
 *
 * <p>Used in combination with {@link org.springframework.context.annotation.Conditional} on a
 * {@code @Bean} method.
 *
 * @see YamlParserProperties
 */
public class YamlParserPropertiesCondition extends SpringBootCondition {

  @Override
  public ConditionOutcome getMatchOutcome(
      ConditionContext context, AnnotatedTypeMetadata metadata) {
    String prefix = "spinnaker.yaml.";

    Integer maxAliases =
        context
            .getEnvironment()
            .getProperty(prefix + "max-aliases-for-collections", Integer.class, 0);
    Integer codePointLimit =
        context.getEnvironment().getProperty(prefix + "code-point-limit", Integer.class, 0);

    boolean valid = (maxAliases > 0 || codePointLimit > 0);

    if (valid) {
      return ConditionOutcome.match("Valid values found in YamlParserProperties");
    } else {
      return ConditionOutcome.noMatch("YamlParserProperties values are not > 0");
    }
  }
}
