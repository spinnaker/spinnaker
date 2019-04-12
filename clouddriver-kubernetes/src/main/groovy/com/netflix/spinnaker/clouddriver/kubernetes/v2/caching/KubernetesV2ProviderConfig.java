/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper;
import com.netflix.spinnaker.cats.thread.NamedThreadFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgentDispatcher;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;
import com.netflix.spinnaker.clouddriver.security.ProviderVersion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Scope;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

@Configuration
@Slf4j
class KubernetesV2ProviderConfig {
  @Bean
  @DependsOn("kubernetesNamedAccountCredentials")
  KubernetesV2Provider kubernetesV2Provider(KubernetesCloudProvider kubernetesCloudProvider,
      AccountCredentialsRepository accountCredentialsRepository,
      KubernetesV2CachingAgentDispatcher kubernetesV2CachingAgentDispatcher,
      KubernetesResourcePropertyRegistry kubernetesResourcePropertyRegistry
  ) {
    this.kubernetesV2Provider = new KubernetesV2Provider();
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.kubernetesV2CachingAgentDispatcher = kubernetesV2CachingAgentDispatcher;
    this.kubernetesResourcePropertyRegistry = kubernetesResourcePropertyRegistry;

    ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory(KubernetesV2ProviderConfig.class.getSimpleName()));

    synchronizeKubernetesV2Provider(kubernetesV2Provider, accountCredentialsRepository);

    return kubernetesV2Provider;
  }

  private KubernetesV2Provider kubernetesV2Provider;
  private AccountCredentialsRepository accountCredentialsRepository;
  private KubernetesV2CachingAgentDispatcher kubernetesV2CachingAgentDispatcher;
  private KubernetesResourcePropertyRegistry kubernetesResourcePropertyRegistry;

  @Bean
  KubernetesV2ProviderSynchronizerTypeWrapper kubernetesV2ProviderSynchronizerTypeWrapper() {
    return new KubernetesV2ProviderSynchronizerTypeWrapper();
  }

  class KubernetesV2ProviderSynchronizerTypeWrapper implements ProviderSynchronizerTypeWrapper {
    @Override
    public Class getSynchronizerType() {
      return KubernetesV2ProviderSynchronizer.class;
    }
  }

  class KubernetesV2ProviderSynchronizer {}

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  KubernetesV2ProviderSynchronizer synchronizeKubernetesV2Provider(
      KubernetesV2Provider kubernetesV2Provider,
      AccountCredentialsRepository accountCredentialsRepository
  ) {
    Set<KubernetesNamedAccountCredentials> allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, KubernetesNamedAccountCredentials.class, ProviderVersion.v2);

    try {
      for (KubernetesNamedAccountCredentials credentials : allAccounts) {
        KubernetesV2Credentials v2Credentials = (KubernetesV2Credentials) credentials.getCredentials();
        v2Credentials.getCustomResources().forEach(cr -> {
          try {
            KubernetesResourceProperties properties = KubernetesResourceProperties.fromCustomResource(cr);
            kubernetesResourcePropertyRegistry.registerAccountProperty(credentials.getName(), properties);
          } catch (Exception e) {
            log.warn("Error encountered registering {}: ", cr, e);
          }
        });
        v2Credentials.initialize();

        List<Agent> newlyAddedAgents = kubernetesV2CachingAgentDispatcher.buildAllCachingAgents(credentials)
            .stream()
            .map(c -> (Agent) c)
            .collect(Collectors.toList());

        log.info("Adding {} agents for account {}", newlyAddedAgents.size(), credentials.getName());

        kubernetesV2Provider.addAllAgents(newlyAddedAgents);
      }
    } catch (Exception e) {
      log.warn("Error encountered scheduling new agents -- using old agent set instead", e);
      kubernetesV2Provider.clearNewAgentSet();
      return new KubernetesV2ProviderSynchronizer();
    }

    // If there is an agent scheduler, then this provider has been through the AgentController in the past.
    // In that case, we need to do the scheduling here (because accounts have been added to a running system).
    if (kubernetesV2Provider.getAgentScheduler() != null) {
      ProviderUtils.rescheduleAgents(kubernetesV2Provider, new ArrayList<>(kubernetesV2Provider.getNextAgentSet()));
    }

    kubernetesV2Provider.switchToNewAgents();

    return new KubernetesV2ProviderSynchronizer();
  }
}
