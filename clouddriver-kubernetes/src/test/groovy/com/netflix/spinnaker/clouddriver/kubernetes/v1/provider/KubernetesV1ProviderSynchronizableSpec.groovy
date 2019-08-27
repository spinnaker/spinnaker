/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.provider

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.cats.module.CatsModule

import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.agent.KubernetesV1CachingAgentDispatcher
import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesV1Credentials
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.AccountResourcePropertyRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKindRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.kubernetes.config.LinkedDockerRegistryConfiguration
import com.netflix.spinnaker.clouddriver.security.ProviderVersion
import com.netflix.spinnaker.kork.configserver.ConfigFileService
import spock.lang.Specification

class KubernetesV1ProviderSynchronizableSpec extends Specification {

  CatsModule catsModule = Mock(CatsModule)
  KubernetesV1Provider v1Provider = Mock(KubernetesV1Provider)
  AccountCredentialsRepository accountCredentialsRepository = Mock(AccountCredentialsRepository)
  KubernetesV1CachingAgentDispatcher agentDispatcher = Mock(KubernetesV1CachingAgentDispatcher)
  NamerRegistry namerRegistry = Mock(NamerRegistry)
  ConfigFileService configFileService = new ConfigFileService()

  KubernetesNamedAccountCredentials.CredentialFactory credentialFactory = new KubernetesNamedAccountCredentials.CredentialFactory(
    "userAgent",
    new NoopRegistry(),
    namerRegistry,
    accountCredentialsRepository,
    Mock(KubectlJobExecutor),
    configFileService,
    null,
    null
  )

  def synchronizeAccounts(KubernetesConfigurationProperties configurationProperties) {
    KubernetesV1ProviderSynchronizable synchronizable = new KubernetesV1ProviderSynchronizable(
      v1Provider,
      accountCredentialsRepository,
      agentDispatcher,
      configurationProperties,
      credentialFactory,
      new KubernetesSpinnakerKindMap(),
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

  void "correctly creates a v1 account and defaults properties"() {
    given:
    KubernetesNamedAccountCredentials credentials

    when:
    KubernetesConfigurationProperties kubernetesConfigurationProperties = new KubernetesConfigurationProperties()
    accountCredentialsRepository.getAll() >> new HashSet<AccountCredentials>()

    kubernetesConfigurationProperties.setAccounts([
      new KubernetesConfigurationProperties.ManagedAccount(
        name: "test-account",
        kubeconfigContents: """
apiVersion: v1
contexts:
- name: default
  context:
    cluster: test
    user: test
current-context: default
clusters:
- name: test
  cluster:
    server: "https://foo:6443"
""",
        namespaces: ["default"],
        dockerRegistries: [new LinkedDockerRegistryConfiguration(accountName: "docker-account")]
      )
    ])
    synchronizeAccounts(kubernetesConfigurationProperties)

    then:
    1 * accountCredentialsRepository.save("test-account", _ as KubernetesNamedAccountCredentials) >> { _, creds ->
      credentials = creds
    }

    credentials.getName() == "test-account"
    credentials.getProviderVersion() == ProviderVersion.v1
    credentials.getEnvironment() == "test-account"
    credentials.getAccountType() == "test-account"
    credentials.getSkin() == "v1"
    credentials.getCacheThreads() == 1
    credentials.getCacheIntervalSeconds() == null

    credentials.getCredentials() instanceof KubernetesV1Credentials
    KubernetesV1Credentials accountCredentials = (KubernetesV1Credentials) credentials.getCredentials()
    accountCredentials.getDeclaredNamespaces() == ["default"]
  }

}
