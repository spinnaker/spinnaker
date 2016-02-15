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
import com.google.api.services.compute.model.Subnetwork
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.model.GoogleSubnet
import com.netflix.spinnaker.clouddriver.model.SubnetProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.SUBNETS

@Component
class GoogleSubnetProvider implements SubnetProvider<GoogleSubnet> {

  private final GoogleCloudProvider googleCloudProvider
  private final Cache cacheView
  final ObjectMapper objectMapper

  @Autowired
  GoogleSubnetProvider(GoogleCloudProvider googleCloudProvider, Cache cacheView, ObjectMapper objectMapper) {
    this.googleCloudProvider = googleCloudProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  String getType() {
    return googleCloudProvider.id
  }

  @Override
  Set<GoogleSubnet> getAll() {
    getAllMatchingKeyPattern(Keys.getSubnetKey(googleCloudProvider, '*', '*', '*'))
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
    if (!(cacheData.attributes.subnet.id instanceof BigInteger)) {
      cacheData.attributes.subnet.id = new BigInteger(cacheData.attributes.subnet.id)
    }

    Subnetwork subnet = objectMapper.convertValue(cacheData.attributes.subnet, Subnetwork)
    Map<String, String> parts = Keys.parse(googleCloudProvider, cacheData.id)

    new GoogleSubnet(
      cloudProvider: googleCloudProvider.id,
      id: subnet.name,
      name: subnet.name,
      gatewayAddress: subnet.gatewayAddress,
      network: GCEUtil.getLocalName(subnet.network),
      cidrBlock: subnet.ipCidrRange,
      account: parts.account,
      region: parts.region
    )
  }
}
