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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.v1.caching.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.view.MutableCacheData
import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesV1Credentials
import groovy.util.logging.Slf4j
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.ReplicaSet

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE

@Slf4j
class KubernetesDeploymentCachingAgent extends KubernetesV1CachingAgent {
  static final Set<AgentDataType> types = Collections.unmodifiableSet([
      AUTHORITATIVE.forType(Keys.Namespace.DEPLOYMENTS.ns),
      INFORMATIVE.forType(Keys.Namespace.SERVER_GROUPS.ns),
  ] as Set)

  KubernetesDeploymentCachingAgent(KubernetesNamedAccountCredentials<KubernetesV1Credentials> namedAccountCredentials,
                                   ObjectMapper objectMapper,
                                   Registry registry,
                                   int agentIndex,
                                   int agentCount) {
    super(namedAccountCredentials, objectMapper, registry, agentIndex, agentCount)
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Loading deployemnts in $agentType")
    reloadNamespaces()

    def deployments = namespaces.collect { String namespace ->
      credentials.apiAdaptor.getDeployments(namespace)
    }.flatten()

    buildCacheResult(deployments)
  }

  private CacheResult buildCacheResult(List<Deployment> deployments) {
    log.info("Describing items in ${agentType}")

    Map<String, MutableCacheData> cachedDeployments = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedReplicaSets = MutableCacheData.mutableCacheMap()

    for (Deployment deployment: deployments) {
      if (!deployment) {
        continue
      }

      def namespace = deployment.metadata.namespace
      def name = deployment.metadata.name

      // TODO(lwander) examine to see if this is a performance bottleneck at scale
      def replicaSetKeys = credentials.apiAdaptor.getReplicaSets(namespace, [(name): "true"]).collect { ReplicaSet replicaSet ->
        Keys.getServerGroupKey(accountName, namespace, replicaSet.metadata.name)
      }

      def key = Keys.getDeploymentKey(accountName, namespace, name)

      replicaSetKeys.each { String rskey ->
        cachedReplicaSets[rskey].with {
          relationships[Keys.Namespace.DEPLOYMENTS.ns] = [key]
        }
      }

      cachedDeployments[key].with {
        attributes.name = deployment.metadata.name
        attributes.deployment = deployment
        relationships[Keys.Namespace.SERVER_GROUPS.ns] = replicaSetKeys
      }
    }

    log.info("Caching ${cachedDeployments.size()} deployments in ${agentType}")

    new DefaultCacheResult([
        (Keys.Namespace.DEPLOYMENTS.ns): cachedDeployments.values(),
        (Keys.Namespace.SERVER_GROUPS.ns): cachedReplicaSets.values(),
    ], [:])
  }
}
