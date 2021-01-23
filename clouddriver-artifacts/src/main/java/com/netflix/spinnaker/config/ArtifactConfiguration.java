/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.config;

import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactAccount;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.CredentialsTypeBaseConfiguration;
import com.netflix.spinnaker.credentials.CredentialsTypeProperties;
import com.squareup.okhttp.OkHttpClient;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

@Configuration
@EnableConfigurationProperties
@EnableScheduling
@Component
@ComponentScan("com.netflix.spinnaker.clouddriver.artifacts")
public class ArtifactConfiguration {
  @Bean
  OkHttpClient okHttpClient() {
    return new OkHttpClient();
  }

  @Bean
  public ArtifactCredentialsRepository artifactCredentialsRepository(
      ApplicationContext applicationContext,
      List<CredentialsTypeProperties<? extends ArtifactCredentials, ? extends ArtifactAccount>>
          credentialsTypes,
      List<CredentialsRepository<? extends ArtifactCredentials>> defaultRepositories) {
    List<CredentialsRepository<? extends ArtifactCredentials>> repositories =
        credentialsTypes.stream()
            .map(c -> new CredentialsTypeBaseConfiguration<>(applicationContext, c))
            .peek(CredentialsTypeBaseConfiguration::afterPropertiesSet)
            .map(CredentialsTypeBaseConfiguration::getCredentialsRepository)
            .collect(Collectors.toList());

    repositories.addAll(defaultRepositories);
    return new ArtifactCredentialsRepository(repositories);
  }
}
