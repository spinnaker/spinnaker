/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.provider.KubernetesProvider
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials

abstract class KubernetesCachingAgent implements CachingAgent, AccountAware {
  final String accountName
  final String providerName = KubernetesProvider.PROVIDER_NAME
  final KubernetesCloudProvider kubernetesCloudProvider = new KubernetesCloudProvider()
  final ObjectMapper objectMapper
  final KubernetesCredentials credentials
  final int agentIndex
  final int agentCount
  List<String> namespaces

  KubernetesCachingAgent(String accountName,
                         ObjectMapper objectMapper,
                         KubernetesCredentials credentials,
                         int agentIndex,
                         int agentCount) {
    this.accountName = accountName
    this.objectMapper = objectMapper
    this.credentials = credentials
    this.agentIndex = agentIndex
    this.agentCount = agentCount
    reloadNamespaces()
  }

  @Override
  String getAgentType() {
    return "${accountName}/${getSimpleName()}[${agentIndex + 1}/$agentCount]"
  }

  void reloadNamespaces() {
    namespaces = credentials.getNamespaces().findAll { String namespace ->
      // Short circuit when one thread is provided to avoid doing the hashCode calculation
      agentCount == 1 || (namespace.hashCode() % agentCount).abs() == agentIndex
    }
  }

  abstract String getSimpleName()
}
