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

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.model.Subnetwork
import com.netflix.spinnaker.cats.agent.*
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.provider.GoogleInfrastructureProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE

@Slf4j
class GoogleSubnetCachingAgent implements CachingAgent, AccountAware {

  final String region

  final GoogleCloudProvider googleCloudProvider
  final String accountName
  final GoogleCredentials credentials
  final ObjectMapper objectMapper

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(Keys.Namespace.SUBNETS.ns)
  ] as Set)

  GoogleSubnetCachingAgent(GoogleCloudProvider googleCloudProvider,
                           String accountName,
                           String region,
                           GoogleCredentials credentials,
                           ObjectMapper objectMapper) {
    this.googleCloudProvider = googleCloudProvider
    this.accountName = accountName
    this.region = region
    this.credentials = credentials
    this.objectMapper = objectMapper
  }

  @Override
  String getProviderName() {
    GoogleInfrastructureProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "$accountName/$region/$GoogleSubnetCachingAgent.simpleName"
  }

  @Override
  String getAccountName() {
    accountName
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<Subnetwork> subnetList = loadSubnets()

    buildCacheResult(providerCache, subnetList)
  }

  List<Subnetwork> loadSubnets() {
    def compute = credentials.compute
    def project = credentials.project

    compute.subnetworks().list(project, region).execute().items
  }

  private CacheResult buildCacheResult(ProviderCache providerCache, List<Subnetwork> subnetList) {
    log.info("Describing items in ${agentType}")

    List<CacheData> data = subnetList.collect { Subnetwork subnet ->
      Map<String, Object> attributes = [subnet: subnet]

      new DefaultCacheData(Keys.getSubnetKey(googleCloudProvider, subnet.getName(), region, accountName),
                           attributes,
                           [:])
    }

    log.info("Caching ${data.size()} items in ${agentType}")

    new DefaultCacheResult([(Keys.Namespace.SUBNETS.ns): data])
  }
}
