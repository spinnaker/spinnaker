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

package com.netflix.spinnaker.clouddriver.titus.caching.providers
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.model.Application
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider
import com.netflix.spinnaker.clouddriver.titus.caching.Keys
import com.netflix.spinnaker.clouddriver.titus.model.TitusApplication
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.APPLICATIONS
import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.CLUSTERS

@Component
class TitusApplicationProvider implements ApplicationProvider {
  TitusCloudProvider titusCloudProvider
  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  TitusApplicationProvider(TitusCloudProvider titusCloudProvider, Cache cacheView, ObjectMapper objectMapper) {
    this.titusCloudProvider = titusCloudProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  Set<Application> getApplications(boolean expand) {
    def relationships = expand ? RelationshipCacheFilter.include(CLUSTERS.ns) : RelationshipCacheFilter.none()
    Collection<CacheData> applications = cacheView.getAll(
      APPLICATIONS.ns, cacheView.filterIdentifiers(APPLICATIONS.ns, "${titusCloudProvider.id}:*"), relationships
    )
    applications.collect this.&translate
  }

  @Override
  Application getApplication(String name) {
    translate(cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(name)))
  }

  Application translate(CacheData cacheData) {
    if (cacheData == null) {
      return null
    }
    String name = Keys.parse(cacheData.id).application
    Map<String, String> attributes = objectMapper.convertValue(cacheData.attributes, new TypeReference<Map<String, String>>() {})
    Map<String, Set<String>> clusterNames = [:].withDefault { new HashSet<String>() }
    for (String clusterId : cacheData.relationships[CLUSTERS.ns]) {
      Map<String, String> cluster = Keys.parse(clusterId)
      if (cluster.account && cluster.cluster) {
        clusterNames[cluster.account].add(cluster.cluster)
      }
    }
    new TitusApplication(name, clusterNames, attributes)
  }

}
