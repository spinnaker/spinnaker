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

package com.netflix.spinnaker.clouddriver.azure.resources.application.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider
import com.netflix.spinnaker.clouddriver.azure.resources.application.model.AzureApplication
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.Keys
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AzureApplicationProvider implements ApplicationProvider {
  private final AzureCloudProvider azureCloudProvider
  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  AzureApplicationProvider(AzureCloudProvider azureCloudProvider, Cache cacheView, ObjectMapper objectMapper) {
    this.azureCloudProvider = azureCloudProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
  }

  @Override
  Set<AzureApplication> getApplications(boolean expand) {
    def relationships = expand ? RelationshipCacheFilter.include(Keys.Namespace.AZURE_CLUSTERS.ns) : RelationshipCacheFilter.none()
    Collection<CacheData> applications = cacheView.getAll(
      Keys.Namespace.AZURE_APPLICATIONS.ns, cacheView.filterIdentifiers(Keys.Namespace.AZURE_APPLICATIONS.ns, "${azureCloudProvider.id}:*"), relationships
    )
    applications.collect this.&translate
  }

  @Override
  AzureApplication getApplication(String name) {
    translate(cacheView.get(Keys.Namespace.AZURE_APPLICATIONS.ns, Keys.getApplicationKey(azureCloudProvider, name))) ?: new AzureApplication(name, [:], [:])
  }

  AzureApplication translate(CacheData cacheData) {
    if (cacheData == null) {
      return null
    }

    String name = Keys.parse(azureCloudProvider, cacheData.id).application
    Map<String, String> attributes = objectMapper.convertValue(cacheData.attributes, AzureApplication.ATTRIBUTES)
    Map<String, Set<String>> clusterNames = [:].withDefault { new HashSet<String>() }
    for (String clusterId : cacheData.relationships[Keys.Namespace.AZURE_CLUSTERS.ns]) {
      Map<String, String> cluster = Keys.parse(azureCloudProvider, clusterId)
      if (cluster.account && cluster.name) {
        clusterNames[cluster.account].add(cluster.name)
      }
    }
    new AzureApplication(name, attributes, clusterNames)
  }

}
