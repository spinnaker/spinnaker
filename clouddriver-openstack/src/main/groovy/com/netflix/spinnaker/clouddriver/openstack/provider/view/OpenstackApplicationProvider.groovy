/*
 * Copyright 2016 Target Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackApplication
import com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.APPLICATIONS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.CLUSTERS

@Component
class OpenstackApplicationProvider implements ApplicationProvider {
  final Cache cacheView
  final ObjectMapper objectMapper

  @Autowired
  OpenstackApplicationProvider(final Cache cacheView, final ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  Set<OpenstackApplication> getApplications(boolean expand) {
    RelationshipCacheFilter relationships = expand ? RelationshipCacheFilter.include(CLUSTERS.ns) : RelationshipCacheFilter.none()
    Collection<CacheData> applications = cacheView.getAll(
      APPLICATIONS.ns, cacheView.filterIdentifiers(APPLICATIONS.ns, "${OpenstackCloudProvider.ID}:*"), relationships
    )
    applications.collect(this.&translate)
  }

  @Override
  OpenstackApplication getApplication(String name) {
    translate(cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(name)))
  }

  OpenstackApplication translate(CacheData cacheData) {
    OpenstackApplication result = null
    if (cacheData) {
      String name = Keys.parse(cacheData.id).application
      Map<String, String> attributes = objectMapper.convertValue(cacheData.attributes, OpenstackInfrastructureProvider.ATTRIBUTES)
      Map<String, Set<String>> clusterNames = [:].withDefault { new HashSet<String>() }
      for (String clusterId : cacheData.relationships[CLUSTERS.ns]) {
        Map<String, String> cluster = Keys.parse(clusterId)
        if (cluster.account && cluster.cluster) {
          clusterNames[cluster.account].add(cluster.cluster)
        }
      }
      result = new OpenstackApplication(name, attributes, clusterNames)
    }
    result
  }
}
