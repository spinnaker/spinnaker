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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider

import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.GlobalResourcePropertyRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.ResourcePropertyRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesKindRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import spock.lang.Specification

class KubernetesAccountResolverSpec extends Specification {
  String ACCOUNT_NAME = "test"
  AccountCredentialsRepository credentialsRepository = Mock(AccountCredentialsRepository)
  ResourcePropertyRegistry globalResourcePropertyRegistry = Mock(GlobalResourcePropertyRegistry)
  KubernetesKindRegistry.Factory kindRegistryFactory = Mock(KubernetesKindRegistry.Factory)

  void "returns an account in the repository if and only if it is a kubernetes v2 account"() {
    given:
    KubernetesAccountResolver accountResolver = new KubernetesAccountResolver(credentialsRepository, globalResourcePropertyRegistry, kindRegistryFactory)
    KubernetesV2Credentials v2Credentials = Mock(KubernetesV2Credentials)
    Optional<KubernetesV2Credentials> credentials

    when:
    credentials = accountResolver.getCredentials(ACCOUNT_NAME)

    then:
    1 * credentialsRepository.getOne(ACCOUNT_NAME) >> Mock(KubernetesNamedAccountCredentials) {
      getCredentials() >> v2Credentials
    }
    credentials.isPresent()
    credentials.get() == v2Credentials

    when:
    credentials = accountResolver.getCredentials(ACCOUNT_NAME)

    then:
    1 * credentialsRepository.getOne(ACCOUNT_NAME) >> Mock(KubernetesNamedAccountCredentials) {
      getCredentials() >> Mock(KubernetesCredentials)
    }
    !credentials.isPresent()

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
    KubernetesAccountResolver accountResolver = new KubernetesAccountResolver(credentialsRepository, globalResourcePropertyRegistry, kindRegistryFactory)
    ResourcePropertyRegistry v2ResourcePropertyRegistry = Mock(ResourcePropertyRegistry)
    ResourcePropertyRegistry registry

    when:
    registry = accountResolver.getResourcePropertyRegistry(ACCOUNT_NAME)

    then:
    1 * credentialsRepository.getOne(ACCOUNT_NAME) >> Mock(KubernetesNamedAccountCredentials) {
      getCredentials() >> Mock(KubernetesV2Credentials) {
        getResourcePropertyRegistry() >> v2ResourcePropertyRegistry
      }
    }
    registry == v2ResourcePropertyRegistry

    when:
    registry = accountResolver.getResourcePropertyRegistry(ACCOUNT_NAME)

    then:
    1 * credentialsRepository.getOne(ACCOUNT_NAME) >> Mock(KubernetesNamedAccountCredentials) {
      getCredentials() >> Mock(KubernetesCredentials)
    }
    registry == globalResourcePropertyRegistry

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

  void "returns kind registry of an account that exists and a new registry for one that doesn't"() {
    given:
    KubernetesAccountResolver accountResolver = new KubernetesAccountResolver(credentialsRepository, globalResourcePropertyRegistry, kindRegistryFactory)
    KubernetesKindRegistry v2KindRegistry = Mock(KubernetesKindRegistry)
    KubernetesKindRegistry registry

    when:
    registry = accountResolver.getKindRegistry(ACCOUNT_NAME)

    then:
    1 * credentialsRepository.getOne(ACCOUNT_NAME) >> Mock(KubernetesNamedAccountCredentials) {
      getCredentials() >> Mock(KubernetesV2Credentials) {
        getKindRegistry() >> v2KindRegistry
      }
    }
    0 * kindRegistryFactory.create(*_)
    registry == v2KindRegistry

    when:
    registry = accountResolver.getKindRegistry(ACCOUNT_NAME)

    then:
    1 * credentialsRepository.getOne(ACCOUNT_NAME) >> Mock(KubernetesNamedAccountCredentials) {
      getCredentials() >> Mock(KubernetesCredentials)
    }
    1 * kindRegistryFactory.create(*_) >> v2KindRegistry
    registry == v2KindRegistry

    when:
    registry = accountResolver.getKindRegistry(ACCOUNT_NAME)

    then:
    1 * credentialsRepository.getOne(ACCOUNT_NAME) >> Mock(AccountCredentials) {
      getCredentials() >> Mock(KubernetesCredentials)
    }
    1 * kindRegistryFactory.create(*_) >> v2KindRegistry
    registry == v2KindRegistry


    when:
    registry = accountResolver.getKindRegistry(ACCOUNT_NAME)

    then:
    1 * credentialsRepository.getOne(ACCOUNT_NAME) >> null
    1 * kindRegistryFactory.create(*_) >> v2KindRegistry
    registry == v2KindRegistry
  }
}
