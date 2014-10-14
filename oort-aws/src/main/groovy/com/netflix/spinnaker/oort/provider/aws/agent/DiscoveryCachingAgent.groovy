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
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.model.discovery.DiscoveryApplication
import com.netflix.spinnaker.oort.model.discovery.DiscoveryApplications
import com.netflix.spinnaker.oort.model.discovery.DiscoveryInstance
import com.netflix.spinnaker.oort.provider.aws.AwsProvider

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.HEALTH
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.INSTANCES

class DiscoveryCachingAgent implements CachingAgent {
  public static final String PROVIDER_NAME = "discovery"

  private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  private static final Collection<AgentDataType> types = Collections.unmodifiableCollection([
    AUTHORITATIVE.forType(HEALTH.ns),
    INFORMATIVE.forType(INSTANCES.ns)
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

  @Override
  CacheResult loadData() {

    DiscoveryApplications disco = discoveryApi.loadDiscoveryApplications()

    Collection<CacheData> discoveryCacheData = new LinkedList<CacheData>()
    Collection<CacheData> instanceCacheData = new LinkedList<CacheData>()

    for (DiscoveryApplication application : disco.applications) {
      for (DiscoveryInstance instance : application.instances) {
        if (instance.instanceId) {
          for (NetflixAmazonCredentials account : accounts) {
            String instanceKey = Keys.getInstanceKey(instance.instanceId, region)
            String instanceHealthKey = Keys.getInstanceHealthKey(instance.instanceId, account.name, region, PROVIDER_NAME)
            Map<String, Object> attributes = objectMapper.convertValue(instance, ATTRIBUTES)
            Map<String, Collection<String>> relationships = [(INSTANCES.ns):[instanceKey]]
            discoveryCacheData.add(new DefaultCacheData(instanceHealthKey, attributes, relationships))
            instanceCacheData.add(new DefaultCacheData(instanceKey, [:], [(HEALTH.ns):[instanceHealthKey]]))
          }
        }
      }
    }

    new DefaultCacheResult(
      (INSTANCES.ns): instanceCacheData,
      (HEALTH.ns): discoveryCacheData)
  }
}
