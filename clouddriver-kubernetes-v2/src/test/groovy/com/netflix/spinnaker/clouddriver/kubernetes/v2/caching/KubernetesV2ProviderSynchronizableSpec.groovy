/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgentDispatcher
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.AccountResourcePropertyRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesKindRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.v2.names.KubernetesManifestNamer
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderVersion
import com.netflix.spinnaker.kork.configserver.ConfigFileService
import spock.lang.Specification

class KubernetesV2ProviderSynchronizableSpec extends Specification {

  CatsModule catsModule = Mock(CatsModule)
  AccountCredentialsRepository accountCredentialsRepository = Mock(AccountCredentialsRepository)
  NamerRegistry namerRegistry = new NamerRegistry([new KubernetesManifestNamer()])
  ConfigFileService configFileService = Mock(ConfigFileService)
  KubernetesV2Provider kubernetesV2Provider = new KubernetesV2Provider()
  KubernetesV2CachingAgentDispatcher agentDispatcher = Mock(KubernetesV2CachingAgentDispatcher)
  AccountResourcePropertyRegistry.Factory resourcePropertyRegistryFactory = Mock(AccountResourcePropertyRegistry.Factory)
  KubernetesKindRegistry.Factory kindRegistryFactory = Mock(KubernetesKindRegistry.Factory)
  KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap = new KubernetesSpinnakerKindMap(Collections.emptyList())

  KubernetesV2Credentials.Factory credentialFactory = new KubernetesV2Credentials.Factory(
    new NoopRegistry(),
    namerRegistry,
    Mock(KubectlJobExecutor),
    configFileService,
    resourcePropertyRegistryFactory,
    kindRegistryFactory,
    kubernetesSpinnakerKindMap
  )

  def synchronizeAccounts(KubernetesConfigurationProperties configurationProperties) {
    KubernetesV2ProviderSynchronizable synchronizable = new KubernetesV2ProviderSynchronizable(
      kubernetesV2Provider,
      accountCredentialsRepository,
      agentDispatcher,
      configurationProperties,
      credentialFactory,
      catsModule
    )

    synchronizable.synchronize()
  }

  void "is a no-op when there are no configured accounts"() {
    when:
    KubernetesConfigurationProperties kubernetesConfigurationProperties = new KubernetesConfigurationProperties()
    accountCredentialsRepository.getAll() >> new HashSet<AccountCredentials>()
    synchronizeAccounts(kubernetesConfigurationProperties)


    then:
    0 * accountCredentialsRepository.save(*_)
  }

  void "correctly creates a v2 account and defaults properties"() {
    given:
    KubernetesNamedAccountCredentials credentials

    when:
    KubernetesConfigurationProperties configurationProperties = new KubernetesConfigurationProperties()
    configurationProperties.setAccounts([
      new KubernetesConfigurationProperties.ManagedAccount(
        name: "test-account",
        namespaces: ["default"],
        providerVersion: ProviderVersion.v2,
      )
    ])
    accountCredentialsRepository.getAll() >> new HashSet<AccountCredentials>()
    synchronizeAccounts(configurationProperties)

    then:
    1 * accountCredentialsRepository.save("test-account", _ as KubernetesNamedAccountCredentials) >> { _, creds ->
      credentials = creds
    }

    credentials.getName() == "test-account"
    credentials.getProviderVersion() == ProviderVersion.v2
    credentials.getEnvironment() == "test-account"
    credentials.getAccountType() == "test-account"
    credentials.getSkin() == "v2"
    credentials.getCacheIntervalSeconds() == null
    credentials.getCacheThreads() == 1

    credentials.getCredentials() instanceof KubernetesV2Credentials
    KubernetesV2Credentials accountCredentials = (KubernetesV2Credentials) credentials.getCredentials()
    accountCredentials.isServiceAccount() == false
    accountCredentials.isOnlySpinnakerManaged() == false
    accountCredentials.isDebug() == false
    accountCredentials.isMetricsEnabled() == true
    accountCredentials.isLiveManifestCalls() == false
  }
}
