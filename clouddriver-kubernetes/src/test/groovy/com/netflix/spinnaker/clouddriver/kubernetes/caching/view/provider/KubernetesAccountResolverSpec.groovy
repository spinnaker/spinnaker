/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider


import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.description.GlobalResourcePropertyRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.description.ResourcePropertyRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import spock.lang.Specification

class KubernetesAccountResolverSpec extends Specification {
  String ACCOUNT_NAME = "test"
  AccountCredentialsRepository credentialsRepository = Mock(AccountCredentialsRepository)
  ResourcePropertyRegistry globalResourcePropertyRegistry = Mock(GlobalResourcePropertyRegistry)

  void "returns an account in the repository if and only if it is a kubernetes account"() {
    given:
    KubernetesAccountResolver accountResolver = new KubernetesAccountResolver(credentialsRepository, globalResourcePropertyRegistry)
    KubernetesCredentials kubernetesCredentials = Mock(KubernetesCredentials)
    Optional<KubernetesCredentials> credentials

    when:
    credentials = accountResolver.getCredentials(ACCOUNT_NAME)

    then:
    1 * credentialsRepository.getOne(ACCOUNT_NAME) >> Mock(KubernetesNamedAccountCredentials) {
      getCredentials() >> kubernetesCredentials
    }
    credentials.isPresent()
    credentials.get() == kubernetesCredentials

    when:
    credentials = accountResolver.getCredentials(ACCOUNT_NAME)

    then:
    1 * credentialsRepository.getOne(ACCOUNT_NAME) >> Mock(AccountCredentials)
    !credentials.isPresent()

    when:
    credentials = accountResolver.getCredentials(ACCOUNT_NAME)

    then:
    1 * credentialsRepository.getOne(ACCOUNT_NAME) >> null
    !credentials.isPresent()
  }

  void "returns the account's property registry, falling back to the global registry"() {
    given:
    KubernetesAccountResolver accountResolver = new KubernetesAccountResolver(credentialsRepository, globalResourcePropertyRegistry)
    ResourcePropertyRegistry resourcePropertyRegistry = Mock(ResourcePropertyRegistry)
    ResourcePropertyRegistry registry

    when:
    registry = accountResolver.getResourcePropertyRegistry(ACCOUNT_NAME)

    then:
    1 * credentialsRepository.getOne(ACCOUNT_NAME) >> Mock(KubernetesNamedAccountCredentials) {
      getCredentials() >> Mock(KubernetesCredentials) {
        getResourcePropertyRegistry() >> resourcePropertyRegistry
      }
    }
    registry == resourcePropertyRegistry

    when:
    registry = accountResolver.getResourcePropertyRegistry(ACCOUNT_NAME)

    then:
    1 * credentialsRepository.getOne(ACCOUNT_NAME) >> Mock(AccountCredentials) {
      getCredentials() >> Mock(KubernetesCredentials)
    }
    registry == globalResourcePropertyRegistry

    when:
    registry = accountResolver.getResourcePropertyRegistry(ACCOUNT_NAME)

    then:
    1 * credentialsRepository.getOne(ACCOUNT_NAME) >> null
    registry == globalResourcePropertyRegistry
  }
}
