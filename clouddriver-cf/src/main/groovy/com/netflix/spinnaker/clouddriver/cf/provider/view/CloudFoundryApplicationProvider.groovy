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

package com.netflix.spinnaker.clouddriver.cf.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.cf.CloudFoundryCloudProvider
import com.netflix.spinnaker.clouddriver.cf.cache.Keys
import com.netflix.spinnaker.clouddriver.cf.model.CloudFoundryApplication
import com.netflix.spinnaker.clouddriver.model.Application
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.cf.cache.Keys.Namespace.APPLICATIONS
import static com.netflix.spinnaker.clouddriver.cf.cache.Keys.Namespace.CLUSTERS

@Component
class CloudFoundryApplicationProvider implements ApplicationProvider {

  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  CloudFoundryApplicationProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
  }

  @Override
  Set<? extends Application> getApplications(boolean expand) {
    def relationships = expand ? RelationshipCacheFilter.include(CLUSTERS.ns) : RelationshipCacheFilter.none()
    Collection<CacheData> applications = cacheView.getAll(
        APPLICATIONS.ns, cacheView.filterIdentifiers(APPLICATIONS.ns, "${CloudFoundryCloudProvider.ID}:*"), relationships
    )
    applications.collect this.&translateApplication
  }

  @Override
  Application getApplication(String name) {
    translateApplication(cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(name)))
  }

  private Application translateApplication(CacheData cacheData) {
    if (cacheData == null) {
      return null
    }

    String name = Keys.parse(cacheData.id).application
    Map<String, String> attributes = objectMapper.convertValue(cacheData.attributes, CloudFoundryApplication.ATTRIBUTES)
    Map<String, Set<String>> clusterNames = [:].withDefault { new HashSet<String>() }
    for (String clusterId : cacheData.relationships[CLUSTERS.ns]) {
      Map<String, String> cluster = Keys.parse(clusterId)
      if (cluster.account && cluster.cluster) {
        clusterNames[cluster.account].add(cluster.cluster)
      }
    }
    new CloudFoundryApplication(name, attributes, clusterNames)
  }

}
