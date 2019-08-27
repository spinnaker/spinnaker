/*
 * Copyright 2015 Google, Inc.
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

import com.netflix.spinnaker.cats.module.CatsModule;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.health.KubernetesHealthIndicator;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.KubernetesUtil;
import com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.KubernetesV1Provider;
import com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.KubernetesV1ProviderSynchronizable;
import com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.agent.KubernetesV1CachingAgentDispatcher;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.KubernetesV2Provider;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.KubernetesV2ProviderSynchronizable;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgentDispatcher;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.GlobalKubernetesKindRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKindProperties;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties
@EnableScheduling
@ConditionalOnProperty("kubernetes.enabled")
@ComponentScan({"com.netflix.spinnaker.clouddriver.kubernetes"})
public class KubernetesConfiguration {
  @Bean
  @ConfigurationProperties("kubernetes")
  public KubernetesConfigurationProperties kubernetesConfigurationProperties() {
    return new KubernetesConfigurationProperties();
  }

  @Bean
  public KubernetesHealthIndicator kubernetesHealthIndicator(
      AccountCredentialsProvider accountCredentialsProvider) {
    return new KubernetesHealthIndicator(accountCredentialsProvider);
  }

  @Bean
  public KubernetesUtil kubernetesUtil() {
    return new KubernetesUtil();
  }

  @Bean
  public KubernetesV1Provider kubernetesV1Provider(
      KubernetesCloudProvider kubernetesCloudProvider) {
    return new KubernetesV1Provider(
        kubernetesCloudProvider, Collections.newSetFromMap(new ConcurrentHashMap<>()));
  }

  @Bean
  public KubernetesV2Provider kubernetesV2Provider() {
    return new KubernetesV2Provider();
  }

  @Bean
  public GlobalKubernetesKindRegistry globalKubernetesKindRegistry() {
    return new GlobalKubernetesKindRegistry(KubernetesKindProperties.getGlobalKindProperties());
  }

  @Bean
  public KubernetesV2ProviderSynchronizable kubernetesV2ProviderSynchronizable(
      KubernetesV2Provider kubernetesV2Provider,
      AccountCredentialsRepository accountCredentialsRepository,
      KubernetesV2CachingAgentDispatcher kubernetesV2CachingAgentDispatcher,
      KubernetesConfigurationProperties kubernetesConfigurationProperties,
      KubernetesNamedAccountCredentials.CredentialFactory credentialFactory,
      KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap,
      CatsModule catsModule) {
    return new KubernetesV2ProviderSynchronizable(
        kubernetesV2Provider,
        accountCredentialsRepository,
        kubernetesV2CachingAgentDispatcher,
        kubernetesConfigurationProperties,
        credentialFactory,
        kubernetesSpinnakerKindMap,
        catsModule);
  }

  @Bean
  public KubernetesV1ProviderSynchronizable kubernetesV1ProviderSynchronizable(
      KubernetesV1Provider kubernetesV1Provider,
      AccountCredentialsRepository accountCredentialsRepository,
      KubernetesV1CachingAgentDispatcher kubernetesV1CachingAgentDispatcher,
      KubernetesConfigurationProperties kubernetesConfigurationProperties,
      KubernetesNamedAccountCredentials.CredentialFactory credentialFactory,
      KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap,
      CatsModule catsModule) {
    return new KubernetesV1ProviderSynchronizable(
        kubernetesV1Provider,
        accountCredentialsRepository,
        kubernetesV1CachingAgentDispatcher,
        kubernetesConfigurationProperties,
        credentialFactory,
        kubernetesSpinnakerKindMap,
        catsModule);
  }
}
