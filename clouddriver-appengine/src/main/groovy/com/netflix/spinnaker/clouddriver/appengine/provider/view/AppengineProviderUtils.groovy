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

package com.netflix.spinnaker.clouddriver.appengine.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.clouddriver.appengine.cache.Keys
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineInstance
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineLoadBalancer
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineServerGroup
import groovy.util.logging.Slf4j

@Slf4j
class AppengineProviderUtils {
  static AppengineServerGroup serverGroupFromCacheData(ObjectMapper objectMapper,
                                                       CacheData cacheData,
                                                       Set<AppengineInstance> instances) {
    AppengineServerGroup serverGroup = objectMapper.convertValue(cacheData.attributes.serverGroup, AppengineServerGroup)
    serverGroup.instances = instances
    serverGroup
  }

  static AppengineInstance instanceFromCacheData(ObjectMapper objectMapper, CacheData instanceData) {
    if (!instanceData) {
      return null
    } else {
      def instance = objectMapper.convertValue(instanceData.attributes.instance, AppengineInstance)
      def loadBalancers = instanceData.relationships[Keys.Namespace.LOAD_BALANCERS.ns]?.collect { Keys.parse(it).name } ?: []
      if (!loadBalancers) {
        log.warn("Load balancers not found for instance ${instance.name}.")
      }

      instance.loadBalancers = loadBalancers

      def serverGroup = instanceData.relationships[Keys.Namespace.SERVER_GROUPS.ns]?.collect { Keys.parse(it).name }?.first()
      if (!serverGroup) {
        log.warn("Server group not found for instance ${instance.name}.")
      }
      instance.serverGroup = serverGroup

      return instance
    }
  }

  static AppengineLoadBalancer loadBalancerFromCacheData(ObjectMapper objectMapper,
                                                         CacheData loadBalancerData,
                                                         Set<AppengineServerGroup> serverGroups) {
    def loadBalancer = objectMapper.convertValue(loadBalancerData.attributes.loadBalancer, AppengineLoadBalancer)
    loadBalancer.setLoadBalancerServerGroups(serverGroups)
    loadBalancer
  }

  static Collection<CacheData> resolveRelationshipData(Cache cacheView, CacheData source, String relationship) {
    cacheView.getAll(relationship, source?.relationships?.get(relationship) ?: [])
  }

  static Collection<CacheData> resolveRelationshipDataForCollection(Cache cacheView,
                                                                    Collection<CacheData> sources,
                                                                    String relationship,
                                                                    CacheFilter cacheFilter = null) {
    Collection<String> relationships = sources?.findResults { it.relationships[relationship] ?: []}?.flatten() ?: []
    relationships ? cacheView.getAll(relationship, relationships, cacheFilter) : []
  }

  static Map<String, Collection<CacheData>> preserveRelationshipDataForCollection(Cache cacheView,
                                                                                  Collection<CacheData> sources,
                                                                                  String relationship,
                                                                                  CacheFilter cacheFilter = null) {
    Map<String, CacheData> allData = resolveRelationshipDataForCollection(cacheView, sources, relationship, cacheFilter)
      .collectEntries { cacheData -> [(cacheData.id): cacheData] }

    sources.collectEntries { CacheData source ->
      [(source.id): source.relationships[relationship].collect { String key -> allData[key] } ]
    }
  }
}
