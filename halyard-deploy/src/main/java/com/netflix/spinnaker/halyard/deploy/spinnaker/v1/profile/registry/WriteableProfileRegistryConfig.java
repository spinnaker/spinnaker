package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.registry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnExpression("${spinnaker.config.input.writer:false}")
@EnableConfigurationProperties(WriteableProfileRegistryProperties.class)
@Slf4j
public class WriteableProfileRegistryConfig {
  @Bean
  public WriteableProfileRegistry defaultWriteableProfileRegistry(WriteableProfileRegistryProperties properties) {
    return new WriteableProfileRegistry(properties);
  }
}

