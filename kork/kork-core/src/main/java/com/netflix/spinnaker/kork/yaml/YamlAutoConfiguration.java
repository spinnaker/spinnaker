package com.netflix.spinnaker.kork.yaml;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(YamlParserProperties.class)
public class YamlAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public YamlHelper yamlHelper(YamlParserProperties properties) {
    return new YamlHelper(properties);
  }
}
