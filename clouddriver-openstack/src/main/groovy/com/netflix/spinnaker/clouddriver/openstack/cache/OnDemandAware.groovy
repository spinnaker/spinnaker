/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.cache

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent.OnDemandResult
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.openstack.provider.view.MutableCacheData

import java.util.concurrent.TimeUnit

import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.ON_DEMAND


trait OnDemandAware {

  /**
   * Helper method to inspect onDemand 'toKeep' cache to see if the cacheData should be used
   * instead of recreating it.
   * @param cacheResultBuilder
   * @param key
   * @return
   */
  boolean shouldUseOnDemandData(CacheResultBuilder cacheResultBuilder, String key) {
    CacheData cacheData = cacheResultBuilder.onDemand.toKeep[key]
    cacheData ? cacheData.attributes.cacheTime >= cacheResultBuilder.startTime : false
  }

  /**
   * Generically will move cached data from onDemand to resource (Server Group, Load Balancer, Security Group) namespace.
   * @param objectMapper
   * @param cacheResultBuilder
   * @param serverGroupKey
   */
  void moveOnDemandDataToNamespace(ObjectMapper objectMapper, TypeReference<Map<String, List<MutableCacheData>>> typeReference, CacheResultBuilder cacheResultBuilder, String key) {
    String cacheResults = cacheResultBuilder.onDemand.toKeep[key].attributes.cacheResults as String
    Map<String, List<MutableCacheData>> onDemandData = objectMapper.readValue(cacheResults, typeReference)

    onDemandData.each { String namespace, List<MutableCacheData> cacheDatas ->
      cacheDatas.each { MutableCacheData cacheData ->
        cacheResultBuilder.namespace(namespace).keep(cacheData.id).with {
          it.attributes = cacheData.attributes
          it.relationships = cacheData.relationships
        }
        cacheResultBuilder.onDemand.toKeep.remove(cacheData.id)
      }
    }
  }

  /**
   * Finds all on demand cache data for a given account and region.
   * @param providerCache
   * @param accountName
   * @param region
   * @return
   */
  Collection<Map> getAllOnDemandCacheByRegionAndAccount(ProviderCache providerCache, String accountName, String region) {
    Collection<String> keys = providerCache.getIdentifiers(ON_DEMAND.ns).findAll { String key ->
      Map<String, String> parsedKey = Keys.parse(key)
      parsedKey && parsedKey.account == accountName && parsedKey.region == region
    }

    providerCache.getAll(ON_DEMAND.ns, keys).collect { CacheData cacheData ->
      [
        details       : Keys.parse(cacheData.id),
        cacheTime     : cacheData.attributes.cacheTime,
        processedCount: cacheData.attributes.processedCount,
        processedTime : cacheData.attributes.processedTime
      ]
    }
  }

  /**
   * Builds on demand cache result based upon the cache result.
   * @param onDemandAgentType
   * @param cacheResult
   * @param namespace
   * @param key
   * @param providerCache
   * @return
   */
  OnDemandResult buildOnDemandCache(Object cachedItem, String onDemandAgentType, CacheResult cacheResult, String namespace, String key) {
    OnDemandResult result = new OnDemandResult(sourceAgentType: onDemandAgentType, cacheResult: cacheResult, evictions: [:].withDefault { _ -> [] })

    if (!cachedItem && key) {
      // Evict this cached item if it no longer exists.
      result.evictions[namespace] << key
    }

    result
  }

  /**
   * Resolves a key that contains a wildcard but still returns a unique result.
   * @param providerCache
   * @param namespace
   * @param key
   * @return
   */
  String resolveKey(ProviderCache providerCache, String namespace, String key) {
    String result = key
    if (key.contains('*')) {
      Collection<String> identifiers = providerCache.filterIdentifiers(namespace, key)
      if (identifiers && identifiers.size() == 1) {
        result = identifiers.first()
      } else {
        throw new UnresolvableKeyException("Unable to resolve ${key}")
      }
    }
    result
  }

  /**
   * Processes the on-demand cache by removing the record if the cache result is empty or
   * by adding the cache record to ON_DEMAND namespace.
   * @param cacheResult
   * @param objectMapper
   * @param metricsSupport
   * @param providerCache
   * @param namespace
   * @param key
   * @return
   */
  void processOnDemandCache(CacheResult cacheResult, ObjectMapper objectMapper, OnDemandMetricsSupport metricsSupport, ProviderCache providerCache, String key) {

    if (cacheResult.cacheResults.values().flatten().isEmpty()) {
      // Avoid writing an empty onDemand cache record (instead delete any that may have previously existed).
      providerCache.evictDeletedItems(ON_DEMAND.ns, [key])
    } else {
      metricsSupport.onDemandStore {
        CacheData cacheData = new DefaultCacheData(
          key,
          TimeUnit.MINUTES.toSeconds(10) as Integer, // ttl
          [
            cacheTime     : System.currentTimeMillis(),
            cacheResults  : objectMapper.writeValueAsString(cacheResult.cacheResults),
            processedCount: 0,
            processedTime : null
          ],
          [:]
        )

        providerCache.putCacheData(ON_DEMAND.ns, cacheData)
      }
    }
  }

  /**
   * Method is a template for handling ON_DEMAND cache normal load data scenarios.  It will
   * check to see that ON_DEMAND data is evicted or keep based upon timestamp and process count.  At the end of processing
   * it will set processing time and increment count by 1.
   * @param providerCache
   * @param keys
   * @param cacheResultClosure
   * @return
   */
  CacheResult buildLoadDataCache(ProviderCache providerCache, List<String> keys, Closure<CacheResult> cacheResultClosure) {
    CacheResultBuilder cacheResultBuilder = new CacheResultBuilder(startTime: System.currentTimeMillis())

    providerCache.getAll(ON_DEMAND.ns, keys).each { CacheData cacheData ->
      // Ensure that we don't overwrite data that was inserted by the `handle` method while we retrieved the
      // replication controllers. Furthermore, cache data that hasn't been processed needs to be updated in the ON_DEMAND
      // cache, so don't evict data without a processedCount > 0.
      if (cacheData.attributes.cacheTime < cacheResultBuilder.startTime && cacheData.attributes.processedCount > 0) {
        cacheResultBuilder.onDemand.toEvict << cacheData.id
      } else {
        cacheResultBuilder.onDemand.toKeep[cacheData.id] = cacheData
      }
    }

    CacheResult result = cacheResultClosure.call(cacheResultBuilder)

    result.cacheResults[ON_DEMAND.ns].each {
      it.attributes.processedTime = System.currentTimeMillis()
      it.attributes.processedCount = (it.attributes.processedCount ?: 0) + 1
    }

    result
  }
}
