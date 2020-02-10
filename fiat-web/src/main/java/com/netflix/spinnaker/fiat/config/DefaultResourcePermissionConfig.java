/*
 * Copyright 2019 Google, LLC
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

package com.netflix.spinnaker.fiat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.fiat.model.resources.Account;
import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.model.resources.BuildService;
import com.netflix.spinnaker.fiat.providers.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
class DefaultResourcePermissionConfig {

  @Bean
  @ConditionalOnProperty(
      value = "auth.permissions.source.account.resource.enabled",
      matchIfMissing = true)
  @Order
  ResourcePermissionSource<Account> accountResourcePermissionSource() {
    return new AccessControlledResourcePermissionSource<>();
  }

  @Bean
  @ConditionalOnProperty(
      value = "auth.permissions.provider.account",
      havingValue = "default",
      matchIfMissing = true)
  public ResourcePermissionProvider<Account> defaultAccountPermissionProvider(
      ResourcePermissionSource<Account> accountResourcePermissionSource) {
    return new DefaultResourcePermissionProvider<>(accountResourcePermissionSource);
  }

  @Bean
  @ConditionalOnProperty(
      value = "auth.permissions.source.application.resource.enabled",
      matchIfMissing = true)
  @Order
  ResourcePermissionSource<Application> applicationResourcePermissionSource() {
    return new ApplicationResourcePermissionSource();
  }

  @Bean
  @ConditionalOnProperty(
      value = "auth.permissions.provider.application",
      havingValue = "default",
      matchIfMissing = true)
  public ResourcePermissionProvider<Application> defaultApplicationPermissionProvider(
      ResourcePermissionSource<Application> applicationResourcePermissionSource) {
    return new DefaultResourcePermissionProvider<>(applicationResourcePermissionSource);
  }

  @Bean
  @ConditionalOnProperty(
      value = "auth.permissions.source.build-service.resource.enabled",
      matchIfMissing = true)
  @Order
  ResourcePermissionSource<BuildService> buildServiceResourcePermissionSource() {
    return new AccessControlledResourcePermissionSource<>();
  }

  @Bean
  @ConditionalOnProperty(
      value = "auth.permissions.provider.build-service",
      havingValue = "default",
      matchIfMissing = true)
  public ResourcePermissionProvider<BuildService> defaultBuildServicePermissionProvider(
      ResourcePermissionSource<BuildService> buildServiceResourcePermissionSource) {
    return new DefaultResourcePermissionProvider<>(buildServiceResourcePermissionSource);
  }

  @Bean
  @ConditionalOnProperty("auth.permissions.source.application.prefix.enabled")
  @ConfigurationProperties("auth.permissions.source.application.prefix")
  ResourcePermissionSource<Application> applicationPrefixResourcePermissionSource() {
    return new ResourcePrefixPermissionSource<Application>();
  }

  @Bean
  @ConditionalOnProperty(value = "auth.permissions.source.application.chaos-monkey.enabled")
  public ResourcePermissionSource<Application> chaosMonkeyApplicationResourcePermissionSource(
      ObjectMapper objectMapper, FiatServerConfigurationProperties configurationProperties) {
    return new ChaosMonkeyApplicationResourcePermissionSource(
        configurationProperties.getChaosMonkey().getRoles(), objectMapper);
  }
}
