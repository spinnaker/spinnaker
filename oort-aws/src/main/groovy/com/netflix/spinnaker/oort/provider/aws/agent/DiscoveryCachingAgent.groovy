/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.oort.provider.aws.agent

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.oort.config.discovery.DiscoveryApi
import com.netflix.spinnaker.oort.model.Instance
import com.netflix.spinnaker.oort.model.discovery.DiscoveryApplication
import com.netflix.spinnaker.oort.model.discovery.DiscoveryApplications
import com.netflix.spinnaker.oort.model.discovery.DiscoveryInstance
import com.netflix.spinnaker.oort.provider.aws.AwsProvider

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE

class DiscoveryCachingAgent implements CachingAgent {

  private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  private static final Collection<AgentDataType> types = Collections.unmodifiableCollection([
    AUTHORITATIVE.forType(AwsProvider.DISCOVERY_HEALTH_TYPE),
    INFORMATIVE.forType(Instance.DATA_TYPE)
  ])

  @Override
  String getProviderName() {
    AwsProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${discoveryHost}/${region}/${DiscoveryCachingAgent.simpleName}"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  private final List<NetflixAmazonCredentials> accounts
  private final String region
  private final DiscoveryApi discoveryApi
  private final ObjectMapper objectMapper
  private final String discoveryHost

  DiscoveryCachingAgent(DiscoveryApi discoveryApi, List<NetflixAmazonCredentials> accounts, String region, ObjectMapper objectMapper) {
    this.accounts = accounts
    this.region = region
    this.discoveryApi = discoveryApi
    this.objectMapper = objectMapper
    this.discoveryHost = accounts[0].discovery.toURL().host
  }

  AwsProvider.Identifiers getIdentifiers(String accountName, String region) {
    new AwsProvider.Identifiers(accountName, region)
  }

  @Override
  CacheResult loadData() {

    DiscoveryApplications disco = discoveryApi.loadDiscoveryApplications()

    Collection<CacheData> discoveryCacheData = new LinkedList<CacheData>()
    Collection<CacheData> instanceCacheData = new LinkedList<CacheData>()

    for (DiscoveryApplication application : disco.applications) {
      for (DiscoveryInstance instance : application.instances) {
        if (instance.instanceId) {
          for (NetflixAmazonCredentials account : accounts) {
            AwsProvider.Identifiers id = getIdentifiers(account.name, region)
            Map<String, Object> attributes = objectMapper.convertValue(instance, ATTRIBUTES)
            Map<String, Collection<String>> relationships = [(Instance.DATA_TYPE):[id.instanceId(instance.instanceId)]]
            discoveryCacheData.add(new DefaultCacheData(id.instanceId(instance.instanceId), attributes, relationships))
            instanceCacheData.add(new DefaultCacheData(id.instanceId(instance.instanceId), [:], [(AwsProvider.DISCOVERY_HEALTH_TYPE):[id.instanceId(instance.instanceId)]]))
          }
        }
      }
    }

    new DefaultCacheResult(
      (Instance.DATA_TYPE): instanceCacheData,
      (AwsProvider.DISCOVERY_HEALTH_TYPE): discoveryCacheData)
  }
}
