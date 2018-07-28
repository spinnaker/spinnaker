/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.artifacts.bitbucket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository;
import com.squareup.okhttp.OkHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Configuration
@ConditionalOnProperty("artifacts.bitbucket.enabled")
@EnableScheduling
@Slf4j
public class BitbucketArtifactConfiguration {
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @ConfigurationProperties("artifacts.bitbucket")
  BitbucketArtifactProviderProperties bitbucketArtifactProviderProperties() {
    return new BitbucketArtifactProviderProperties();
  }

  @Autowired
  BitbucketArtifactProviderProperties bitbucketArtifactProviderProperties;

  @Autowired
  ArtifactCredentialsRepository artifactCredentialsRepository;

  @Autowired
  ObjectMapper objectMapper;

  @Bean
  OkHttpClient bitbucketOkHttpClient() {
    return new OkHttpClient();
  }

  @Bean
  List<? extends BitbucketArtifactCredentials> bitbucketArtifactCredentials(OkHttpClient bitbucketOkHttpClient) {
    return bitbucketArtifactProviderProperties.getAccounts()
      .stream()
      .map(a -> {
        try {
          BitbucketArtifactCredentials c = new BitbucketArtifactCredentials(a, bitbucketOkHttpClient, objectMapper);
          artifactCredentialsRepository.save(c);
          return c;
        } catch (Exception e) {
          log.warn("Failure instantiating Bitbucket artifact account {}: ", a, e);
          return null;
        }
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }
}
