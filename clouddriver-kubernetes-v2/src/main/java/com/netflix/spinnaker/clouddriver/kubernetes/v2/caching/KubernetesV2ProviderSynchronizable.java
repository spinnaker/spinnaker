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
import com.netflix.spinnaker.cats.module.CatsModule;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgentDispatcher;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.security.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KubernetesV2ProviderSynchronizable implements CredentialsInitializerSynchronizable {

  private final KubernetesV2Provider kubernetesV2Provider;
  private final AccountCredentialsRepository accountCredentialsRepository;
  private final KubernetesV2CachingAgentDispatcher kubernetesV2CachingAgentDispatcher;
  private final KubernetesConfigurationProperties kubernetesConfigurationProperties;
  private final KubernetesV2Credentials.Factory credentialFactory;
  private final CatsModule catsModule;

  public KubernetesV2ProviderSynchronizable(
      KubernetesV2Provider kubernetesV2Provider,
      AccountCredentialsRepository accountCredentialsRepository,
      KubernetesV2CachingAgentDispatcher kubernetesV2CachingAgentDispatcher,
      KubernetesConfigurationProperties kubernetesConfigurationProperties,
      KubernetesV2Credentials.Factory credentialFactory,
      CatsModule catsModule) {
    this.kubernetesV2Provider = kubernetesV2Provider;
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.kubernetesV2CachingAgentDispatcher = kubernetesV2CachingAgentDispatcher;
    this.kubernetesConfigurationProperties = kubernetesConfigurationProperties;
    this.credentialFactory = credentialFactory;
    this.catsModule = catsModule;
  }

  @PostConstruct
  public void setup() {
    Set<KubernetesNamedAccountCredentials<KubernetesV2Credentials>> allAccounts =
        kubernetesConfigurationProperties.getAccounts().stream()
            .filter(a -> ProviderVersion.v2.equals(a.getProviderVersion()))
            .map(
                managedAccount ->
                    new KubernetesNamedAccountCredentials<>(managedAccount, credentialFactory))
            .peek(a -> accountCredentialsRepository.save(a.getName(), a))
            .collect(Collectors.toSet());

    synchronizeKubernetesV2Provider(allAccounts);
  }

  @Override
  public void synchronize() {
    Set<String> newAndChangedAccounts = synchronizeAccountCredentials();

    // we only want to initialize caching agents for new or updated accounts
    Set<KubernetesNamedAccountCredentials<KubernetesV2Credentials>> allAccounts =
        ProviderUtils.buildThreadSafeSetOfAccounts(
                accountCredentialsRepository,
                KubernetesNamedAccountCredentials.class,
                ProviderVersion.v2)
            .stream()
            .map(c -> (KubernetesNamedAccountCredentials<KubernetesV2Credentials>) c)
            .filter(account -> newAndChangedAccounts.contains(account.getName()))
            .collect(Collectors.toSet());

    if (allAccounts.size() < 1) {
      log.info(
          "No changes detected to V2 Kubernetes accounts. Skipping caching agent synchronization.");
      return;
    }

    log.info("Synchronizing {} caching agents for V2 Kubernetes accounts.", allAccounts.size());
    synchronizeKubernetesV2Provider(allAccounts);
  }

  private Set<String> synchronizeAccountCredentials() {
    List<String> deletedAccounts = getDeletedAccountNames();
    List<String> changedAccounts = new ArrayList<>();
    Set<String> newAndChangedAccounts = new HashSet<>();

    deletedAccounts.forEach(accountCredentialsRepository::delete);

    kubernetesConfigurationProperties.getAccounts().stream()
        .filter(a -> ProviderVersion.v2.equals(a.getProviderVersion()))
        .map(
            managedAccount -> {
              KubernetesNamedAccountCredentials credentials =
                  new KubernetesNamedAccountCredentials<>(managedAccount, credentialFactory);

              AccountCredentials existingCredentials =
                  accountCredentialsRepository.getOne(managedAccount.getName());

              if (existingCredentials == null) {
                // account didn't previously exist
                newAndChangedAccounts.add(managedAccount.getName());
              } else if (!existingCredentials.equals(credentials)) {
                // account exists but has changed
                changedAccounts.add(managedAccount.getName());
                newAndChangedAccounts.add(managedAccount.getName());
              } else {
                // Current credentials may contain memoized namespaces, we should keep if the
                // definition has not changed
                return null;
              }
              return credentials;
            })
        .filter(Objects::nonNull)
        .forEach(a -> saveToCredentialsRepository(a));

    ProviderUtils.unscheduleAndDeregisterAgents(deletedAccounts, catsModule);
    ProviderUtils.unscheduleAndDeregisterAgents(changedAccounts, catsModule);

    return newAndChangedAccounts;
  }

  /**
   * Validate and save credentials to repository
   *
   * @param account
   */
  private void saveToCredentialsRepository(KubernetesNamedAccountCredentials account) {
    // Attempt to get namespaces to resolve any connectivity error without blocking /credentials
    List<String> namespaces = account.getCredentials().getDeclaredNamespaces();
    if (namespaces.isEmpty()) {
      log.warn(
          "New or modified account {} did not return any namespace and could be unreachable or misconfigured",
          account.getName());
    }
    accountCredentialsRepository.save(account.getName(), account);
  }

  private List<String> getDeletedAccountNames() {
    List<String> existingNames =
        accountCredentialsRepository.getAll().stream()
            .filter(
                (AccountCredentials c) -> KubernetesCloudProvider.ID.equals(c.getCloudProvider()))
            .filter((AccountCredentials c) -> ProviderVersion.v2.equals(c.getProviderVersion()))
            .map(AccountCredentials::getName)
            .collect(Collectors.toList());

    Set<String> newNames =
        kubernetesConfigurationProperties.getAccounts().stream()
            .map(KubernetesConfigurationProperties.ManagedAccount::getName)
            .collect(Collectors.toSet());

    return existingNames.stream()
        .filter(name -> !newNames.contains(name))
        .collect(Collectors.toList());
  }

  private void synchronizeKubernetesV2Provider(
      Set<KubernetesNamedAccountCredentials<KubernetesV2Credentials>> allAccounts) {

    try {
      for (KubernetesNamedAccountCredentials<KubernetesV2Credentials> credentials : allAccounts) {
        List<Agent> newlyAddedAgents =
            kubernetesV2CachingAgentDispatcher.buildAllCachingAgents(credentials).stream()
                .map(c -> (Agent) c)
                .collect(Collectors.toList());

        log.info("Adding {} agents for account {}", newlyAddedAgents.size(), credentials.getName());

        kubernetesV2Provider.addAllAgents(newlyAddedAgents);
      }
    } catch (Exception e) {
      log.warn("Error encountered scheduling new agents -- using old agent set instead", e);
      kubernetesV2Provider.clearNewAgentSet();
    }

    // If there is an agent scheduler, then this provider has been through the AgentController in
    // the past.
    // In that case, we need to do the scheduling here (because accounts have been added to a
    // running system).
    if (kubernetesV2Provider.getAgentScheduler() != null) {
      ProviderUtils.rescheduleAgents(
          kubernetesV2Provider, new ArrayList<>(kubernetesV2Provider.getNextAgentSet()));
    }

    kubernetesV2Provider.switchToNewAgents();
  }
}
