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
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.*
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.cache.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.provider.KubernetesProvider
import com.netflix.spinnaker.clouddriver.kubernetes.provider.view.MutableCacheData
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials
import groovy.util.logging.Slf4j
import io.fabric8.kubernetes.api.model.Pod

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE

@Slf4j
class KubernetesInstanceCachingAgent implements  CachingAgent, AccountAware {
  static final Set<AgentDataType> types = Collections.unmodifiableSet([
      AUTHORITATIVE.forType(Keys.Namespace.INSTANCES.ns),
      AUTHORITATIVE.forType(Keys.Namespace.PROCESSES.ns),
  ] as Set)

  final KubernetesCloudProvider kubernetesCloudProvider
  final String accountName
  final String namespace
  final KubernetesCredentials credentials
  final ObjectMapper objectMapper
  final Registry registry

  KubernetesInstanceCachingAgent(KubernetesCloudProvider kubernetesCloudProvider,
                                 String accountName,
                                 KubernetesCredentials credentials,
                                 String namespace,
                                 ObjectMapper objectMapper,
                                 Registry registry) {
    this.kubernetesCloudProvider = kubernetesCloudProvider
    this.accountName = accountName
    this.credentials = credentials
    this.objectMapper = objectMapper
    this.namespace = namespace
    this.registry = registry
  }

  @Override
  String getAccountName() {
    return accountName
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Loading pods in $agentType")

    def pods = credentials.apiAdaptor.getPods(namespace)

    buildCacheResult(pods)
  }

  private CacheResult buildCacheResult(List<Pod> pods) {
    log.info("Describing items in ${agentType}")

    Map<String, MutableCacheData> cachedInstances = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedProcesses = MutableCacheData.mutableCacheMap()

    for (Pod pod : pods) {
      if (!pod) {
        continue
      }

      def key = Keys.getProcessKey(accountName, namespace, pod.metadata.name)
      cachedProcesses[key].with {
        attributes.name = pod.metadata.name
        attributes.pod = pod
        key = Keys.getInstanceKey(accountName, namespace, pod.metadata.name)
        cachedInstances[key].with {
          attributes.name = pod.metadata.name
          attributes.pod = pod
        }
      }
    }

    log.info("Caching ${cachedInstances.size()} instances in ${agentType}")
    log.info("Caching ${cachedProcesses.size()} processes in ${agentType}")

    new DefaultCacheResult([
        (Keys.Namespace.INSTANCES.ns): cachedInstances.values(),
        (Keys.Namespace.PROCESSES.ns): cachedProcesses.values()
    ], [:])
  }

  @Override
  String getAgentType() {
    "${accountName}/${namespace}/${KubernetesInstanceCachingAgent.simpleName}"
  }

  @Override
  String getProviderName() {
    KubernetesProvider.PROVIDER_NAME
  }
}
