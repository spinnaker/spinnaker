package com.netflix.spinnaker.clouddriver.google.provider.agent

import tools.jackson.databind.ObjectMapper
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.Subnetwork
import com.google.api.services.compute.model.SubnetworkList
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Specification
import spock.lang.Subject
import tools.jackson.databind.json.JsonMapper

class GoogleSubnetCachingAgentSpec extends Specification {
  static final String PROJECT_NAME = "my-project"
  static final String REGION = 'us-east1'
  static final String ACCOUNT_NAME = 'some-account-name'

  void "should add subnets and cache project name as an attribute to cacheData"() {
    setup:
    def registry = new DefaultRegistry()
    def computeMock = Mock(Compute)
    def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).name(ACCOUNT_NAME).compute(computeMock).build()
    def subnetsMock = Mock(Compute.Subnetworks)
    def subnetworksListMock = Mock(Compute.Subnetworks.List)
    def subnetA = new Subnetwork(name: 'name-a',
      selfLink: 'https://compute.googleapis.com/compute/v1/projects/my-project/us-east1/subnetworks/name-a')
    def keyGroupA = Keys.getSubnetKey(subnetA.name as String,
      REGION,
      ACCOUNT_NAME)
    def SubnetsListReal = new SubnetworkList(items: [subnetA])
    def ProviderCache providerCache = Mock(ProviderCache)
    @Subject GoogleSubnetCachingAgent agent = new GoogleSubnetCachingAgent("testApplicationName",
      credentials,
      JsonMapper.builder().build(),
      registry,REGION)

    when:
    def cache = agent.loadData(providerCache)

    then:
    1 * computeMock.subnetworks() >> subnetsMock
    1 * subnetsMock.list(PROJECT_NAME,REGION) >> subnetworksListMock
    1 * subnetworksListMock.execute() >> SubnetsListReal
    def cd = cache.cacheResults.get(Keys.Namespace.SUBNETS.ns)
    cd.id.containsAll([keyGroupA])
    with(cd.asList().get(0)){
      def attributes = it.attributes
      attributes.project == "my-project"
      attributes.subnet.name ==  "name-a"
      attributes.subnet.selfLink ==  "https://compute.googleapis.com/compute/v1/projects/my-project/us-east1/subnetworks/name-a"
    }
  }

  void "should still report the SUBNETS namespace with an empty list when there are no live subnets"() {
    // Regression test: the last live subnet in a namespace being deleted must still evict its
    // stale cache entry, not leave it stuck forever. That only happens if the namespace's key is
    // present in the CacheResult even when nothing was found this cycle -- see
    // CacheResultBuilder's dataTypes-arg constructor and GoogleInfrastructureProvider's
    // ProviderCacheConfiguration opt-in.
    setup:
    def registry = new DefaultRegistry()
    def computeMock = Mock(Compute)
    def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).name(ACCOUNT_NAME).compute(computeMock).build()
    def subnetsMock = Mock(Compute.Subnetworks)
    def subnetworksListMock = Mock(Compute.Subnetworks.List)
    def emptySubnetsListReal = new SubnetworkList(items: [])
    def ProviderCache providerCache = Mock(ProviderCache)
    @Subject GoogleSubnetCachingAgent agent = new GoogleSubnetCachingAgent("testApplicationName",
      credentials,
      JsonMapper.builder().build(),
      registry, REGION)

    when:
    def cache = agent.loadData(providerCache)

    then:
    1 * computeMock.subnetworks() >> subnetsMock
    1 * subnetsMock.list(PROJECT_NAME, REGION) >> subnetworksListMock
    1 * subnetworksListMock.execute() >> emptySubnetsListReal
    cache.cacheResults.containsKey(Keys.Namespace.SUBNETS.ns)
    cache.cacheResults.get(Keys.Namespace.SUBNETS.ns).isEmpty()
  }

}
