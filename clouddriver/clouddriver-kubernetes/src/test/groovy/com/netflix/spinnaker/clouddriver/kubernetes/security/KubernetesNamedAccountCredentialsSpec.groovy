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

package com.netflix.spinnaker.clouddriver.kubernetes.security

import com.google.common.collect.ImmutableList
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesAccountProperties.ManagedAccount
import com.netflix.spinnaker.clouddriver.kubernetes.description.AccountResourcePropertyRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.description.GlobalResourcePropertyRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesSpinnakerKindMap
import com.netflix.spinnaker.clouddriver.kubernetes.names.KubernetesManifestNamer
import com.netflix.spinnaker.clouddriver.kubernetes.names.KubernetesNamerRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesUnregisteredCustomResourceHandler
import com.netflix.spinnaker.clouddriver.kubernetes.op.job.KubectlJobExecutor
import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.kork.configserver.ConfigFileService
import spock.lang.Specification

import java.nio.file.Files

class KubernetesNamedAccountCredentialsSpec extends Specification {
  KubernetesNamerRegistry namerRegistry = new KubernetesNamerRegistry([new KubernetesManifestNamer()])
  ConfigFileService configFileService = new ConfigFileService()
  AccountResourcePropertyRegistry.Factory resourcePropertyRegistryFactory = Mock(AccountResourcePropertyRegistry.Factory)
  KubernetesKindRegistry.Factory kindRegistryFactory = Mock(KubernetesKindRegistry.Factory)
  KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap = new KubernetesSpinnakerKindMap(ImmutableList.of())
  GlobalResourcePropertyRegistry globalResourcePropertyRegistry = new GlobalResourcePropertyRegistry(ImmutableList.of(), new KubernetesUnregisteredCustomResourceHandler())

  KubectlJobExecutor mockKubectlJobExecutor = Mock(KubectlJobExecutor)

  KubernetesCredentials.Factory credentialFactory = new KubernetesCredentials.Factory(
    new NoopRegistry(),
    namerRegistry,
    mockKubectlJobExecutor,
    configFileService,
    resourcePropertyRegistryFactory,
    kindRegistryFactory,
    kubernetesSpinnakerKindMap,
    globalResourcePropertyRegistry
  )


  void "should equal 2 Kubernetes accounts with same kubeconfig content"() {
    setup:
      def file1 = Files.createTempFile("test", "")
      file1.toFile().append("some content")
      def account1Def = new ManagedAccount()
      account1Def.setName("test")
      account1Def.setCacheThreads(1)
      account1Def.getPermissions().add(Authorization.READ, "test@test.com")
      account1Def.setNamespaces(["ns1", "ns2"])
      account1Def.setKubeconfigFile(file1.toString())

      def file2 = Files.createTempFile("other", "")
      file2.toFile().append("some content")
      def account2Def = new ManagedAccount()
      account2Def.setName("test")
      account2Def.setCacheThreads(1)
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

  void 'getting namespaces makes no calls to kubernetes'() {
    given: 'an account that does not specify namespaces'
      def account1Def = new ManagedAccount()
      account1Def.setName("test")
      account1Def.setCacheThreads(1)
      account1Def.getPermissions().add(Authorization.READ, "test@test.com")
      account1Def.setServiceAccount(true);
      def account1 = new KubernetesNamedAccountCredentials(account1Def, credentialFactory)

    when: 'retrieving namespaces for the account'
      account1.getNamespaces()

    then: 'no calls to kubernetes occurred'
      0 * mockKubectlJobExecutor._
  }
}
