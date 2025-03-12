/*
 * Copyright 2022 OpsMx Inc.
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

import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunCloudProvider;
import com.netflix.spinnaker.clouddriver.cloudrun.config.CloudrunConfigurationProperties;
import com.netflix.spinnaker.clouddriver.cloudrun.config.CloudrunCredentialsConfiguration;
import com.netflix.spinnaker.clouddriver.cloudrun.health.CloudrunHealthIndicator;
import com.netflix.spinnaker.clouddriver.cloudrun.provider.CloudrunProvider;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties
@EnableScheduling
@ConditionalOnProperty("cloudrun.enabled")
@ComponentScan("com.netflix.spinnaker.clouddriver.cloudrun")
@Import(CloudrunCredentialsConfiguration.class)
public class CloudrunConfiguration {
  @Bean
  @ConfigurationProperties("cloudrun")
  public CloudrunConfigurationProperties cloudrunConfigurationProperties() {
    return new CloudrunConfigurationProperties();
  }

  @Bean
  public CloudrunHealthIndicator cloudrunHealthIndicator() {
    return new CloudrunHealthIndicator();
  }

  @Bean
  public CloudrunProvider cloudrunProvider(CloudrunCloudProvider cloudProvider) {
    return new CloudrunProvider(cloudProvider);
  }

  @Bean
  @ConditionalOnMissingBean(
      value = CloudrunNamedAccountCredentials.class,
      parameterizedContainer = CredentialsRepository.class)
  public CredentialsRepository<CloudrunNamedAccountCredentials> credentialsRepository(
      CredentialsLifecycleHandler<CloudrunNamedAccountCredentials> eventHandler) {
    return new MapBackedCredentialsRepository<>(CloudrunCloudProvider.ID, eventHandler);
  }
}
