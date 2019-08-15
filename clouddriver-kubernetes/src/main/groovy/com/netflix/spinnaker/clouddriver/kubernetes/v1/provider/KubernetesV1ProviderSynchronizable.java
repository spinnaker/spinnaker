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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.provider;

import com.netflix.spinnaker.cats.module.CatsModule;
import com.netflix.spinnaker.cats.thread.NamedThreadFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.KubernetesCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.agent.KubernetesV1CachingAgentDispatcher;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;
import com.netflix.spinnaker.clouddriver.security.ProviderVersion;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KubernetesV1ProviderSynchronizable implements CredentialsInitializerSynchronizable {

  private KubernetesV1Provider kubernetesV1Provider;
  private AccountCredentialsRepository accountCredentialsRepository;
  private KubernetesV1CachingAgentDispatcher kubernetesV1CachingAgentDispatcher;
  private KubernetesConfigurationProperties kubernetesConfigurationProperties;
  private KubernetesNamedAccountCredentials.CredentialFactory credentialFactory;
  private KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap;
  private CatsModule catsModule;

  public KubernetesV1ProviderSynchronizable(
      KubernetesV1Provider kubernetesV1Provider,
      AccountCredentialsRepository accountCredentialsRepository,
      KubernetesV1CachingAgentDispatcher kubernetesV1CachingAgentDispatcher,
      KubernetesConfigurationProperties kubernetesConfigurationProperties,
      KubernetesNamedAccountCredentials.CredentialFactory credentialFactory,
      KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap,
      CatsModule catsModule) {
    this.kubernetesV1Provider = kubernetesV1Provider;
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.kubernetesV1CachingAgentDispatcher = kubernetesV1CachingAgentDispatcher;
    this.kubernetesConfigurationProperties = kubernetesConfigurationProperties;
    this.credentialFactory = credentialFactory;
    this.kubernetesSpinnakerKindMap = kubernetesSpinnakerKindMap;
    this.catsModule = catsModule;

    ScheduledExecutorService poller =
        Executors.newSingleThreadScheduledExecutor(
            new NamedThreadFactory(KubernetesV1ProviderSynchronizable.class.getSimpleName()));
  }

  @Override
  @PostConstruct
  public void synchronize() {
    Set<String> newAndChangedAccounts = synchronizeAccountCredentials();

    Set<KubernetesNamedAccountCredentials> allAccounts =
        ProviderUtils.buildThreadSafeSetOfAccounts(
                accountCredentialsRepository,
                KubernetesNamedAccountCredentials.class,
                ProviderVersion.v1)
            .stream()
            .filter(account -> newAndChangedAccounts.contains(account.getName()))
            .collect(Collectors.toSet());

    if (allAccounts.size() < 1) {
      log.info(
          "No changes detected to V1 Kubernetes accounts. Skipping caching agent synchronization.");
      return;
    }

    log.info("Synchronizing {} caching agents for V1 Kubernetes accounts.", allAccounts.size());
    synchronizeKubernetesV1Provider(allAccounts);
  }

  private Set<String> synchronizeAccountCredentials() {
    List<String> deletedAccounts = getDeletedAccountNames();
    List<String> changedAccounts = new ArrayList<>();
    Set<String> newAndChangedAccounts = new HashSet<>();

    deletedAccounts.stream().forEach(accountCredentialsRepository::delete);

    kubernetesConfigurationProperties.getAccounts().stream()
        .filter(a -> ProviderVersion.v1.equals(a.getProviderVersion()))
        .forEach(
            managedAccount -> {
              KubernetesNamedAccountCredentials credentials =
                  new KubernetesNamedAccountCredentials(
                      managedAccount, kubernetesSpinnakerKindMap, credentialFactory);

              AccountCredentials existingCredentials =
                  accountCredentialsRepository.getOne(managedAccount.getName());
              if (existingCredentials == null) {
                newAndChangedAccounts.add(managedAccount.getName());
              } else if (!existingCredentials.equals(credentials)) {
                changedAccounts.add(managedAccount.getName());
                newAndChangedAccounts.add(managedAccount.getName());
              }

              accountCredentialsRepository.save(managedAccount.getName(), credentials);
            });

    ProviderUtils.unscheduleAndDeregisterAgents(deletedAccounts, catsModule);
    ProviderUtils.unscheduleAndDeregisterAgents(changedAccounts, catsModule);

    return newAndChangedAccounts;
  }

  private List<String> getDeletedAccountNames() {
    Set<String> existingNames =
        accountCredentialsRepository.getAll().stream()
            .filter(c -> KubernetesCloudProvider.ID.equals(c.getCloudProvider()))
            .filter(c -> ProviderVersion.v1.equals(c.getProviderVersion()))
            .map(it -> it.getName())
            .collect(Collectors.toSet());

    Set<String> newNames =
        kubernetesConfigurationProperties.getAccounts().stream()
            .map(it -> it.getName())
            .collect(Collectors.toSet());

    return existingNames.stream()
        .filter(name -> !newNames.contains(name))
        .collect(Collectors.toList());
  }

  private void synchronizeKubernetesV1Provider(Set<KubernetesNamedAccountCredentials> allAccounts) {

    kubernetesV1Provider.getAgents().clear();

    for (KubernetesNamedAccountCredentials credentials : allAccounts) {
      Collection<KubernetesCachingAgent> newlyAddedAgents =
          kubernetesV1CachingAgentDispatcher.buildAllCachingAgents(credentials);

      log.info("Adding {} agents for account {}", newlyAddedAgents.size(), credentials.getName());

      // If there is an agent scheduler, then this provider has been through the AgentController in
      // the past.
      // In that case, we need to do the scheduling here (because accounts have been added to a
      // running system).
      if (kubernetesV1Provider.getAgentScheduler() != null) {
        ProviderUtils.rescheduleAgents(kubernetesV1Provider, new ArrayList<>(newlyAddedAgents));
      }

      kubernetesV1Provider.getAgents().addAll(newlyAddedAgents);
    }
  }
}
