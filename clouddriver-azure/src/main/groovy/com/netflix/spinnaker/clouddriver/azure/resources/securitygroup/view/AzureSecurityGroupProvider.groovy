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

package com.netflix.spinnaker.clouddriver.azure.resources.securitygroup.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.Keys
import com.netflix.spinnaker.clouddriver.azure.resources.securitygroup.model.AzureSecurityGroup
import com.netflix.spinnaker.clouddriver.azure.resources.securitygroup.model.AzureSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.model.SecurityGroupProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AzureSecurityGroupProvider implements SecurityGroupProvider<AzureSecurityGroup> {

  private final AzureCloudProvider azureCloudProvider
  private final Cache cacheView
  final ObjectMapper objectMapper

  @Autowired
  AzureSecurityGroupProvider(AzureCloudProvider azureCloudProvider, Cache cacheView, ObjectMapper objectMapper) {
    this.azureCloudProvider = azureCloudProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  String getCloudProvider() {
    return azureCloudProvider.id
  }

  @Override
  String getType() {
    return azureCloudProvider.id
  }

  @Override
  Set<AzureSecurityGroup> getAll(boolean includeRules) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(azureCloudProvider, '*', '*', '*', '*'), includeRules)
  }

  @Override
  Set<AzureSecurityGroup> getAllByRegion(boolean includeRules, String region) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(azureCloudProvider, '*', '*', region, '*'), includeRules)
  }

  @Override
  Set<AzureSecurityGroup> getAllByAccount(boolean includeRules, String account) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(azureCloudProvider, '*', '*', '*', account), includeRules)
  }

  @Override
  Set<AzureSecurityGroup> getAllByAccountAndName(boolean includeRules, String account, String name) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(azureCloudProvider, name, '*', '*', account), includeRules)
  }

  @Override
  Set<AzureSecurityGroup> getAllByAccountAndRegion(boolean includeRules, String account, String region) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(azureCloudProvider, '*', '*', region, account), includeRules)
  }

  @Override
  AzureSecurityGroup get(String account, String region, String name, String vnet) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(azureCloudProvider, name, '*', region, account), true)[0]
  }

  Set<AzureSecurityGroup> getAllMatchingKeyPattern(String pattern, boolean includeRules) {
    loadResults(includeRules, cacheView.filterIdentifiers(Keys.Namespace.SECURITY_GROUPS.ns, pattern))
  }

  Set<AzureSecurityGroup> loadResults(boolean includeRules, Collection<String> identifiers) {
    def transform = this.&fromCacheData.curry(includeRules)
    def data = cacheView.getAll(Keys.Namespace.SECURITY_GROUPS.ns, identifiers, RelationshipCacheFilter.none())
    def transformed = data.collect(transform)

    return transformed
  }

  AzureSecurityGroup fromCacheData(boolean includeRules, CacheData cacheData) {
    AzureSecurityGroupDescription sg = objectMapper.convertValue(cacheData.attributes['securitygroup'], AzureSecurityGroupDescription)
    def parts = Keys.parse(azureCloudProvider, cacheData.id)

    // TODO Check if we can skip returning the security rules

    new AzureSecurityGroup(
      type: "azure",
      id: sg.name,
      name: sg.name,
      account: parts.account?: "none",
      accountName: parts.account?: "none",
      application: parts.application?: sg.name,
      region: sg.location,
      network: "na",
      tags: sg.tags,
      subnets: sg.subnets,
      inboundRules: [],
      outboundRules: [],
      securityRules: sg.securityRules
    )
  }

}
