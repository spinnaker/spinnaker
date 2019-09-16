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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.v1.caching.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.v1.model.KubernetesV1Application
import com.netflix.spinnaker.clouddriver.model.Application
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.kubernetes.v1.caching.Keys.Namespace.APPLICATIONS
import static com.netflix.spinnaker.clouddriver.kubernetes.v1.caching.Keys.Namespace.CLUSTERS

@Component
class KubernetesV1ApplicationProvider implements ApplicationProvider {
  private final KubernetesCloudProvider kubernetesCloudProvider
  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  KubernetesV1ApplicationProvider(KubernetesCloudProvider kubernetesCloudProvider, Cache cacheView, ObjectMapper objectMapper) {
    this.kubernetesCloudProvider = kubernetesCloudProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
  }

  @Override
  Set<Application> getApplications(boolean expand) {
    def relationships = expand ? RelationshipCacheFilter.include(CLUSTERS.ns) : RelationshipCacheFilter.none()
    Collection<CacheData> applications = cacheView.getAll(APPLICATIONS.ns, cacheView.filterIdentifiers(APPLICATIONS.ns, "${kubernetesCloudProvider.id}:*"), relationships)
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
    Map<String, String> attributes = objectMapper.convertValue(cacheData.attributes, KubernetesV1Application.ATTRIBUTES)
    Map<String, Set<String>> clusterNames = [:].withDefault { new HashSet<String>() }
    for (String clusterId : cacheData.relationships[CLUSTERS.ns]) {
      Map<String, String> cluster = Keys.parse(clusterId)
      if (cluster.account && cluster.name) {
        clusterNames[cluster.account].add(cluster.name)
      }
    }

    new KubernetesV1Application(name, attributes, clusterNames)
  }
}
