package com.netflix.spinnaker.clouddriver.dcos.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerLbId
import com.netflix.spinnaker.clouddriver.dcos.provider.MutableCacheData
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.App
import mesosphere.marathon.client.model.v2.GetAppNamespaceResponse
import spock.lang.Specification

class DcosLoadBalancerCachingAgentSpec extends Specification {
  static final private String ACCOUNT = "testaccount"
  static final private String APP = "testapp"
  static final private String REGION = "us-test-1"
  static final private String CLUSTER = "${APP}-cluster"
  static final private String LOAD_BALANCER_NAME = "${CLUSTER}-v000"
  static final private String MARATHON_APP_ID = "/${ACCOUNT}/${LOAD_BALANCER_NAME}"
  DcosAccountCredentials credentials
  AccountCredentialsRepository accountCredentialsRepository

  DcosLoadBalancerCachingAgent subject
  private DcosClientProvider clientProvider
  private DCOS dcosClient
  private String loadBalancerKey
  ProviderCache providerCache
  private ObjectMapper objectMapper
  private registryMock


  def setup() {
    registryMock = Mock(Registry)
    registryMock.get('id') >> 'id'
    registryMock.timer(_, _) >> Mock(com.netflix.spectator.api.Timer)
    accountCredentialsRepository = Mock(AccountCredentialsRepository)
    credentials = Stub(DcosAccountCredentials)
    dcosClient = Mock(DCOS)
    providerCache = Mock(ProviderCache)
    objectMapper = new ObjectMapper()

    loadBalancerKey = Keys.getLoadBalancerKey(DcosSpinnakerLbId.parseVerbose(MARATHON_APP_ID).get(), REGION)

    clientProvider = Mock(DcosClientProvider) {
      getDcosClient(credentials, REGION) >> dcosClient
    }


    subject = new DcosLoadBalancerCachingAgent(ACCOUNT, REGION, credentials, clientProvider, objectMapper, registryMock)
  }

  void "On-demand cache should cache a single load balancer"() {
    given:
    dcosClient.maybeApp(MARATHON_APP_ID) >> Optional.of(Mock(App) { getId() >> MARATHON_APP_ID })

    when:
    def result = subject.handle(providerCache, ["loadBalancerName": LOAD_BALANCER_NAME, "account": ACCOUNT, "region": REGION])

    then:
    1 * providerCache.putCacheData(Keys.Namespace.ON_DEMAND.ns, { cacheData ->
      assert cacheData.id == loadBalancerKey
      assert cacheData.ttlSeconds == 600
      assert cacheData.attributes.cacheResults.length() > 0
      true
    })

    result.evictions == [:]
  }

  void "On-demand cache should evict a server group that no longer exists"() {
    given:
    dcosClient.maybeApp(MARATHON_APP_ID) >> Optional.empty()

    when:
    def result = subject.handle(providerCache, ["loadBalancerName": LOAD_BALANCER_NAME, "account": ACCOUNT, "region": REGION])

    then:
    1 * providerCache.evictDeletedItems(Keys.Namespace.ON_DEMAND.ns, [loadBalancerKey])
    result.evictions == [(Keys.Namespace.LOAD_BALANCERS.ns): [loadBalancerKey]]
    result.cacheResult.cacheResults.values().flatten() == []
  }

  void "Newer On-demand cache items should not be replaced by an older version"() {
    setup:

    def start = System.currentTimeMillis()

    def onDemandLoadBalancer = Mock(App) {
      getId() >> MARATHON_APP_ID
      getLabels() >> ["SPINNAKER_LOAD_BALANCER": LOAD_BALANCER_NAME, "tag": "onDemand"]
    }

    def retrievedLoadBalancer = Mock(App) {
      getId() >> MARATHON_APP_ID
      getLabels() >> ["SPINNAKER_LOAD_BALANCER": LOAD_BALANCER_NAME, "tag": "retrieved"]
    }

    GetAppNamespaceResponse appsInAccount = Mock(GetAppNamespaceResponse) {
      getApps() >> [retrievedLoadBalancer]
    }

    def cacheData = Stub(CacheData) {
      getId() >> loadBalancerKey
      getAttributes() >> [
              "cacheTime"     : Long.MAX_VALUE,
              "cacheResults"  : cacheResultsJson(onDemandLoadBalancer),
              "processedCount": 0]
    }
    providerCache.getAll(Keys.Namespace.ON_DEMAND.ns, [loadBalancerKey]) >> [cacheData]
    dcosClient.maybeApps(ACCOUNT) >> Optional.of(appsInAccount)

    when:
    final result = subject.loadData(providerCache)

    then:
    // verify that the cache keeps the version of the app loaded by ondemand (indicated by the onDemand "tag" label)
    // instead of overwriting with the version returned from get apps
    result.cacheResults.loadBalancers[0].attributes.app.labels.tag == "onDemand"
    result.cacheResults.onDemand[0].attributes.processedTime > start
    result.cacheResults.onDemand[0].attributes.processedCount == 1
  }

  void "Older on-demand cache items should be replaced by newer versions"() {
    setup:

    def onDemandLoadBalancer = Mock(App) {
      getId() >> MARATHON_APP_ID
      getLabels() >> ["SPINNAKER_LOAD_BALANCER": LOAD_BALANCER_NAME, "tag": "onDemand"]
    }

    def retrievedLoadBalancer = Mock(App) {
      getId() >> MARATHON_APP_ID
      getLabels() >> ["SPINNAKER_LOAD_BALANCER": LOAD_BALANCER_NAME, "tag": "retrieved"]
    }

    GetAppNamespaceResponse appsInAccount = Mock(GetAppNamespaceResponse) {
      getApps() >> [retrievedLoadBalancer]
    }

    def cacheData = Stub(CacheData) {
      getId() >> loadBalancerKey
      getAttributes() >> [
              "cacheTime"     : Long.MIN_VALUE,
              "cacheResults"  : cacheResultsJson(onDemandLoadBalancer),
              "processedCount": 1]
    }
    providerCache.getAll(Keys.Namespace.ON_DEMAND.ns, [loadBalancerKey]) >> [cacheData]
    dcosClient.maybeApps(ACCOUNT) >> Optional.of(appsInAccount)

    when:
    final result = subject.loadData(providerCache)

    then:
    result.cacheResults.loadBalancers[0].attributes.app.labels.tag == "retrieved"
    result.evictions.onDemand[0] == loadBalancerKey
  }

  void "Should cache marathon apps with the SPINNAKER_LOAD_BALANCER label as load balancers"() {
    setup:

    def loadBalancer = Mock(App) {
      getId() >> MARATHON_APP_ID
      getLabels() >> ["SPINNAKER_LOAD_BALANCER": LOAD_BALANCER_NAME]
    }

    GetAppNamespaceResponse appsInAccount = Mock(GetAppNamespaceResponse) {
      getApps() >> [loadBalancer]
    }

    dcosClient.maybeApps(ACCOUNT) >> Optional.of(appsInAccount)
    def providerCacheMock = Mock(ProviderCache)
    providerCacheMock.getAll(_, _) >> []
    when:
    final result = subject.loadData(providerCacheMock)
    then:

    result.cacheResults.loadBalancers[0].attributes.name == MARATHON_APP_ID
    result.cacheResults.loadBalancers[0].attributes.app == loadBalancer
  }

  def cacheResultsJson(App loadBalancer) {
    def cacheData = MutableCacheData.mutableCacheMap()
    cacheData[loadBalancerKey].with {
      attributes.name = MARATHON_APP_ID
      attributes.app = loadBalancer
    }
    def result = new DefaultCacheResult([
            (Keys.Namespace.LOAD_BALANCERS.ns): cacheData.values()
    ], [:])
    new ObjectMapper().writeValueAsString(result.cacheResults)
  }
}
