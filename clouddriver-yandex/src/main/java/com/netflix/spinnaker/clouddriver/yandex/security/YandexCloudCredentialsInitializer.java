/*
 * Copyright 2020 YANDEX LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.clouddriver.yandex.security;

import com.google.common.base.MoreObjects;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import com.netflix.spinnaker.clouddriver.yandex.security.config.YandexConfigurationProperties;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import yandex.cloud.sdk.ChannelFactory;
import yandex.cloud.sdk.ServiceFactory;
import yandex.cloud.sdk.auth.Auth;

@Configuration
@EnableConfigurationProperties
public class YandexCloudCredentialsInitializer implements CredentialsInitializerSynchronizable {

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @ConfigurationProperties("yandex")
  public YandexConfigurationProperties yandexCloudAccountConfig() {
    return new YandexConfigurationProperties();
  }

  @Bean
  public List<YandexCloudCredentials> yandexCloudCredentials(
      YandexConfigurationProperties properties, AccountCredentialsRepository repository) {
    return properties.getAccounts().stream()
        .map(YandexCloudCredentialsInitializer::convertToCredentials)
        .peek(cred -> repository.save(cred.getName(), cred))
        .collect(Collectors.toList());
  }

  @NotNull
  private static YandexCloudCredentials convertToCredentials(
      YandexConfigurationProperties.Account account) {
    YandexCloudCredentials credentials = new YandexCloudCredentials();
    credentials.setFolder(account.getFolder());
    credentials.setName(account.getName());
    credentials.setEnvironment(
        MoreObjects.firstNonNull(account.getEnvironment(), account.getName()));
    credentials.setAccountType(
        MoreObjects.firstNonNull(account.getAccountType(), account.getName()));
    credentials.setServiceFactory(makeJDKConfig(account));
    return credentials;
  }

  private static ServiceFactory makeJDKConfig(YandexConfigurationProperties.Account account) {
    return ServiceFactory.builder()
        .endpoint(
            account.getEndpoint() != null ? account.getEndpoint() : ChannelFactory.DEFAULT_ENDPOINT)
        .credentialProvider(Auth.apiKeyBuilder().fromFile(Paths.get(account.getJsonPath())))
        .requestTimeout(Duration.ofMinutes(1))
        .build();
  }
}
