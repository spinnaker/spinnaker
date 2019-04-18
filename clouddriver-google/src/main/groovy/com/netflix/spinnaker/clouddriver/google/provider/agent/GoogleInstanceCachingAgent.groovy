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

package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.google.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstance
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.CLUSTERS
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.SERVER_GROUPS

@Slf4j
@InheritConstructors
class GoogleInstanceCachingAgent extends AbstractGoogleCachingAgent {
  final Set<AgentDataType> providedDataTypes = [
      AUTHORITATIVE.forType(INSTANCES.ns),
      INFORMATIVE.forType(SERVER_GROUPS.ns),
      INFORMATIVE.forType(CLUSTERS.ns)
  ]

  String agentType = "${accountName}/global/${GoogleInstanceCachingAgent.simpleName}"

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<GoogleInstance> instances = GCEUtil.fetchInstances(this, credentials)
    buildCacheResults(providerCache, instances)
  }

  CacheResult buildCacheResults(ProviderCache providerCache, List<GoogleInstance> googleInstances) {
    CacheResultBuilder cacheResultBuilder = new CacheResultBuilder()

    googleInstances.each { GoogleInstance instance ->
      def moniker = instance.view.moniker
      def clusterKey = Keys.getClusterKey(accountName, moniker.app, moniker.cluster)
      def instanceKey = Keys.getInstanceKey(accountName, instance.region, instance.name)
      cacheResultBuilder.namespace(INSTANCES.ns).keep(instanceKey).with {
        attributes = objectMapper.convertValue(instance, ATTRIBUTES)
        relationships[CLUSTERS.ns].add(clusterKey)
      }
    }

    log.debug("Caching ${cacheResultBuilder.namespace(INSTANCES.ns).keepSize()} instances in ${agentType}")

    cacheResultBuilder.build()
  }
}
