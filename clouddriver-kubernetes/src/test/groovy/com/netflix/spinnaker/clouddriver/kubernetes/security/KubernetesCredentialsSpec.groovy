/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.security

import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.api.KubernetesApiAdaptor
import com.netflix.spinnaker.clouddriver.kubernetes.config.LinkedDockerRegistryConfiguration
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import spock.lang.Specification

class KubernetesCredentialsSpec extends Specification {
  List<String> NAMESPACES1 = ['default', 'kube-system']
  List<String> NAMESPACES2 = ['default', 'spacename']

  String ACCOUNT1 = 'account-default'

  // These aren't pertinent to the test, so they can be reused for each account
  String ADDRESS = 'gcr.io'
  String BASIC_AUTH = 'lwander:hunter2'
  String EMAIL = 'lwander@google.com'

  List<LinkedDockerRegistryConfiguration> REGISTRIES1 = [
    new LinkedDockerRegistryConfiguration(accountName: ACCOUNT1, namespaces: NAMESPACES1)
  ]

  List<LinkedDockerRegistryConfiguration> REGISTRIES2 = [
    new LinkedDockerRegistryConfiguration(accountName: ACCOUNT1, namespaces: null)
  ]

  DockerRegistryNamedAccountCredentials mockCredentials(String accountName) {
    DockerRegistryNamedAccountCredentials registryAccountMock = Mock(DockerRegistryNamedAccountCredentials)
    registryAccountMock.getAccountName() >> ACCOUNT1
    registryAccountMock.getAddress() >> ADDRESS
    registryAccountMock.getEmail() >> EMAIL

    return registryAccountMock
  }

  void "should ignore kubernetes namespaces"() {
    setup:
    KubernetesApiAdaptor adaptorMock = Mock(KubernetesApiAdaptor)
    adaptorMock.getNamespacesByName() >> NAMESPACES2

    AccountCredentialsRepository repositoryMock = Mock(AccountCredentialsRepository)
    DockerRegistryNamedAccountCredentials registryAccountMock = mockCredentials(ACCOUNT1)
    repositoryMock.getOne(ACCOUNT1) >> registryAccountMock

    when:
    def result = new KubernetesCredentials(adaptorMock, NAMESPACES1, REGISTRIES1, repositoryMock)

    then:
    result.getNamespaces() == NAMESPACES1
    result.dockerRegistries.get(0).namespaces == NAMESPACES1
  }

  void "should use kubernetes namespaces"() {
    setup:
    KubernetesApiAdaptor adaptorMock = Mock(KubernetesApiAdaptor)
    adaptorMock.getNamespacesByName() >> NAMESPACES2

    AccountCredentialsRepository repositoryMock = Mock(AccountCredentialsRepository)
    DockerRegistryNamedAccountCredentials registryAccountMock = mockCredentials(ACCOUNT1)
    repositoryMock.getOne(ACCOUNT1) >> registryAccountMock

    when:
    def result = new KubernetesCredentials(adaptorMock, null, REGISTRIES2, repositoryMock)

    then:
    result.getNamespaces() == NAMESPACES2
    result.dockerRegistries.get(0).namespaces == NAMESPACES2
  }

  void "should not use kubernetes namespaces only in registry"() {
    setup:
    KubernetesApiAdaptor adaptorMock = Mock(KubernetesApiAdaptor)
    adaptorMock.getNamespacesByName() >> NAMESPACES2

    AccountCredentialsRepository repositoryMock = Mock(AccountCredentialsRepository)
    DockerRegistryNamedAccountCredentials registryAccountMock = mockCredentials(ACCOUNT1)
    repositoryMock.getOne(ACCOUNT1) >> registryAccountMock

    when:
    def result = new KubernetesCredentials(adaptorMock, null, REGISTRIES1, repositoryMock)

    then:
    result.getNamespaces() == NAMESPACES2
    result.dockerRegistries.get(0).namespaces == NAMESPACES1
  }
}
