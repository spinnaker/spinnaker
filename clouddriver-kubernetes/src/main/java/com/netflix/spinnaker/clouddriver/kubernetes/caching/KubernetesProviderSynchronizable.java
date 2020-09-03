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

package com.netflix.spinnaker.clouddriver.kubernetes.caching;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.module.CatsModule;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCachingAgentDispatcher;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.security.*;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesProviderSynchronizable implements CredentialsInitializerSynchronizable {
  private static final Logger log = LoggerFactory.getLogger(KubernetesProviderSynchronizable.class);

  private final KubernetesProvider kubernetesProvider;
  private final AccountCredentialsRepository accountCredentialsRepository;
  private final KubernetesCachingAgentDispatcher kubernetesCachingAgentDispatcher;
  private final KubernetesConfigurationProperties kubernetesConfigurationProperties;
  private final KubernetesCredentials.Factory credentialFactory;
  private final CatsModule catsModule;

  public KubernetesProviderSynchronizable(
      KubernetesProvider kubernetesProvider,
      AccountCredentialsRepository accountCredentialsRepository,
      KubernetesCachingAgentDispatcher kubernetesCachingAgentDispatcher,
      KubernetesConfigurationProperties kubernetesConfigurationProperties,
      KubernetesCredentials.Factory credentialFactory,
      CatsModule catsModule) {
    this.kubernetesProvider = kubernetesProvider;
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.kubernetesCachingAgentDispatcher = kubernetesCachingAgentDispatcher;
    this.kubernetesConfigurationProperties = kubernetesConfigurationProperties;
    this.credentialFactory = credentialFactory;
    this.catsModule = catsModule;
  }

  @PostConstruct
  public void setup() {
    Set<KubernetesNamedAccountCredentials> allAccounts =
        kubernetesConfigurationProperties.getAccounts().stream()
            .map(
                managedAccount ->
                    new KubernetesNamedAccountCredentials(managedAccount, credentialFactory))
            .peek(a -> accountCredentialsRepository.save(a.getName(), a))
            .collect(Collectors.toSet());

    synchronizeKubernetesProvider(allAccounts);
  }

  @Override
  public void synchronize() {
    Set<String> newAndChangedAccountNames = synchronizeAccountCredentials();

    // we only want to initialize caching agents for new or updated accounts
    Set<KubernetesNamedAccountCredentials> newAndChangedAccounts =
        ProviderUtils.buildThreadSafeSetOfAccounts(
                accountCredentialsRepository, KubernetesNamedAccountCredentials.class)
            .stream()
            .filter(account -> newAndChangedAccountNames.contains(account.getName()))
            .collect(Collectors.toSet());

    if (newAndChangedAccounts.size() < 1) {
      log.info("No new or changed Kubernetes accounts. Skipping caching agent synchronization.");
      return;
    }

    synchronizeKubernetesProvider(newAndChangedAccounts);
  }

  private Set<String> synchronizeAccountCredentials() {
    List<String> deletedAccounts = getDeletedAccountNames();
    Set<String> newAndChangedAccounts = new HashSet<>();

    deleteAccounts(deletedAccounts);

    kubernetesConfigurationProperties.getAccounts().stream()
        .map(
            managedAccount -> {
              KubernetesNamedAccountCredentials credentials =
                  new KubernetesNamedAccountCredentials(managedAccount, credentialFactory);

              AccountCredentials<?> existingCredentials =
                  accountCredentialsRepository.getOne(managedAccount.getName());

              if (existingCredentials == null || !existingCredentials.equals(credentials)) {
                // account didn't previously exist or exists but has changed
                newAndChangedAccounts.add(managedAccount.getName());
              } else {
                // Current credentials may contain memoized namespaces, we should keep if the
                // definition has not changed
                return null;
              }
              return credentials;
            })
        .filter(Objects::nonNull)
        .forEach(this::saveToCredentialsRepository);

    return newAndChangedAccounts;
  }

  private void deleteAccounts(List<String> deletedAccounts) {
    log.info(
        "{} accounts were deleted and need to be removed from repository and caching agents",
        deletedAccounts.size());
    deletedAccounts.forEach(
        accountCredentialsRepository::delete); // delete from endpoint /credentials
    ProviderUtils.unscheduleAndDeregisterAgents(
        deletedAccounts, catsModule); // delete caching agents
  }

  /** Validate and save credentials to repository */
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
            .filter(c -> KubernetesCloudProvider.ID.equals(c.getCloudProvider()))
            // Using a lambda here causes a warning about raw types; the real fix would be to have
            // the credentials repository return AccountCredentials<?> instead of an
            // AccountCredentials.
            .map(c -> c.getName())
            .collect(Collectors.toList());

    Set<String> newNames =
        kubernetesConfigurationProperties.getAccounts().stream()
            .map(KubernetesConfigurationProperties.ManagedAccount::getName)
            .collect(Collectors.toSet());

    return existingNames.stream()
        .filter(name -> !newNames.contains(name))
        .collect(Collectors.toList());
  }

  private void synchronizeKubernetesProvider(
      Set<KubernetesNamedAccountCredentials> newAndChangedAccounts) {

    log.info(
        "Synchronizing caching agents for {} new or changed Kubernetes accounts.",
        newAndChangedAccounts.size());

    Set<String> stagedAccountNames = new HashSet<>();

    for (KubernetesNamedAccountCredentials credentials : newAndChangedAccounts) {
      try {
        List<Agent> newlyAddedAgents =
            kubernetesCachingAgentDispatcher.buildAllCachingAgents(credentials).stream()
                .map(c -> (Agent) c)
                .collect(Collectors.toList());

        log.info("Adding {} agents for account {}", newlyAddedAgents.size(), credentials.getName());

        kubernetesProvider.stageAgents(newlyAddedAgents);
        stagedAccountNames.add(credentials.getName());
      } catch (Exception e) {
        log.warn(
            "Error encountered scheduling new agents for account {} -- using its old agent set instead",
            credentials.getName(),
            e);
      }
    }

    // Remove existing agents belonging to changed accounts
    ProviderUtils.unscheduleAndDeregisterAgents(stagedAccountNames, catsModule);

    // If there is an agent scheduler, then this provider has been through the AgentController in
    // the past.
    // In that case, we need to do the scheduling here (because accounts have been added to a
    // running system).
    if (kubernetesProvider.getAgentScheduler() != null) {
      ProviderUtils.rescheduleAgents(
          kubernetesProvider, new ArrayList<>(kubernetesProvider.getStagedAgents()));
    }

    kubernetesProvider.promoteStagedAgents();
  }
}
