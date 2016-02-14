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

package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.discovery.DiscoveryApi
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.clouddriver.aws.model.discovery.DiscoveryApplication
import com.netflix.spinnaker.clouddriver.aws.model.discovery.DiscoveryApplications
import com.netflix.spinnaker.clouddriver.aws.model.discovery.DiscoveryInstance
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import groovy.util.logging.Slf4j
import java.util.regex.Pattern

import static com.netflix.spinnaker.clouddriver.aws.data.Keys.Namespace.HEALTH
import static com.netflix.spinnaker.clouddriver.aws.data.Keys.Namespace.INSTANCES

@Slf4j
class DiscoveryCachingAgent extends AgentSchedulerAware implements CachingAgent, HealthProvidingCachingAgent {
  private final Set<NetflixAmazonCredentials> accounts
  private final String region
  private final DiscoveryApi discoveryApi
  private final ObjectMapper objectMapper
  private final String discoveryHost
  final String healthId = "discovery"
  private final Map<String, NetflixAmazonCredentials> accountIdLookup

  DiscoveryCachingAgent(DiscoveryApi discoveryApi, Set<NetflixAmazonCredentials> accounts, String region, ObjectMapper objectMapper) {
    this.accounts = accounts
    this.region = region
    this.discoveryApi = discoveryApi
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    this.discoveryHost = accounts[0].discovery.replaceAll(Pattern.quote('{{region}}'), region)
    accountIdLookup = accounts.collectEntries { [(it.accountId):it] }
  }

  Set<NetflixAmazonCredentials> getAccounts() {
    accounts
  }

  @Override
  String getProviderName() {
    AwsProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${discoveryHost}/${DiscoveryCachingAgent.simpleName}"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in ${agentType}")
    DiscoveryApplications disco = discoveryApi.loadDiscoveryApplications()

    Collection<CacheData> discoveryCacheData = new LinkedList<CacheData>()
    Collection<CacheData> instanceCacheData = new LinkedList<CacheData>()

    for (DiscoveryApplication application : disco.applications) {
      for (DiscoveryInstance instance : application.instances) {
        if (instance.instanceId && instance.accountId) {
          def account = accountIdLookup[instance.accountId]
          if (account) {
            String instanceKey = Keys.getInstanceKey(instance.instanceId, account.name, region)
            String instanceHealthKey = Keys.getInstanceHealthKey(instance.instanceId, account.name, region, healthId)
            Map<String, Object> attributes = objectMapper.convertValue(instance, ATTRIBUTES)
            Map<String, Collection<String>> relationships = [(INSTANCES.ns): [instanceKey]]
            discoveryCacheData.add(new DefaultCacheData(instanceHealthKey, attributes, relationships))
            instanceCacheData.add(new DefaultCacheData(instanceKey, [:], [(HEALTH.ns): [instanceHealthKey]]))
          }
        }
      }
    }
    log.info("Caching ${discoveryCacheData.size()} items in ${agentType}")
    new DefaultCacheResult(
      (INSTANCES.ns): instanceCacheData,
      (HEALTH.ns): discoveryCacheData)
  }
}
