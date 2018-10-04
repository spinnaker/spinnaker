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

package com.netflix.spinnaker.clouddriver.azure.resources.servergroup.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.Keys
import static com.netflix.spinnaker.clouddriver.azure.resources.common.cache.Keys.Namespace.*
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureInstance
import com.netflix.spinnaker.clouddriver.model.InstanceProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AzureInstanceProvider implements InstanceProvider<AzureInstance, String> {
  final String cloudProvider = AzureCloudProvider.ID
  private final AzureCloudProvider azureCloudProvider
  private final Cache cacheView
  final ObjectMapper objectMapper

  @Autowired
  AzureInstanceProvider(AzureCloudProvider azureCloudProvider, Cache cacheView, ObjectMapper objectMapper) {
    this.azureCloudProvider = azureCloudProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  AzureInstance getInstance(String account, String region, String id) {
    String pattern = Keys.getInstanceKey(AzureCloudProvider.ID, "*", id, region, account)
    def identifiers = cacheView.filterIdentifiers(AZURE_INSTANCES.ns, pattern)

    Set<CacheData> instances = cacheView.getAll(AZURE_INSTANCES.ns, identifiers, RelationshipCacheFilter.none())

    if (!instances || instances.size() == 0) {
      return null
    }

    if (instances.size() > 1) {
      throw new IllegalStateException("Multiple instances with same name: ${id} in the same account: ${account} and region: ${region}")
    }

    CacheData instanceData = instances.first()
    objectMapper.convertValue(instanceData.attributes.instance, AzureInstance)
  }

  @Override
  String getConsoleOutput(String account, String region, String id) {
    return null
  }

}
