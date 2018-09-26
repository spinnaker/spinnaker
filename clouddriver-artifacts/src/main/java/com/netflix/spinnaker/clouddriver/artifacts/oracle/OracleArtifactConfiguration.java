/*
 * Copyright (c) 2017, 2018, Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.artifacts.oracle;

import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Configuration
@ConditionalOnProperty("artifacts.oracle.enabled")
@EnableScheduling
@Slf4j
public class OracleArtifactConfiguration {
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @ConfigurationProperties("artifacts.oracle")
  OracleArtifactProviderProperties oracleArtifactProviderProperties() {
    return new OracleArtifactProviderProperties();
  }

  @Autowired
  OracleArtifactProviderProperties oracleArtifactProviderProperties;

  @Autowired
  ArtifactCredentialsRepository artifactCredentialsRepository;

  @Bean
  List<? extends OracleArtifactCredentials> oracleArtifactCredentials(String clouddriverUserAgentApplicationName) {
    return oracleArtifactProviderProperties.getAccounts()
            .stream()
            .map(a -> {
              try {
                OracleArtifactCredentials c = new OracleArtifactCredentials(clouddriverUserAgentApplicationName, a);
                artifactCredentialsRepository.save(c);
                return c;
              } catch (IOException | GeneralSecurityException e) {
                log.warn("Failure instantiating oracle artifact account {}: ", a, e);
                return null;
              }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
  }
}
