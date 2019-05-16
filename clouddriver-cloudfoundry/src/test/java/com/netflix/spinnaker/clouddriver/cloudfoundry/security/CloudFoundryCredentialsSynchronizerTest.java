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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.*;
import com.netflix.spinnaker.cats.module.CatsModule;
import com.netflix.spinnaker.cats.module.CatsModuleAware;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import com.netflix.spinnaker.clouddriver.cloudfoundry.config.CloudFoundryConfigurationProperties;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.CloudFoundryProvider;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.agent.CloudFoundryCachingAgent;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CloudFoundryCredentialsSynchronizerTest {
  private CloudFoundryCredentialsSynchronizer synchronizer;

  private final CloudFoundryConfigurationProperties configurationProperties =
      new CloudFoundryConfigurationProperties();
  private final AccountCredentialsRepository repository =
      new MapBackedAccountCredentialsRepository();

  private final CloudFoundryProvider provider = new CloudFoundryProvider(new ArrayList<>());
  private final TestAgentScheduler scheduler = new TestAgentScheduler();

  private final CatsModule catsModule = mock(CatsModule.class);
  private final ProviderRegistry providerRegistry = mock(ProviderRegistry.class);
  private final Registry registry = mock(Registry.class);

  @BeforeEach
  void setUp() {
    when(catsModule.getProviderRegistry()).thenReturn(providerRegistry);
    when(providerRegistry.getProviders()).thenReturn(Collections.singletonList(provider));

    provider.setAgentScheduler(scheduler);

    synchronizer =
        new CloudFoundryCredentialsSynchronizer(
            provider, configurationProperties, repository, catsModule, registry);
  }

  @Test
  void synchronize() {
    repository.save("to-be-changed", createCredentials("to-be-changed"));
    repository.save("unchanged2", createCredentials("unchanged2"));
    repository.save("unchanged3", createCredentials("unchanged3"));
    repository.save("to-be-deleted", createCredentials("to-be-deleted"));

    loadProviderFromRepository();

    CloudFoundryConfigurationProperties.ManagedAccount changedAccount =
        createAccount("to-be-changed");
    changedAccount.setPassword("newpassword");

    configurationProperties.setAccounts(
        Arrays.asList(
            createAccount("unchanged2"),
            createAccount("unchanged3"),
            createAccount("added"),
            changedAccount));

    synchronizer.synchronize();

    assertThat(repository.getAll())
        .extracting(AccountCredentials::getName)
        .containsExactlyInAnyOrder("unchanged2", "unchanged3", "added", "to-be-changed");

    assertThat(ProviderUtils.getScheduledAccounts(provider))
        .containsExactlyInAnyOrder("unchanged2", "unchanged3", "added", "to-be-changed");

    assertThat(scheduler.getScheduledAccountNames())
        .containsExactlyInAnyOrder("added", "to-be-changed");
    assertThat(scheduler.getUnscheduledAccountNames())
        .containsExactlyInAnyOrder("to-be-changed", "to-be-deleted");
  }

  private CloudFoundryConfigurationProperties.ManagedAccount createAccount(String name) {
    CloudFoundryConfigurationProperties.ManagedAccount account =
        new CloudFoundryConfigurationProperties.ManagedAccount();
    account.setName(name);
    account.setApi("api." + name);
    account.setUser("user-" + name);
    account.setPassword("pwd-" + name);

    return account;
  }

  private CloudFoundryCredentials createCredentials(String name) {
    return new CloudFoundryCredentials(
        name, null, null, "api." + name, "user-" + name, "pwd-" + name, null);
  }

  private void loadProviderFromRepository() {
    Set<CloudFoundryCredentials> accounts =
        ProviderUtils.buildThreadSafeSetOfAccounts(repository, CloudFoundryCredentials.class);

    List<CloudFoundryCachingAgent> agents =
        accounts.stream()
            .map(
                account ->
                    new CloudFoundryCachingAgent(account.getName(), account.getClient(), registry))
            .collect(Collectors.toList());

    provider.getAgents().addAll(agents);
  }

  private class TestAgentScheduler extends CatsModuleAware implements AgentScheduler<AgentLock> {
    private List<String> scheduledAccountNames = new ArrayList<>();
    private List<String> unscheduledAccountNames = new ArrayList<>();

    @Override
    public void schedule(
        Agent agent,
        AgentExecution agentExecution,
        ExecutionInstrumentation executionInstrumentation) {
      if (agent instanceof AccountAware) {
        scheduledAccountNames.add(((AccountAware) agent).getAccountName());
      }
    }

    @Override
    public CatsModule getCatsModule() {
      return catsModule;
    }

    @Override
    public void unschedule(Agent agent) {
      if (agent instanceof AccountAware) {
        unscheduledAccountNames.add(((AccountAware) agent).getAccountName());
      }
    }

    List<String> getScheduledAccountNames() {
      return scheduledAccountNames;
    }

    List<String> getUnscheduledAccountNames() {
      return unscheduledAccountNames;
    }
  }
}
