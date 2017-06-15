package com.netflix.spinnaker.clouddriver.dcos.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerAppId
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerLbId
import com.netflix.spinnaker.clouddriver.dcos.provider.MutableCacheData
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.App
import mesosphere.marathon.client.model.v2.GetAppNamespaceResponse
import mesosphere.marathon.client.model.v2.GetAppResponse
import mesosphere.marathon.client.model.v2.Task
import spock.lang.Specification

class DcosServerGroupCachingAgentSpec extends Specification {
  static final private String ACCOUNT = "testaccount"
  static final private String APP = "testapp"
  static final private String CLUSTER = "${APP}-cluster"
  static final private String SERVER_GROUP = "${CLUSTER}-v000"
  static final private String GROUP = "default"
  static final private String REGION = "us-west-1"
  static final private String MARATHON_APP = "/${ACCOUNT}/${GROUP}/${SERVER_GROUP}"
  static final private String TASK = "${MARATHON_APP}-some-task-id"
  static final private String LOAD_BALANCER = "/${ACCOUNT}/${APP}-frontend"

  DcosAccountCredentials credentials
  AccountCredentialsRepository accountCredentialsRepository

  DcosServerGroupCachingAgent subject
  private DcosClientProvider clientProvider
  private DCOS dcosClient
  private String appKey
  private String serverGroupKey
  private String clusterKey
  private String instanceKey
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

    clientProvider = Mock(DcosClientProvider) {
      getDcosClient(credentials, REGION) >> dcosClient
    }

    appKey = Keys.getApplicationKey(APP)
    serverGroupKey = Keys.getServerGroupKey(DcosSpinnakerAppId.parseVerbose(MARATHON_APP, ACCOUNT).get(), REGION)
    clusterKey = Keys.getClusterKey(ACCOUNT, APP, CLUSTER)
    instanceKey = Keys.getInstanceKey(DcosSpinnakerAppId.parseVerbose(MARATHON_APP, ACCOUNT).get(), TASK)
    loadBalancerKey = Keys.getLoadBalancerKey(DcosSpinnakerLbId.parseVerbose(LOAD_BALANCER).get(), REGION)

    subject = new DcosServerGroupCachingAgent(ACCOUNT, REGION, credentials, clientProvider, objectMapper, registryMock)
  }

  void "On-demand cache should cache a single server group"() {
    given:
    def appResponse = Mock(GetAppResponse) {
      getApp() >>
        Mock(App) {
          getId() >> MARATHON_APP
          getTasks() >> [
            Mock(Task) {
              getId() >> TASK
              getAppId() >> MARATHON_APP
            }
          ]
        }

    }

    dcosClient.getApp(MARATHON_APP) >> appResponse
    when:
    //TODO: again, not sure yet if this can be the fully qualified name or just the leaf of the path tree
    def result = subject.handle(providerCache, ["serverGroupName": SERVER_GROUP, "dcosCluster": REGION, "region": "${REGION}/${GROUP}", "group": GROUP, "account": ACCOUNT])
    then:
    1 * providerCache.putCacheData(Keys.Namespace.ON_DEMAND.ns, { cacheData ->
      assert cacheData.id == serverGroupKey
      assert cacheData.ttlSeconds == 600
      assert cacheData.attributes.cacheResults.length() > 0
      true
    })

    def cache = result.cacheResult.cacheResults
    result.evictions == [:]
    cache.applications.attributes.name == [APP]
    cache.applications.relationships.clusters[0][0] == clusterKey
    cache.applications.relationships.serverGroups[0][0] == serverGroupKey

    cache.clusters.attributes.name == [CLUSTER]
    cache.clusters.relationships.serverGroups[0][0] == serverGroupKey
    cache.clusters.relationships.applications[0][0] == appKey

    cache.serverGroups.attributes.name == [MARATHON_APP]
    cache.serverGroups.relationships.clusters[0][0] == clusterKey
    cache.serverGroups.relationships.applications[0][0] == appKey
    cache.serverGroups.relationships.instances[0][0] == instanceKey

    cache.instances.relationships.clusters[0][0] == clusterKey
    cache.instances.relationships.applications[0][0] == appKey
    cache.instances.relationships.serverGroups[0][0] == serverGroupKey
  }

  void "On-demand cache should evict a server group that no longer exists"() {
    when:
    //TODO: again, not sure yet if this can be the fully qualified name or just the leaf of the path tree
    def result = subject.handle(providerCache, ["serverGroupName": SERVER_GROUP, "dcosCluster": REGION, "region": "${REGION}/${GROUP}", "group": GROUP, "account": ACCOUNT])
    then:
    1 * providerCache.evictDeletedItems(Keys.Namespace.ON_DEMAND.ns, [serverGroupKey])
    result.evictions == [(Keys.Namespace.SERVER_GROUPS.ns): [serverGroupKey]]
    result.cacheResult.cacheResults.values().flatten() == []
  }

  void "Newer On-demand cache items should not be replaced by an older version"() {
    setup:
    GetAppNamespaceResponse appsInAccount = Mock(GetAppNamespaceResponse) {
      getApps() >> [
        Mock(App) {
          getId() >> MARATHON_APP
          getTasks() >> [
            Mock(Task) {
              getId() >> TASK
              getAppId() >> MARATHON_APP
            }
          ]
        }
      ]
    }

    def onDemandTaskId = "${TASK}-OnDemand"
    def cacheData = Stub(CacheData) {
      getId() >> serverGroupKey
      getAttributes() >> [
        "cacheTime"     : Long.MAX_VALUE,
        "cacheResults"  : appJson(onDemandTaskId),
        "processedCount": 0]
    }
    providerCache.getAll(Keys.Namespace.ON_DEMAND.ns, [serverGroupKey]) >> [cacheData]
    dcosClient.maybeApps(ACCOUNT, ['app.tasks', 'app.deployments']) >> Optional.of(appsInAccount)
    when:
    final result = subject.loadData(providerCache)
    then:
    // verify that the cache keeps the version of the app loaded by ondemand (indicated by the different task id)
    // instead of overwriting with the version returned from get apps
    result.cacheResults.serverGroups.relationships.instances[0][0] == onDemandTaskId

  }

  void "Older on-demand cache items should be replaced by newer versions"() {
    setup:
    GetAppNamespaceResponse appsInAccount = Mock(GetAppNamespaceResponse) {
      getApps() >> [
        Mock(App) {
          getId() >> MARATHON_APP
          getTasks() >> [
            Mock(Task) {
              getId() >> TASK
              getAppId() >> MARATHON_APP
            }
          ]
        }
      ]
    }

    def onDemandTaskId = "${TASK}-OnDemand"
    def cacheData = Stub(CacheData) {
      getId() >> serverGroupKey
      getAttributes() >> [
        "cacheTime"     : Long.MIN_VALUE,
        "cacheResults"  : appJson(onDemandTaskId),
        "processedCount": 1]
    }
    providerCache.getAll(Keys.Namespace.ON_DEMAND.ns, [serverGroupKey]) >> [cacheData]
    dcosClient.maybeApps(ACCOUNT, ['app.tasks', 'app.deployments']) >> Optional.of(appsInAccount)
    when:
    final result = subject.loadData(providerCache)
    then:
    // verify that the cache keeps the version of the app loaded by ondemand (indicated by the different task id)
    // instead of overwriting with the version returned from get apps
    result.cacheResults.serverGroups.relationships.instances[0][0] == instanceKey
  }

  void "Should cache marathon application and relationships"() {
    setup:
    GetAppNamespaceResponse appsInAccount = Mock(GetAppNamespaceResponse) {
      getApps() >> [
        Mock(App) {
          getId() >> MARATHON_APP
          getTasks() >> [
            Mock(Task) {
              getId() >> TASK
              getAppId() >> MARATHON_APP
            }
          ]
          getLabels() >> ["HAPROXY_GROUP": "${ACCOUNT}_${APP}-frontend"]
        }
      ]
    }

    dcosClient.maybeApps(ACCOUNT, ['app.tasks', 'app.deployments']) >> Optional.of(appsInAccount)
    def providerCacheMock = Mock(ProviderCache)
    providerCacheMock.getAll(_, _) >> []
    when:
    final result = subject.loadData(providerCacheMock)
    then:
    result.cacheResults.applications.attributes.name == [APP]
    result.cacheResults.applications.relationships.clusters[0][0] == clusterKey
    result.cacheResults.applications.relationships.serverGroups[0][0] == serverGroupKey
    result.cacheResults.applications.relationships.loadBalancers[0][0] == loadBalancerKey

    result.cacheResults.clusters.attributes.name == [CLUSTER]
    result.cacheResults.clusters.relationships.serverGroups[0][0] == serverGroupKey
    result.cacheResults.clusters.relationships.applications[0][0] == appKey
    result.cacheResults.clusters.relationships.loadBalancers[0][0] == loadBalancerKey


    result.cacheResults.serverGroups.attributes.name == [MARATHON_APP]
    result.cacheResults.serverGroups.relationships.clusters[0][0] == clusterKey
    result.cacheResults.serverGroups.relationships.applications[0][0] == appKey
    result.cacheResults.serverGroups.relationships.instances[0][0] == instanceKey
    result.cacheResults.serverGroups.relationships.loadBalancers[0][0] == loadBalancerKey

    result.cacheResults.instances.relationships.clusters[0][0] == clusterKey
    result.cacheResults.instances.relationships.applications[0][0] == appKey
    result.cacheResults.instances.relationships.serverGroups[0][0] == serverGroupKey
    result.cacheResults.instances.relationships.loadBalancers[0][0] == loadBalancerKey

    result.cacheResults.loadBalancers.relationships.serverGroups[0][0] == serverGroupKey
    result.cacheResults.loadBalancers.relationships.instances[0][0] == instanceKey
  }

  def appJson(String task) {
    def cacheData = MutableCacheData.mutableCacheMap()
    cacheData[serverGroupKey].with {
      attributes.name = MARATHON_APP
      relationships[Keys.Namespace.INSTANCES.ns].addAll(task)
    }
    def result = new DefaultCacheResult([
                                          (Keys.Namespace.SERVER_GROUPS.ns): cacheData.values()
                                        ], [:])
    new ObjectMapper().writeValueAsString(result.cacheResults)
  }
}
