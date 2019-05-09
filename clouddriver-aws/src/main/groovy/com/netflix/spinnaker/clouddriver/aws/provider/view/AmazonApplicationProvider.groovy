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

package com.netflix.spinnaker.clouddriver.aws.provider.view

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.model.Application
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.*

@Component
class AmazonApplicationProvider implements ApplicationProvider {
  private final AmazonCloudProvider amazonCloudProvider
  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  AmazonApplicationProvider(AmazonCloudProvider amazonCloudProvider, Cache cacheView, @Qualifier("amazonObjectMapper") ObjectMapper objectMapper) {
    this.amazonCloudProvider = amazonCloudProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
  }

  @Override
  Set<Application> getApplications(boolean expand) {
    def relationships = expand ? RelationshipCacheFilter.include(CLUSTERS.ns) : RelationshipCacheFilter.none()
    Collection<CacheData> applications = cacheView.getAll(
      APPLICATIONS.ns, cacheView.filterIdentifiers(APPLICATIONS.ns, "${amazonCloudProvider.id}:*"), relationships
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
    Map<String, String> attributes = objectMapper.convertValue(cacheData.attributes, CatsApplication.ATTRIBUTES)
    Map<String, Set<String>> clusterNames = [:].withDefault { new HashSet<String>() }
    for (String clusterId : cacheData.relationships[CLUSTERS.ns]) {
      Map<String, String> cluster = Keys.parse(clusterId)
      if (cluster.account && cluster.cluster) {
        clusterNames[cluster.account].add(cluster.cluster)
      }
    }
    new CatsApplication(name, attributes, clusterNames)
  }

  private static class CatsApplication implements Application {
    public static final TypeReference<Map<String, String>> ATTRIBUTES = new TypeReference<Map<String, String>>() {}
    final String name
    final Map<String, String> attributes
    final Map<String, Set<String>> clusterNames

    CatsApplication(String name, Map<String, String> attributes, Map<String, Set<String>> clusterNames) {
      this.name = name
      this.attributes = attributes
      this.clusterNames = clusterNames
    }
  }
}
