/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.clouddriver.kubernetes.security

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.AccountResourcePropertyRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesKindRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.v2.names.KubernetesManifestNamer
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.clouddriver.security.ProviderVersion
import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.kork.configserver.ConfigFileService
import spock.lang.Specification

import java.nio.file.Files

class KubernetesNamedAccountCredentialsSpec extends Specification {
  KubernetesSpinnakerKindMap kindMap = new KubernetesSpinnakerKindMap(Collections.emptyList())
  NamerRegistry namerRegistry = new NamerRegistry([new KubernetesManifestNamer()])
  ConfigFileService configFileService = new ConfigFileService()
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


  void "should equal 2 Kubernetes accounts with same kubeconfig content"() {
    setup:
      def file1 = Files.createTempFile("test", "")
      file1.append("some content")
      def account1Def = new KubernetesConfigurationProperties.ManagedAccount()
      account1Def.setName("test")
      account1Def.setCacheThreads(1)
      account1Def.setProviderVersion(ProviderVersion.v2)
      account1Def.getPermissions().add(Authorization.READ, "test@test.com")
      account1Def.setNamespaces(["ns1", "ns2"])
      account1Def.setKubeconfigFile(file1.toString())

      def file2 = Files.createTempFile("other", "")
      file2.append("some content")
      def account2Def = new KubernetesConfigurationProperties.ManagedAccount()
      account2Def.setName("test")
      account2Def.setCacheThreads(1)
      account2Def.setProviderVersion(ProviderVersion.v2)
      account2Def.getPermissions().add(Authorization.READ, "test@test.com")
      account2Def.setNamespaces(["ns1", "ns2"])
      account2Def.setKubeconfigFile(file2.toString())


    when:
      def account1 = new KubernetesNamedAccountCredentials(account1Def, credentialFactory)
      def account2 = new KubernetesNamedAccountCredentials(account2Def, credentialFactory)

    then:
      account1.equals(account2)

    cleanup:
      Files.delete(file1)
      Files.delete(file2)
  }
}
