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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.security

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties
import com.netflix.spinnaker.clouddriver.kubernetes.config.LinkedDockerRegistryConfiguration
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentialsInitializer
import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesV1Credentials
import com.netflix.spinnaker.clouddriver.kubernetes.v2.names.KubernetesManifestNamer
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderVersion
import org.springframework.context.ApplicationContext
import spock.lang.Specification

class KubernetesNamedAccountCredentialsInitializerSpec extends Specification {
  CatsModule catsModule = Mock(CatsModule)
  ApplicationContext applicationContext = Mock(ApplicationContext)
  AccountCredentialsRepository accountCredentialsRepository = Mock(AccountCredentialsRepository)
  List<ProviderSynchronizerTypeWrapper> providerSynchronizerTypeWrapper = Collections.emptyList()
  NamerRegistry namerRegistry = Mock(NamerRegistry)
  KubernetesNamedAccountCredentials.CredentialFactory credentialFactory = new KubernetesNamedAccountCredentials.CredentialFactory(
    "userAgent",
    new NoopRegistry(),
    namerRegistry,
    accountCredentialsRepository,
    Mock(KubectlJobExecutor)
  )

  KubernetesNamedAccountCredentialsInitializer kubernetesNamedAccountCredentialsInitializer = new KubernetesNamedAccountCredentialsInitializer(
    spectatorRegistry: new NoopRegistry()
  )

  def synchronizeAccounts(KubernetesConfigurationProperties kubernetesConfigurationProperties) {
    return kubernetesNamedAccountCredentialsInitializer.synchronizeKubernetesAccounts(
      credentialFactory, kubernetesConfigurationProperties, catsModule, applicationContext, accountCredentialsRepository, providerSynchronizerTypeWrapper
    )
  }

  void "is a no-op when there are no configured accounts"() {
    when:
    KubernetesConfigurationProperties kubernetesConfigurationProperties = new KubernetesConfigurationProperties()
    synchronizeAccounts(kubernetesConfigurationProperties)

    then:
    0 * accountCredentialsRepository.save(*_)
  }

  void "correctly creates a v2 account and defaults properties"() {
    given:
    KubernetesNamedAccountCredentials credentials

    when:
    KubernetesConfigurationProperties kubernetesConfigurationProperties = new KubernetesConfigurationProperties()
    kubernetesConfigurationProperties.setAccounts([
      new KubernetesConfigurationProperties.ManagedAccount(
        name: "test-account",
        namespaces: ["default"],
        providerVersion: ProviderVersion.v2)
    ])
    synchronizeAccounts(kubernetesConfigurationProperties)


    then:
    1 * accountCredentialsRepository.save("test-account", _ as KubernetesNamedAccountCredentials) >> { _, creds ->
      credentials = creds
    }
    1 * namerRegistry.getNamingStrategy("kubernetesAnnotations") >> Mock(KubernetesManifestNamer)

    credentials.getName() == "test-account"
    credentials.getProviderVersion() == ProviderVersion.v2
    credentials.getEnvironment() == "test-account"
    credentials.getAccountType() == "test-account"
    credentials.getSkin() == "v2"
    credentials.getCacheThreads() == 1
    credentials.getCacheIntervalSeconds() == null

    credentials.getCredentials() instanceof KubernetesV2Credentials
    KubernetesV2Credentials accountCredentials = (KubernetesV2Credentials) credentials.getCredentials()
    accountCredentials.isServiceAccount() == false
    accountCredentials.isOnlySpinnakerManaged() == false
    accountCredentials.isDebug() == false
    accountCredentials.isMetrics() == true
    accountCredentials.isLiveManifestCalls() == false
  }

  void "correctly creates a v1 account and defaults properties"() {
    given:
    KubernetesNamedAccountCredentials credentials

    when:
    KubernetesConfigurationProperties kubernetesConfigurationProperties = new KubernetesConfigurationProperties()
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
