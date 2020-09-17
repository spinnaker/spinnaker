/*
 * Copyright 2019 Armory
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.caching

import com.google.common.collect.ImmutableList
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.AgentExecution
import com.netflix.spinnaker.cats.agent.AgentScheduler
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation
import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.cats.module.CatsModuleAware
import com.netflix.spinnaker.cats.provider.ProviderRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCoreCachingAgent
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesUnregisteredCustomResourceCachingAgent
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties
import com.netflix.spinnaker.clouddriver.kubernetes.names.KubernetesNamerRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCachingAgentDispatcher
import com.netflix.spinnaker.clouddriver.kubernetes.description.AccountResourcePropertyRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesSpinnakerKindMap
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesKindRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.names.KubernetesManifestNamer
import com.netflix.spinnaker.clouddriver.kubernetes.op.job.KubectlJobExecutor
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import com.netflix.spinnaker.kork.configserver.ConfigFileService
import spock.lang.Specification

class KubernetesProviderSynchronizableSpec extends Specification {

  CatsModule catsModule = Mock(CatsModule)
  AccountCredentialsRepository accountCredentialsRepository
  KubernetesNamerRegistry namerRegistry = new KubernetesNamerRegistry([new KubernetesManifestNamer()])
  ConfigFileService configFileService = Mock(ConfigFileService)
  KubernetesProvider kubernetesProvider = new KubernetesProvider()
  KubernetesCachingAgentDispatcher agentDispatcher = Mock(KubernetesCachingAgentDispatcher)
  AccountResourcePropertyRegistry.Factory resourcePropertyRegistryFactory = Mock(AccountResourcePropertyRegistry.Factory)
  KubernetesKindRegistry.Factory kindRegistryFactory = Mock(KubernetesKindRegistry.Factory)
  KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap = new KubernetesSpinnakerKindMap(ImmutableList.of())
  Registry spectatorRegistry = Mock(Registry)
  ProviderRegistry providerRegistry = Mock(ProviderRegistry)
  TestAgentScheduler scheduler

  KubernetesCredentials.Factory credentialFactory = new KubernetesCredentials.Factory(
    new NoopRegistry(),
    namerRegistry,
    Mock(KubectlJobExecutor),
    configFileService,
    resourcePropertyRegistryFactory,
    kindRegistryFactory,
    kubernetesSpinnakerKindMap
  )

  def synchronizeAccounts(KubernetesConfigurationProperties configurationProperties, failedAgentAccounts = []) {
    catsModule.providerRegistry >> providerRegistry
    providerRegistry.providers >> [kubernetesProvider]
    kubernetesProvider.setAgentScheduler(scheduler)
    for (KubernetesConfigurationProperties.ManagedAccount account in configurationProperties.accounts) {
      def credentials = new KubernetesNamedAccountCredentials(account, credentialFactory)
      if (account.name in failedAgentAccounts) {
        agentDispatcher.buildAllCachingAgents(credentials) >> { throw new Exception("Exception building agents for account " + credentials) }
      } else {
        agentDispatcher.buildAllCachingAgents(credentials) >> agentsForCredentials(credentials)
      }
    }

    KubernetesProviderSynchronizable synchronizable = new KubernetesProviderSynchronizable(
      kubernetesProvider,
      accountCredentialsRepository,
      agentDispatcher,
      configurationProperties,
      credentialFactory,
      catsModule
    )

    synchronizable.synchronize()
  }

  def agentsForCredentials(KubernetesNamedAccountCredentials... credentials) {
    List<Agent> result = new ArrayList<>()
    for (KubernetesNamedAccountCredentials creds in credentials) {
      result << new KubernetesCoreCachingAgent(creds, null, spectatorRegistry, 0, 1, 1L)
      result << new KubernetesUnregisteredCustomResourceCachingAgent(creds, null, spectatorRegistry, 0, 1, 1L)
    }
    return result
  }

  void "is a no-op when there are no configured accounts"() {
    when:
    KubernetesConfigurationProperties kubernetesConfigurationProperties = new KubernetesConfigurationProperties()
    accountCredentialsRepository = new MapBackedAccountCredentialsRepository()
    scheduler = new TestAgentScheduler(catsModule)
    synchronizeAccounts(kubernetesConfigurationProperties)


    then:
    accountCredentialsRepository.all.size() == 0
    kubernetesProvider.agents.size() == 0
    scheduler.agents.size() == 0
  }

  void "correctly creates an account and defaults properties"() {
    when:
    KubernetesConfigurationProperties configurationProperties = new KubernetesConfigurationProperties()
    configurationProperties.setAccounts([
      new KubernetesConfigurationProperties.ManagedAccount(
        name: "test-account",
        namespaces: ["default"],
      )
    ])
    accountCredentialsRepository = new MapBackedAccountCredentialsRepository()
    scheduler = new TestAgentScheduler(catsModule)
    synchronizeAccounts(configurationProperties)

    then:
    accountCredentialsRepository.all.size() == 1
    KubernetesNamedAccountCredentials credentials = accountCredentialsRepository.getOne("test-account") as KubernetesNamedAccountCredentials

    credentials.getName() == "test-account"
    credentials.getEnvironment() == "test-account"
    credentials.getAccountType() == "test-account"
    credentials.getCacheIntervalSeconds() == null
    credentials.getCacheThreads() == 1

    credentials.getCredentials() instanceof KubernetesCredentials
    KubernetesCredentials accountCredentials = (KubernetesCredentials) credentials.getCredentials()
    accountCredentials.isServiceAccount() == false
    accountCredentials.isOnlySpinnakerManaged() == false
    accountCredentials.isDebug() == false
    accountCredentials.isMetricsEnabled() == true

    kubernetesProvider.agents.size() == 2
    kubernetesProvider.agents.find { it.agentType == "test-account/KubernetesCoreCachingAgent[1/1]" } != null
    kubernetesProvider.agents.find { it.agentType == "test-account/KubernetesUnregisteredCustomResourceCachingAgent[1/1]" } != null

    scheduler.agents.size() == 2
    scheduler.agents.find { it.agentType == "test-account/KubernetesCoreCachingAgent[1/1]" } != null
    scheduler.agents.find { it.agentType == "test-account/KubernetesUnregisteredCustomResourceCachingAgent[1/1]" } != null
  }

  void "adds a new account and leaves other accounts unmodified"() {
    when:
    KubernetesConfigurationProperties configurationProperties = new KubernetesConfigurationProperties()
    configurationProperties.setAccounts([ new KubernetesConfigurationProperties.ManagedAccount(
      name: "new-account",
      namespaces: ["default"],
    ), new KubernetesConfigurationProperties.ManagedAccount(
      name: "existing-account",
      namespaces: ["default"],
    ) ])
    def existingAccountCredentials = new KubernetesNamedAccountCredentials(
      configurationProperties.getAccounts().get(1),
      credentialFactory
    )
    accountCredentialsRepository = new MapBackedAccountCredentialsRepository()
    accountCredentialsRepository.save("existing-account", existingAccountCredentials)
    kubernetesProvider.agents = agentsForCredentials(existingAccountCredentials)
    scheduler = new TestAgentScheduler(catsModule)
    scheduler.agents = agentsForCredentials(existingAccountCredentials)
    synchronizeAccounts(configurationProperties)

    then:
    accountCredentialsRepository.all.size() == 2
    accountCredentialsRepository.getOne("existing-account") != null
    accountCredentialsRepository.getOne("new-account") != null

    kubernetesProvider.agents.size() == 4
    kubernetesProvider.agents.find { it.agentType == "existing-account/KubernetesCoreCachingAgent[1/1]" } != null
    kubernetesProvider.agents.find { it.agentType == "existing-account/KubernetesUnregisteredCustomResourceCachingAgent[1/1]" } != null
    kubernetesProvider.agents.find { it.agentType == "new-account/KubernetesCoreCachingAgent[1/1]" } != null
    kubernetesProvider.agents.find { it.agentType == "new-account/KubernetesUnregisteredCustomResourceCachingAgent[1/1]" } != null

    scheduler.agents.size() == 4
    scheduler.agents.find { it.agentType == "existing-account/KubernetesCoreCachingAgent[1/1]" } != null
    scheduler.agents.find { it.agentType == "existing-account/KubernetesUnregisteredCustomResourceCachingAgent[1/1]" } != null
    scheduler.agents.find { it.agentType == "new-account/KubernetesCoreCachingAgent[1/1]" } != null
    scheduler.agents.find { it.agentType == "new-account/KubernetesUnregisteredCustomResourceCachingAgent[1/1]" } != null
  }

  void "deletes an account from repository and deletes its caching agents"() {
    when:
    KubernetesConfigurationProperties configurationProperties = new KubernetesConfigurationProperties()
    configurationProperties.setAccounts([ new KubernetesConfigurationProperties.ManagedAccount(
      name: "account-1",
      namespaces: ["default"],
    )])
    def account1Creds = new KubernetesNamedAccountCredentials(
      configurationProperties.getAccounts().get(0),
      credentialFactory
    )
    def account2Creds = new KubernetesNamedAccountCredentials(
      new KubernetesConfigurationProperties.ManagedAccount(
        name: "account-2",
        namespaces: ["default"],
      ),
      credentialFactory
    )
    accountCredentialsRepository = new MapBackedAccountCredentialsRepository()
    accountCredentialsRepository.save("account-1", account1Creds)
    accountCredentialsRepository.save("account-2", account2Creds)
    kubernetesProvider.agents = agentsForCredentials(account1Creds, account2Creds)
    scheduler = new TestAgentScheduler(catsModule)
    scheduler.agents = agentsForCredentials(account1Creds, account2Creds)
    synchronizeAccounts(configurationProperties)

    then:
    accountCredentialsRepository.all.size() == 1
    accountCredentialsRepository.getOne("account-1") != null

    kubernetesProvider.agents.size() == 2
    kubernetesProvider.agents.find { it.agentType == "account-1/KubernetesCoreCachingAgent[1/1]" } != null
    kubernetesProvider.agents.find { it.agentType == "account-1/KubernetesUnregisteredCustomResourceCachingAgent[1/1]" } != null

    scheduler.agents.size() == 2
    scheduler.agents.find { it.agentType == "account-1/KubernetesCoreCachingAgent[1/1]" } != null
    scheduler.agents.find { it.agentType == "account-1/KubernetesUnregisteredCustomResourceCachingAgent[1/1]" } != null
  }

  void "updates an existing account and leaves other accounts unmodified"() {
    when:
    KubernetesConfigurationProperties configurationProperties = new KubernetesConfigurationProperties()
    configurationProperties.setAccounts([ new KubernetesConfigurationProperties.ManagedAccount(
      name: "updated-account",
      namespaces: ["default", "new-namespace"],
    ), new KubernetesConfigurationProperties.ManagedAccount(
      name: "unmodified-account",
      namespaces: ["default"],
    ) ])
    def updatedAccount = new KubernetesNamedAccountCredentials(
      new KubernetesConfigurationProperties.ManagedAccount(
        name: "updated-account",
        namespaces: ["default"],
      ),
      credentialFactory
    )
    def unmodifiedAccount = new KubernetesNamedAccountCredentials(
      configurationProperties.accounts.get(1),
      credentialFactory
    )
    accountCredentialsRepository = new MapBackedAccountCredentialsRepository()
    accountCredentialsRepository.save("updated-account", updatedAccount)
    accountCredentialsRepository.save("unmodified-account", unmodifiedAccount)
    kubernetesProvider.agents = agentsForCredentials(updatedAccount, unmodifiedAccount)
    scheduler = new TestAgentScheduler(catsModule)
    scheduler.agents = agentsForCredentials(updatedAccount, unmodifiedAccount)
    synchronizeAccounts(configurationProperties)

    then:
    accountCredentialsRepository.all.size() == 2
    accountCredentialsRepository.getOne("updated-account") != null
    (accountCredentialsRepository.getOne("updated-account") as KubernetesNamedAccountCredentials).namespaces == ["default", "new-namespace"]
    accountCredentialsRepository.getOne("unmodified-account") != null

    kubernetesProvider.agents.size() == 4
    kubernetesProvider.agents.find { it.agentType == "updated-account/KubernetesCoreCachingAgent[1/1]" } != null
    ((kubernetesProvider.agents.find { it.agentType == "updated-account/KubernetesCoreCachingAgent[1/1]" } as KubernetesCoreCachingAgent).credentials as KubernetesCredentials).namespaces == ["default", "new-namespace"]
    kubernetesProvider.agents.find { it.agentType == "updated-account/KubernetesUnregisteredCustomResourceCachingAgent[1/1]" } != null
    kubernetesProvider.agents.find { it.agentType == "unmodified-account/KubernetesCoreCachingAgent[1/1]" } != null
    kubernetesProvider.agents.find { it.agentType == "unmodified-account/KubernetesUnregisteredCustomResourceCachingAgent[1/1]" } != null

    scheduler.agents.size() == 4
    scheduler.agents.find { it.agentType == "updated-account/KubernetesCoreCachingAgent[1/1]" } != null
    ((scheduler.agents.find { it.agentType == "updated-account/KubernetesCoreCachingAgent[1/1]" } as KubernetesCoreCachingAgent).credentials as KubernetesCredentials).namespaces == ["default", "new-namespace"]
    scheduler.agents.find { it.agentType == "updated-account/KubernetesUnregisteredCustomResourceCachingAgent[1/1]" } != null
    scheduler.agents.find { it.agentType == "unmodified-account/KubernetesCoreCachingAgent[1/1]" } != null
    scheduler.agents.find { it.agentType == "unmodified-account/KubernetesUnregisteredCustomResourceCachingAgent[1/1]" } != null
  }

  void "errors when building new caching agents leave other agents unmodified"() {
    when:
    KubernetesConfigurationProperties configurationProperties = new KubernetesConfigurationProperties()
    configurationProperties.setAccounts([ new KubernetesConfigurationProperties.ManagedAccount(
      name: "updated-account-good",
      namespaces: ["default", "new-namespace"],
    ), new KubernetesConfigurationProperties.ManagedAccount(
      name: "updated-account-error",
      namespaces: ["default", "new-namespace"],
    ), new KubernetesConfigurationProperties.ManagedAccount(
      name: "unmodified-account",
      namespaces: ["default"],
    ) ])
    def updatedAccountGood = new KubernetesNamedAccountCredentials(
      new KubernetesConfigurationProperties.ManagedAccount(
        name: "updated-account-good",
        namespaces: ["default"],
      ),
      credentialFactory
    )
    def updatedAccountError = new KubernetesNamedAccountCredentials(
      new KubernetesConfigurationProperties.ManagedAccount(
        name: "updated-account-error",
        namespaces: ["default"],
      ),
      credentialFactory
    )
    def unmodifiedAccount = new KubernetesNamedAccountCredentials(
      configurationProperties.accounts.get(2),
      credentialFactory
    )
    accountCredentialsRepository = new MapBackedAccountCredentialsRepository()
    accountCredentialsRepository.save("updated-account-good", updatedAccountGood)
    accountCredentialsRepository.save("updated-account-error", updatedAccountError)
    accountCredentialsRepository.save("unmodified-account", unmodifiedAccount)
    kubernetesProvider.agents = agentsForCredentials(updatedAccountGood, updatedAccountError, unmodifiedAccount)
    scheduler = new TestAgentScheduler(catsModule)
    scheduler.agents = agentsForCredentials(updatedAccountGood, updatedAccountError, unmodifiedAccount)
    synchronizeAccounts(configurationProperties, ["updated-account-error"])

    then:
    accountCredentialsRepository.all.size() == 3
    accountCredentialsRepository.getOne("updated-account-good") != null
    (accountCredentialsRepository.getOne("updated-account-good") as KubernetesNamedAccountCredentials).namespaces == ["default", "new-namespace"]
    accountCredentialsRepository.getOne("updated-account-error") != null
    (accountCredentialsRepository.getOne("updated-account-error") as KubernetesNamedAccountCredentials).namespaces == ["default", "new-namespace"]
    accountCredentialsRepository.getOne("unmodified-account") != null

    kubernetesProvider.agents.size() == 6
    kubernetesProvider.agents.find { it.agentType == "updated-account-good/KubernetesCoreCachingAgent[1/1]" } != null
    kubernetesProvider.agents.find { it.agentType == "updated-account-good/KubernetesUnregisteredCustomResourceCachingAgent[1/1]" } != null
    ((kubernetesProvider.agents.find { it.agentType == "updated-account-good/KubernetesCoreCachingAgent[1/1]" } as KubernetesCoreCachingAgent).credentials as KubernetesCredentials).namespaces == ["default", "new-namespace"]
    kubernetesProvider.agents.find { it.agentType == "updated-account-error/KubernetesCoreCachingAgent[1/1]" } != null
    kubernetesProvider.agents.find { it.agentType == "updated-account-error/KubernetesUnregisteredCustomResourceCachingAgent[1/1]" } != null
    ((kubernetesProvider.agents.find { it.agentType == "updated-account-error/KubernetesCoreCachingAgent[1/1]" } as KubernetesCoreCachingAgent).credentials as KubernetesCredentials).namespaces == ["default"]
    kubernetesProvider.agents.find { it.agentType == "unmodified-account/KubernetesCoreCachingAgent[1/1]" } != null
    kubernetesProvider.agents.find { it.agentType == "unmodified-account/KubernetesUnregisteredCustomResourceCachingAgent[1/1]" } != null

    scheduler.agents.size() == 6
    scheduler.agents.find { it.agentType == "updated-account-good/KubernetesCoreCachingAgent[1/1]" } != null
    scheduler.agents.find { it.agentType == "updated-account-good/KubernetesUnregisteredCustomResourceCachingAgent[1/1]" } != null
    ((scheduler.agents.find { it.agentType == "updated-account-good/KubernetesCoreCachingAgent[1/1]" } as KubernetesCoreCachingAgent).credentials as KubernetesCredentials).namespaces == ["default", "new-namespace"]
    scheduler.agents.find { it.agentType == "updated-account-error/KubernetesCoreCachingAgent[1/1]" } != null
    scheduler.agents.find { it.agentType == "updated-account-error/KubernetesUnregisteredCustomResourceCachingAgent[1/1]" } != null
    ((scheduler.agents.find { it.agentType == "updated-account-error/KubernetesCoreCachingAgent[1/1]" } as KubernetesCoreCachingAgent).credentials as KubernetesCredentials).namespaces == ["default"]
    scheduler.agents.find { it.agentType == "unmodified-account/KubernetesCoreCachingAgent[1/1]" } != null
    scheduler.agents.find { it.agentType == "unmodified-account/KubernetesUnregisteredCustomResourceCachingAgent[1/1]" } != null
  }

  class TestAgentScheduler extends CatsModuleAware implements AgentScheduler {
    List<Agent> agents = new ArrayList<>()
    TestAgentScheduler(CatsModule catsModule) {
      this.catsModule = catsModule
    }
    @Override
    void schedule(Agent agent, AgentExecution agentExecution, ExecutionInstrumentation executionInstrumentation) {
      agents.add(agent)
    }
    @Override
    void unschedule(Agent agent) {
      agents.remove(agents.find { (it.agentType == agent.agentType) })
    }
  }
}
