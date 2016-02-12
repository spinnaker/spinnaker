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

package com.netflix.spinnaker.clouddriver.azure.resources.subnet.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.Keys
import com.netflix.spinnaker.clouddriver.azure.resources.subnet.model.AzureSubnet
import com.netflix.spinnaker.clouddriver.azure.resources.subnet.model.AzureSubnetDescription
import com.netflix.spinnaker.clouddriver.model.SubnetProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AzureSubnetProvider implements SubnetProvider<AzureSubnet> {

  private final AzureCloudProvider azureCloudProvider
  private final Cache cacheView
  final ObjectMapper objectMapper

  @Autowired
  AzureSubnetProvider(AzureCloudProvider azureCloudProvider, Cache cacheView, ObjectMapper objectMapper) {
    this.azureCloudProvider = azureCloudProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  String getCloudProvider() {
    return azureCloudProvider.id
  }

  @Override
  Set<AzureSubnet> getAll() {
    cacheView.getAll(Keys.Namespace.AZURE_SUBNETS.ns, RelationshipCacheFilter.none()).collect(this.&fromCacheData)
  }

  AzureSubnet fromCacheData(CacheData cacheData) {
    AzureSubnetDescription subnet = objectMapper.convertValue(cacheData.attributes['subnet'], AzureSubnetDescription)
    def parts = Keys.parse(azureCloudProvider, cacheData.id)

    new AzureSubnet(
      cloudProvider: "azure",
      id: subnet.id,
      name: subnet.name,
      account: parts.account?: "azure-cred1",
      region: subnet.region,
      addressPrefix: subnet.addressPrefix,
      networkSecurityGroup: subnet.networkSecurityGroup,
      vnet: subnet.vnet,
      tag: subnet.etag,
      purpose: "TBD"
    )
  }
}
