/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.kubernetes.caching.KubernetesCachingAgent
import com.netflix.spinnaker.clouddriver.kubernetes.caching.KubernetesCachingAgentDispatcher
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class KubernetesV1CachingAgentDispatcher implements KubernetesCachingAgentDispatcher {
  @Autowired
  ObjectMapper objectMapper

  @Autowired
  Registry registry

  @Override
  Collection<KubernetesCachingAgent> buildAllCachingAgents(KubernetesNamedAccountCredentials credentials) {
    def agents = []
    for (def index = 0; index < credentials.cacheThreads; index++) {
      agents << new KubernetesInstanceCachingAgent(credentials, objectMapper, registry, index, credentials.cacheThreads)
      agents << new KubernetesLoadBalancerCachingAgent(credentials, objectMapper, registry, index, credentials.cacheThreads)
      agents << new KubernetesSecurityGroupCachingAgent(credentials, objectMapper, registry, index, credentials.cacheThreads)
      agents << new KubernetesServerGroupCachingAgent(credentials, objectMapper, registry, index, credentials.cacheThreads)
      agents << new KubernetesDeploymentCachingAgent(credentials, objectMapper, registry, index, credentials.cacheThreads)
      agents << new KubernetesServiceAccountCachingAgent(credentials, objectMapper, registry, index, credentials.cacheThreads)
      agents << new KubernetesConfigMapCachingAgent(credentials, objectMapper, registry, index, credentials.cacheThreads)
      agents << new KubernetesSecretCachingAgent(credentials, objectMapper, registry, index, credentials.cacheThreads)
      agents << new KubernetesControllersCachingAgent(credentials, objectMapper, registry, index, credentials.cacheThreads)
    }

    return agents
  }
}
