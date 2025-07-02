package com.netflix.spinnaker.kork.yaml;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

@AutoConfiguration
@EnableConfigurationProperties(YamlParserProperties.class)
@Slf4j
public class YamlAutoConfiguration {

  /**
   * Creates and configures a {@link org.yaml.snakeyaml.Yaml} bean using the provided {@link
   * YamlParserProperties}.
   *
   * <p>This bean is only created if no other {@code Yaml} bean is already defined in the Spring
   * context and if the {@link YamlParserPropertiesCondition} condition is met.
   *
   * <p>The {@link LoaderOptions} is configured based on the values provided in {@link
   * YamlParserProperties}. Specifically:
   *
   * <ul>
   *   <li>If {@code maxAliasesForCollections} > 0, it sets the maximum number of aliases allowed
   *       for collections.
   *   <li>If {@code codePointLimit} > 0, it sets a limit on the total number of Unicode code
   *       points.
   * </ul>
   *
   * @param props the YAML parser configuration properties
   * @return a configured {@link org.yaml.snakeyaml.Yaml} instance
   * @see YamlParserProperties
   * @see YamlParserPropertiesCondition
   */
  @Bean
  @ConditionalOnMissingBean
  @Conditional(YamlParserPropertiesCondition.class)
  public Yaml yaml(YamlParserProperties props) {
    log.info(
        "Creating SnakeYAML bean with MaxAliasesForCollections={} and CodePointLimit={}",
        props.getMaxAliasesForCollections(),
        props.getCodePointLimit());

    LoaderOptions opts = new LoaderOptions();

    if (props.getMaxAliasesForCollections() > 0) {
      opts.setMaxAliasesForCollections(props.getMaxAliasesForCollections());
    }

    if (props.getCodePointLimit() > 0) {
      opts.setCodePointLimit(props.getCodePointLimit());
    }

    return new Yaml(opts);
  }
}
