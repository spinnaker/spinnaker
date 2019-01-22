/*
 * Copyright 2017 Google, Inc.
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
import com.google.api.services.compute.model.Network
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.cache.WriteableCache
import com.netflix.spinnaker.cats.mem.InMemoryCache
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class GoogleNetworkProviderSpec extends Specification {

  @Subject
  GoogleNetworkProvider provider

  WriteableCache cache = new InMemoryCache()
  ObjectMapper mapper = new ObjectMapper()

  def setup() {
    provider = new GoogleNetworkProvider(cache, mapper)
    cache.mergeAll(Keys.Namespace.NETWORKS.ns, getAllNetworks())
  }

  void "getAll lists all and does not choke on deserializing routingConfig"() {
    when:
      def result = provider.getAll()

    then:
      result.size() == 2
  }

  @Shared
  List<Network> networkList = [
    [
      id: 6614377178691015954,
      name: 'some-network',
      selfLink: 'https://compute.googleapis.com/compute/alpha/projects/some-project/global/networks/some-network',
      autoCreateSubnets: Boolean.TRUE,
      subnets: ['https://compute.googleapis.com/compute/alpha/projects/some-project/regions/europe-west1/subnetworks/some-network',
                'https://compute.googleapis.com/compute/alpha/projects/some-project/regions/europe-west2/subnetworks/some-network'],
      routingConfig: [routingMode: 'GLOBAL']
    ],
    [
      id: 6614377178691015955,
      name: 'some-network-2',
      selfLink: 'https://compute.googleapis.com/compute/alpha/projects/some-project/global/networks/some-network02',
      autoCreateSubnets: Boolean.TRUE,
      subnets: ['https://compute.googleapis.com/compute/alpha/projects/some-project/regions/europe-west1/subnetworks/some-network-2',
                'https://compute.googleapis.com/compute/alpha/projects/some-project/regions/europe-west2/subnetworks/some-network-2'],
      routingConfig: [routingMode: 'GLOBAL']
    ]
  ]

  private List<CacheData> getAllNetworks() {
    networkList.collect { Map network ->
      Map<String, Object> attributes = [network: network]
      new DefaultCacheData(Keys.getNetworkKey(network.name, 'global', 'test'), attributes, [:])
    }
  }
}
