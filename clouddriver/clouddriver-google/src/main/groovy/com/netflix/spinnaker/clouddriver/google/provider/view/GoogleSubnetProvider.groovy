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

package com.netflix.spinnaker.clouddriver.google.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.model.GoogleSubnet
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.model.SubnetProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.SUBNETS

@Component
class GoogleSubnetProvider implements SubnetProvider<GoogleSubnet> {

  private final AccountCredentialsProvider accountCredentialsProvider
  private final Cache cacheView
  private final ObjectMapper objectMapper

  final String cloudProvider = GoogleCloudProvider.ID

  @Autowired
  GoogleSubnetProvider(AccountCredentialsProvider accountCredentialsProvider, Cache cacheView, ObjectMapper objectMapper) {
    this.accountCredentialsProvider = accountCredentialsProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  Set<GoogleSubnet> getAll() {
    getAllMatchingKeyPattern(Keys.getSubnetKey('*', '*', '*'))
  }

  Set<GoogleSubnet> getAllMatchingKeyPattern(String pattern) {
    loadResults(cacheView.filterIdentifiers(SUBNETS.ns, pattern))
  }

  Set<GoogleSubnet> loadResults(Collection<String> identifiers) {
    def data = cacheView.getAll(SUBNETS.ns, identifiers, RelationshipCacheFilter.none())
    def transformed = data.collect(this.&fromCacheData)

    return transformed
  }

  GoogleSubnet fromCacheData(CacheData cacheData) {
    Map subnet = cacheData.attributes.subnet
    Map<String, String> parts = Keys.parse(cacheData.id)
    def project = cacheData.attributes.project

    new GoogleSubnet(
      type: this.cloudProvider,
      id: parts.id,
      name: subnet.name,
      gatewayAddress: subnet.gatewayAddress,
      network: deriveNetworkId(project, subnet),
      cidrBlock: subnet.ipCidrRange,
      account: parts.account,
      region: parts.region,
      selfLink: subnet.selfLink,
      purpose: subnet.purpose ?: "n/a"
    )
  }

  private String deriveNetworkId(String project, Map subnet) {

    def networkProject = GCEUtil.deriveProjectId(subnet.network)
    def networkId = GCEUtil.getLocalName(subnet.network)

    if (networkProject != project) {
      networkId = "$networkProject/$networkId"
    }

    return networkId
  }
}
