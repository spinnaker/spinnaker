package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
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
      new ObjectMapper(),
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

}
