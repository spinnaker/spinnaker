/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.model.Network
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.model.GoogleNetwork
import com.netflix.spinnaker.clouddriver.model.NetworkProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.NETWORKS

@Component
class GoogleNetworkProvider implements NetworkProvider<GoogleNetwork> {

  private final GoogleCloudProvider googleCloudProvider
  private final Cache cacheView
  final ObjectMapper objectMapper

  @Autowired
  GoogleNetworkProvider(GoogleCloudProvider googleCloudProvider, Cache cacheView, ObjectMapper objectMapper) {
    this.googleCloudProvider = googleCloudProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  String getCloudProvider() {
    return googleCloudProvider.id
  }

  @Override
  Set<GoogleNetwork> getAll() {
    getAllMatchingKeyPattern(Keys.getNetworkKey(googleCloudProvider, '*', '*', '*'))
  }

  Set<GoogleNetwork> getAllMatchingKeyPattern(String pattern) {
    loadResults(cacheView.filterIdentifiers(NETWORKS.ns, pattern))
  }

  Set<GoogleNetwork> loadResults(Collection<String> identifiers) {
    def data = cacheView.getAll(NETWORKS.ns, identifiers, RelationshipCacheFilter.none())
    def transformed = data.collect(this.&fromCacheData)

    return transformed
  }

  GoogleNetwork fromCacheData(CacheData cacheData) {
    if (!(cacheData.attributes.network.id instanceof BigInteger)) {
      cacheData.attributes.network.id = new BigInteger(cacheData.attributes.network.id)
    }

    Network network = objectMapper.convertValue(cacheData.attributes.network, Network)
    Map<String, String> parts = Keys.parse(googleCloudProvider, cacheData.id)

    new GoogleNetwork(
      cloudProvider: googleCloudProvider.id,
      id: network.name,
      name: network.name,
      account: parts.account,
      region: parts.region,
      autoCreateSubnets: network.autoCreateSubnetworks,
      subnets: network.subnetworks
    )
  }
}
