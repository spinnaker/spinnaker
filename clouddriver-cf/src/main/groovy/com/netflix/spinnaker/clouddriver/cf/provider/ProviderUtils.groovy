/*
 * Copyright 2016 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cf.provider

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import org.cloudfoundry.client.lib.domain.*

class ProviderUtils {

  static CloudApplication buildNativeApplication(Map serverGroupData) {
    def nativeApplication = new CloudApplication(serverGroupData)
    nativeApplication.meta = mapToMeta(serverGroupData.meta)
    nativeApplication.space = new CloudSpace(
        mapToMeta(serverGroupData.space.meta),
        serverGroupData.space.name,
        new CloudOrganization(
            mapToMeta(serverGroupData.space.organization.meta),
            serverGroupData.space.organization.name
        )
    )
    nativeApplication
  }

  static CloudService buildNativeService(Map serviceData) {
    def nativeService = new CloudService(mapToMeta(serviceData.meta), serviceData.name)
    nativeService.label = serviceData.label
    nativeService.plan = serviceData.plan
    nativeService
  }

  static InstanceInfo buildNativeInstance(Map instanceInfo) {
    def results = new InstanceInfo([
      since: instanceInfo.since / 1000,
      index: instanceInfo.index,
      state: instanceInfo.state
    ])
    results
  }

  static CloudEntity.Meta mapToMeta(Object meta) {
    if (meta instanceof CloudEntity.Meta) {
      return meta
    } else if (meta instanceof Map) {
      return new CloudEntity.Meta(
          UUID.fromString(meta.guid),
          meta?.created ? new Date(meta.created) : null,
          meta?.updated ? new Date(meta.updated) : null,
          meta.url
      )
    } else {
      throw new RuntimeException('Cannot handle mapping ' + meta.class.canonicalName)
    }
  }

  static Collection<CacheData> resolveRelationshipDataForCollection(Cache cacheView, Collection<CacheData> sources, String relationship, CacheFilter cacheFilter = null) {
    Collection<String> relationships = sources?.findResults { it.relationships[relationship]?: [] }?.flatten() ?: []
    relationships ? cacheView.getAll(relationship, relationships, cacheFilter) : []
  }

  static Collection<CacheData> resolveRelationshipData(Cache cacheView, CacheData source, String relationship) {
    resolveRelationshipData(cacheView, source, relationship) { true }
  }

  static Collection<CacheData> resolveRelationshipData(Cache cacheView, CacheData source, String relationship, Closure<Boolean> relFilter) {
    Collection<String> filteredRelationships = source.relationships[relationship]?.findAll(relFilter)
    filteredRelationships ? cacheView.getAll(relationship, filteredRelationships) : []
  }


}
