/*
 * Copyright 2019 Google, Inc.
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
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.KubernetesV2Provider;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.KubernetesV2ProviderSynchronizable;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgentDispatcher;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKindProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.model.ManifestProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.model.NoopManifestProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.GlobalKubernetesKindRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
public class KubernetesV2Configuration {
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
      KubernetesV2Credentials.Factory credentialFactory,
      CatsModule catsModule) {
    return new KubernetesV2ProviderSynchronizable(
        kubernetesV2Provider,
        accountCredentialsRepository,
        kubernetesV2CachingAgentDispatcher,
        kubernetesConfigurationProperties,
        credentialFactory,
        catsModule);
  }

  @Bean
  @ConditionalOnMissingBean(ManifestProvider.class)
  public ManifestProvider noopManifestProvider() {
    return new NoopManifestProvider();
  }
}
