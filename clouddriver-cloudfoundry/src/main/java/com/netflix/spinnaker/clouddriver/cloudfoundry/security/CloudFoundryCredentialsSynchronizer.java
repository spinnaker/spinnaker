/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.security;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.module.CatsModule;
import com.netflix.spinnaker.clouddriver.cloudfoundry.config.CloudFoundryConfigurationProperties;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.CloudFoundryProvider;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.agent.CloudFoundryCachingAgent;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;

public class CloudFoundryCredentialsSynchronizer implements CredentialsInitializerSynchronizable {

  private final CloudFoundryProvider cloudFoundryProvider;
  private final CloudFoundryConfigurationProperties cloudFoundryConfigurationProperties;
  private final AccountCredentialsRepository accountCredentialsRepository;
  private final CatsModule catsModule;
  private final Registry registry;

  public CloudFoundryCredentialsSynchronizer(
      CloudFoundryProvider cloudFoundryProvider,
      CloudFoundryConfigurationProperties cloudFoundryConfigurationProperties,
      AccountCredentialsRepository accountCredentialsRepository,
      CatsModule catsModule,
      Registry registry) {
    this.cloudFoundryProvider = cloudFoundryProvider;
    this.cloudFoundryConfigurationProperties = cloudFoundryConfigurationProperties;
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.catsModule = catsModule;
    this.registry = registry;
  }

  @Override
  @PostConstruct
  public void synchronize() {
    List<String> deletedAccountNames =
        getDeletedAccountNames(accountCredentialsRepository, cloudFoundryConfigurationProperties);

    List<String> changedAccountNames =
        synchronizeRepository(
            cloudFoundryConfigurationProperties.getAccounts(), deletedAccountNames);

    synchronizeAgentCache(changedAccountNames, deletedAccountNames);
  }

  private List<String> synchronizeRepository(
      List<CloudFoundryConfigurationProperties.ManagedAccount> accounts,
      List<String> deletedAccountNames) {

    List<String> changedAccountNames = new ArrayList<>();

    deletedAccountNames.forEach(accountCredentialsRepository::delete);

    accounts.forEach(
        managedAccount -> {
          CloudFoundryCredentials credentials =
              new CloudFoundryCredentials(
                  managedAccount.getName(),
                  managedAccount.getAppsManagerUri(),
                  managedAccount.getMetricsUri(),
                  managedAccount.getApi(),
                  managedAccount.getUser(),
                  managedAccount.getPassword(),
                  managedAccount.getEnvironment());

          AccountCredentials existingCredentials =
              accountCredentialsRepository.getOne(credentials.getName());
          if (existingCredentials != null) {
            if (!existingCredentials.equals(credentials)) {
              accountCredentialsRepository.save(managedAccount.getName(), credentials);

              changedAccountNames.add(managedAccount.getName());
            }
          } else {
            accountCredentialsRepository.save(managedAccount.getName(), credentials);
          }
        });

    return changedAccountNames;
  }

  private void synchronizeAgentCache(
      List<String> changedAccountNames, List<String> deletedAccountNames) {
    ProviderUtils.unscheduleAndDeregisterAgents(changedAccountNames, catsModule);
    ProviderUtils.unscheduleAndDeregisterAgents(deletedAccountNames, catsModule);

    List<Agent> addedAgents = getAddedAgents();

    cloudFoundryProvider.getAgents().addAll(addedAgents);
    ProviderUtils.rescheduleAgents(cloudFoundryProvider, addedAgents);
  }

  private List<String> getDeletedAccountNames(
      AccountCredentialsRepository accountCredentialsRepository,
      CloudFoundryConfigurationProperties cloudFoundryConfigurationProperties) {
    List<String> existingNames =
        accountCredentialsRepository.getAll().stream()
            .map(AccountCredentials::getName)
            .collect(Collectors.toList());

    List<String> newNames =
        cloudFoundryConfigurationProperties.getAccounts().stream()
            .map(CloudFoundryConfigurationProperties.ManagedAccount::getName)
            .collect(Collectors.toList());

    return existingNames.stream()
        .filter(name -> !newNames.contains(name))
        .collect(Collectors.toList());
  }

  private List<Agent> getAddedAgents() {
    Set<String> existingAgentAccountNames =
        ProviderUtils.getScheduledAccounts(cloudFoundryProvider);

    Set<CloudFoundryCredentials> allAccounts =
        ProviderUtils.buildThreadSafeSetOfAccounts(
            accountCredentialsRepository, CloudFoundryCredentials.class);

    return allAccounts.stream()
        .filter(account -> !existingAgentAccountNames.contains(account.getName()))
        .map(
            account ->
                new CloudFoundryCachingAgent(account.getName(), account.getClient(), registry))
        .collect(Collectors.toList());
  }
}
