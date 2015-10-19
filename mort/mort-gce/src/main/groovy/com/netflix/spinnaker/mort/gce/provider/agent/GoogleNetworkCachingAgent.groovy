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

package com.netflix.spinnaker.mort.gce.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.model.Network
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials
import com.netflix.spinnaker.mort.gce.cache.Keys
import com.netflix.spinnaker.mort.gce.provider.GoogleInfrastructureProvider
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE

@Slf4j
class GoogleNetworkCachingAgent implements CachingAgent {

  final GoogleCloudProvider googleCloudProvider
  final String accountName
  final GoogleCredentials credentials
  final ObjectMapper objectMapper

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(Keys.Namespace.NETWORKS.ns)
  ] as Set)

  GoogleNetworkCachingAgent(GoogleCloudProvider googleCloudProvider,
                            String accountName,
                            GoogleCredentials credentials,
                            ObjectMapper objectMapper) {
    this.googleCloudProvider = googleCloudProvider
    this.accountName = accountName
    this.credentials = credentials
    this.objectMapper = objectMapper
  }

  @Override
  String getProviderName() {
    GoogleInfrastructureProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${accountName}/global/${GoogleNetworkCachingAgent.simpleName}"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<Network> networkList = loadNetworks()

    buildCacheResult(providerCache, networkList)
  }

  List<Network> loadNetworks() {
    def compute = credentials.compute
    def project = credentials.project

    compute.networks().list(project).execute().items
  }

  private CacheResult buildCacheResult(ProviderCache providerCache, List<Network> networkList) {
    log.info("Describing items in ${agentType}")

    List<CacheData> data = networkList.collect { Network network ->
      Map<String, Object> attributes = [network: network]

      new DefaultCacheData(Keys.getNetworkKey(googleCloudProvider, network.getName(), "global", accountName),
                           attributes,
                           [:])
    }

    log.info("Caching ${data.size()} items in ${agentType}")

    new DefaultCacheResult([(Keys.Namespace.NETWORKS.ns): data])
  }
}
