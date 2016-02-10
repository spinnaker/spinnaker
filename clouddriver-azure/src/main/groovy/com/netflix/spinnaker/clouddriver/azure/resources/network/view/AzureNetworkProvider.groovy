/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.network.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.Keys
import com.netflix.spinnaker.clouddriver.azure.resources.network.model.AzureNetwork
import com.netflix.spinnaker.clouddriver.azure.resources.network.model.AzureVirtualNetworkDescription
import com.netflix.spinnaker.clouddriver.model.NetworkProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AzureNetworkProvider implements NetworkProvider<AzureNetwork> {

  private final AzureCloudProvider azureCloudProvider
  private final Cache cacheView
  final ObjectMapper objectMapper

  @Autowired
  AzureNetworkProvider(AzureCloudProvider azureCloudProvider, Cache cacheView, ObjectMapper objectMapper) {
    this.azureCloudProvider = azureCloudProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  String getCloudProvider() {
    return azureCloudProvider.id
  }

  @Override
  Set<AzureNetwork> getAll() {
    cacheView.getAll(Keys.Namespace.AZURE_NETWORKS.ns, RelationshipCacheFilter.none()).collect(this.&fromCacheData)
  }

  AzureNetwork fromCacheData(CacheData cacheData) {
    AzureVirtualNetworkDescription vnet = objectMapper.convertValue(cacheData.attributes['network'], AzureVirtualNetworkDescription)
    def parts = Keys.parse(azureCloudProvider, cacheData.id)

    new AzureNetwork(
      cloudProvider: "azure",
      id: vnet.name,
      name: vnet.name,
      account: parts.account?: "none",
      region: vnet.region
    )
  }
}
