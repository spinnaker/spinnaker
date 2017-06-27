/*
 * Copyright 2017 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.dcos.model.DcosApplication
import com.netflix.spinnaker.clouddriver.model.Application
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.dcos.provider.DcosProviderUtils.getAllMatchingKeyPattern

@Component
class DcosApplicationProvider implements ApplicationProvider {
  private final DcosCloudProvider dcosCloudProvider
  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  DcosApplicationProvider(DcosCloudProvider dcosCloudProvider, Cache cacheView, ObjectMapper objectMapper) {
    this.dcosCloudProvider = dcosCloudProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
  }

  @Override
  Set<Application> getApplications(boolean expand) {
    def relationshipFilter = expand ? RelationshipCacheFilter.include(Keys.Namespace.CLUSTERS.ns) : RelationshipCacheFilter.none()
    Collection<CacheData> applications = getAllMatchingKeyPattern(cacheView, Keys.Namespace.APPLICATIONS.ns, "${dcosCloudProvider.id}:*", relationshipFilter)
    applications.collect { translate(it, null) }
  }

  @Override
  Application getApplication(String name) {
    translate(cacheView.get(Keys.Namespace.APPLICATIONS.ns, Keys.getApplicationKey(name)), getSecrets())
  }

  private Map<String, Collection<String>> getSecrets() {
    Collection<CacheData> secretData = getAllMatchingKeyPattern(cacheView, Keys.Namespace.SECRETS.ns, "${dcosCloudProvider.id}:*")
    Map<String, Collection<String>> secretsByCluster = [:].withDefault { key -> return [] }
    secretData.each {
      secretsByCluster[Keys.parse(it.id).dcosCluster].add(objectMapper.convertValue(it.attributes.secretPath, String.class))
    }
    secretsByCluster
  }

  Application translate(CacheData appCacheData, Map<String, Collection<String>> secretsByCluster) {
    if (appCacheData == null) {
      return null
    }

    String name = Keys.parse(appCacheData.id).application
    Map<String, String> attributes = objectMapper.convertValue(appCacheData.attributes, DcosApplication.ATTRIBUTES)
    Map<String, Set<String>> clusterNames = [:].withDefault { new HashSet<String>() }
    for (String clusterId : appCacheData.relationships[Keys.Namespace.CLUSTERS.ns]) {
      Map<String, String> cluster = Keys.parse(clusterId)
      if (cluster.account && cluster.name) {
        clusterNames[cluster.account].add(cluster.name)
      }
    }

    if (secretsByCluster) {
      attributes.put("secrets", objectMapper.writeValueAsString(secretsByCluster))
    }

    new DcosApplication(name, attributes, clusterNames)
  }
}
