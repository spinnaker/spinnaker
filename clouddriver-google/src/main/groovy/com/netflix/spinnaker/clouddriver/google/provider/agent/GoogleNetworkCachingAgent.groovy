/*
 * Copyright 2015 Google, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.Network
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.NETWORKS

@Slf4j
class GoogleNetworkCachingAgent extends AbstractGoogleCachingAgent {

  final Set<AgentDataType> providedDataTypes = [
      AUTHORITATIVE.forType(NETWORKS.ns)
  ] as Set

  String agentType = "${accountName}/global/${GoogleNetworkCachingAgent.simpleName}"

  GoogleNetworkCachingAgent(GoogleCloudProvider googleCloudProvider,
                            String googleApplicationName,
                            String accountName,
                            String project,
                            Compute compute,
                            ObjectMapper objectMapper) {
    this.googleCloudProvider = googleCloudProvider
    this.googleApplicationName = googleApplicationName
    this.accountName = accountName
    this.project = project
    this.compute = compute
    this.objectMapper = objectMapper
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<Network> networkList = loadNetworks()
    buildCacheResult(providerCache, networkList)
  }

  List<Network> loadNetworks() {
    compute.networks().list(project).execute().items as List
  }

  private CacheResult buildCacheResult(ProviderCache _, List<Network> networkList) {
    log.info("Describing items in ${agentType}")

    def cacheResultBuilder = new CacheResultBuilder()

    networkList.each { Network network ->
      def networkKey = Keys.getNetworkKey(googleCloudProvider, network.getName(), "global", accountName)

      cacheResultBuilder.namespace(NETWORKS.ns).get(networkKey).with {
        attributes.network = network
      }
    }

    log.info("Caching ${cacheResultBuilder.namespace(NETWORKS.ns).size()} items in ${agentType}")

    cacheResultBuilder.build()
  }
}
