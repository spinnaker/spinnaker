/*
 * Copyright 2016 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.common.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.provider.AzureInfrastructureProvider
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandType

abstract class AzureCachingAgent implements CachingAgent, OnDemandAgent, AccountAware {

  protected static ON_DEMAND_UPDATED = "OnDemandUpdated"
  protected static ON_DEMAND_EVICTED = "OnDemandEvicted"

  final AzureCloudProvider azureCloudProvider
  final String accountName
  final AzureCredentials creds
  final String region
  final ObjectMapper objectMapper

  static Set<AgentDataType> types

  AzureCachingAgent(AzureCloudProvider azureCloudProvider,
                    String accountName,
                    AzureCredentials creds,
                    String region,
                    ObjectMapper objectMapper) {
    this.azureCloudProvider = azureCloudProvider
    this.accountName = accountName
    this.creds = creds
    this.region = region
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    types = initializeTypes()
  }

  protected abstract Set<AgentDataType> initializeTypes()

  @Override
  String getProviderName() {
    AzureInfrastructureProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${accountName}/${region}/${this.class.simpleName}"
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
  String getOnDemandAgentType() {
    "${getAgentType()}-OnDemand"
  }

  @Override
  boolean handles(OnDemandType type, String cloudProvider) {
    type == getOnDemandType() && cloudProvider == azureCloudProvider.id
  }

  @Override
  abstract OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data)

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    return []
  }

  @Override
  abstract CacheResult loadData(ProviderCache providerCache)

  abstract Boolean validKeys(Map<String, ? extends Object> data)

  abstract protected OnDemandType getOnDemandType()

  def static parseOnDemandCache(Collection<CacheData> results, long lastReadTime) {
    List<String> evictions = new ArrayList<String>()
    Map<String, CacheData> usable = [:]
    results?.each {
      if(it.attributes.cachedTime < lastReadTime && it.attributes.processedCount > 0){
        evictions.add(it.id)
      } else {
        usable[it.id] = it
      }
    }
    [evictions, usable]
  }

}
