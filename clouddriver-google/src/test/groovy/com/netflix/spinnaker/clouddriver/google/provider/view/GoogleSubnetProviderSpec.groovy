package com.netflix.spinnaker.clouddriver.google.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.Subnetwork
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.cache.WriteableCache
import com.netflix.spinnaker.cats.mem.InMemoryCache
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class GoogleSubnetProviderSpec extends Specification {
  @Subject
  GoogleSubnetProvider provider

  WriteableCache cache = new InMemoryCache()
  ObjectMapper mapper = new ObjectMapper()

  def setup() {
    def accountCredentialsProvider =  new DefaultAccountCredentialsProvider()
    provider = new GoogleSubnetProvider(accountCredentialsProvider, cache, mapper)
    cache.mergeAll(Keys.Namespace.SUBNETS.ns, getAllSubnets())
  }

  void "getAll lists all"() {
    when:
    def result = provider.getAll()

    then:
    result.size() == 2
  }

  @Shared
  Map<String, List<Compute.Subnetworks>> subnetsMap = [
    'us-central1': [
      new Subnetwork(
        name: 'a',
        gatewayAddress: '10.0.0.1',
        id: 6614377178691015953,
        ipCidrRange: '10.0.0.0/24',
        network: 'https://compute.googleapis.com/compute/v1/projects/my-project/global/networks/default',
        regionUrl: 'https://compute.googleapis.com/compute/v1/projects/my-project/regions/us-central1',
        selfLink: 'https://compute.googleapis.com/compute/v1/projects/my-project/regions/us-central1/subnetworks/a'
      )
    ],
    'asia-south1':[
      new Subnetwork(
        name: 'b',
        gatewayAddress: '10.1.0.1',
        id: 6614377178691015954,
        ipCidrRange: '10.1.0.0/24',
        network: 'https://compute.googleapis.com/compute/v1/projects/my-project/global/networks/default',
        regionUrl: 'https://compute.googleapis.com/compute/v1/projects/my-project/regions/asia-south1',
        selfLink: 'https://compute.googleapis.com/compute/v1/projects/my-project/regions/us-central1/subnetworks/b'
      ),
    ]
  ]


  private List<CacheData> getAllSubnets() {
    String account = 'my-account'
    subnetsMap.collect { String regions, List<Subnetwork> region ->
      region.collect { Subnetwork subnet ->
        Map<String, Object> attributes = [subnet: subnet,project: "my-project"]
        new DefaultCacheData(Keys.getSubnetKey(subnet.getName(),"global", account), attributes, [:])
      }
    }.flatten()
  }
}
