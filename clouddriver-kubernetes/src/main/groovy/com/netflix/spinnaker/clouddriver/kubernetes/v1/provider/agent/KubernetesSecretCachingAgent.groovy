/*
 * Copyright 2017 Skuid, Inc.
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
import io.fabric8.kubernetes.api.model.Secret

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE

@Slf4j
class KubernetesSecretCachingAgent extends KubernetesV1CachingAgent {
  static final Set<AgentDataType> types = Collections.unmodifiableSet([
      AUTHORITATIVE.forType(Keys.Namespace.SECRETS.ns),
  ] as Set)

  KubernetesSecretCachingAgent(KubernetesNamedAccountCredentials<KubernetesV1Credentials> namedAccountCredentials,
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
    log.info("Loading secrets in $agentType")
    reloadNamespaces()

    def secrets = namespaces.collect { String namespace ->
      credentials.apiAdaptor.getSecrets(namespace)
    }.flatten()

    buildCacheResult(secrets)
  }

  private CacheResult buildCacheResult(List<Secret> secrets) {
    log.info("Describing items in ${agentType}")

    Map<String, MutableCacheData> cachedSecrets = MutableCacheData.mutableCacheMap()

    for (Secret secret : secrets) {
      if (!secret) {
        continue
      }

      def key = Keys.getSecretKey(accountName, secret.metadata.namespace, secret.metadata.name)

      cachedSecrets[key].with {
        attributes.name = secret.metadata.name
        attributes.namespace = secret.metadata.namespace
      }

    }

    log.info("Caching ${cachedSecrets.size()} secrets in ${agentType}")

    new DefaultCacheResult([
        (Keys.Namespace.SECRETS.ns): cachedSecrets.values(),
    ], [:])
  }
}
