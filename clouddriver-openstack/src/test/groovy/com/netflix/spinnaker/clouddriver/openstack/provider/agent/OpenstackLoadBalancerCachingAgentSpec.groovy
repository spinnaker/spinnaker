/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Timer
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent.OnDemandResult
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import com.netflix.spinnaker.clouddriver.openstack.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackLoadBalancer
import com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.model.common.ActionResponse
import org.openstack4j.model.network.Port
import org.openstack4j.model.network.ext.HealthMonitorV2
import org.openstack4j.model.network.ext.LbPoolV2
import org.openstack4j.model.network.ext.ListenerProtocol
import org.openstack4j.model.network.ext.ListenerV2
import org.openstack4j.model.network.ext.LoadBalancerV2
import org.openstack4j.model.network.ext.LoadBalancerV2StatusTree
import org.openstack4j.model.network.ext.status.LbPoolV2Status
import org.openstack4j.model.network.ext.status.ListenerV2Status
import org.openstack4j.model.network.ext.status.LoadBalancerV2Status
import org.openstack4j.model.network.ext.status.MemberV2Status
import org.openstack4j.openstack.networking.domain.ext.ListItem
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.FLOATING_IPS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.LOAD_BALANCERS

class OpenstackLoadBalancerCachingAgentSpec extends Specification {

  OpenstackLoadBalancerCachingAgent cachingAgent
  OpenstackNamedAccountCredentials namedAccountCredentials
  OpenstackCredentials credentials
  ObjectMapper objectMapper
  OpenstackClientProvider provider
  Registry registry

  @Shared
  String region = 'east'
  @Shared
  String account = 'account'
  final String serverGroupName = 'myapp-test-v000'

  void "setup"() {
    provider = Mock(OpenstackClientProvider)
    credentials = GroovyMock(OpenstackCredentials) {
      it.provider >> { provider }
    }
    namedAccountCredentials = GroovyMock(OpenstackNamedAccountCredentials) {
      it.credentials >> { credentials }
    }
    objectMapper = Mock(ObjectMapper)
    registry = Stub(Registry) {
      timer(_, _) >> Mock(Timer)
    }
    cachingAgent = Spy(OpenstackLoadBalancerCachingAgent, constructorArgs: [namedAccountCredentials, region, objectMapper, registry]) {
      it.accountName >> { account }
      it.clientProvider >> { provider }
    }
  }

  void "test load data"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    CacheResult cacheResult = Mock(CacheResult)
    GroovyMock(CompletableFuture, global: true)
    CompletableFuture.supplyAsync(_) >> Mock(CompletableFuture) {
      thenApplyAsync(_) >> Mock(CompletableFuture)
    }
    CompletableFuture.allOf(_ as CompletableFuture, _ as CompletableFuture, _ as CompletableFuture, _ as CompletableFuture, _ as CompletableFuture, _ as CompletableFuture) >> Mock(CompletableFuture)

    when:
    CacheResult result = cachingAgent.loadData(providerCache)

    then:
    1 * cachingAgent.buildLoadDataCache(providerCache, [], _ as Closure<CacheResult>) >> cacheResult

    and:
    result == cacheResult
    noExceptionThrown()
  }


  void "test load data exception"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    CompletableFuture f = Mock(CompletableFuture) {
      thenApplyAsync(_) >> Mock(CompletableFuture)
    }
    GroovyMock(CompletableFuture, global: true)
    CompletableFuture.supplyAsync(_) >> f
    CompletableFuture.allOf(_ as CompletableFuture, _ as CompletableFuture, _ as CompletableFuture, _ as CompletableFuture, _ as CompletableFuture, _ as CompletableFuture) >> Mock(CompletableFuture)
    Throwable throwable = new OpenstackProviderException(ActionResponse.actionFailed('test', 1))

    when:
    cachingAgent.loadData(providerCache)

    then:
    1 * f.get() >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException == throwable
  }

  void "test build cache"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    String loadBalancerId = UUID.randomUUID().toString()
    String listenerId = UUID.randomUUID().toString()
    String poolId = UUID.randomUUID().toString()
    String healthId = UUID.randomUUID().toString()
    String ipId = UUID.randomUUID().toString()
    String vipPortId = UUID.randomUUID().toString()
    String lbName = 'myapp-lb'
    String subnetId = UUID.randomUUID().toString()
    String ipv6 = 'fd16:3966:18cc:0:f816:3eff:fe88:9004'
    LoadBalancerV2 loadBalancer = Mock(LoadBalancerV2) {
      it.id >> { loadBalancerId }
      it.name >> { lbName }
      it.vipPortId >> { vipPortId }
      it.vipSubnetId >> { subnetId }
      it.listeners >> [new ListItem(id: listenerId)]
    }
    ListenerV2 listener = Mock(ListenerV2) {
      it.id >> { listenerId }
      it.protocol >> { ListenerProtocol.HTTP }
      it.protocolPort >> { 80 }
      it.description >> { "HTTP:80:HTTP:8080" }
      it.defaultPoolId >> { poolId }
    }
    LbPoolV2 pool = Mock(LbPoolV2) {
      it.id >> { poolId }
      it.healthMonitorId >> { healthId }
    }
    HealthMonitorV2 healthMonitor = Mock(HealthMonitorV2) {
      it.id >> { 'id' }
    }
    Map<String, Object> lbAttributes = new HashMap<>()
    String lbKey = Keys.getLoadBalancerKey(lbName, loadBalancerId, account, region)
    MemberV2Status memberV2Status = Mock(MemberV2Status) {
      it.address >> { ipv6 }
      it.operatingStatus >> { 'ONLINE' }
    }
    LbPoolV2Status lbPoolV2Status = Mock(LbPoolV2Status) {
      it.memberStatuses >> { [memberV2Status] }
    }
    ListenerV2Status listenerV2Status = Mock(ListenerV2Status) {
      it.lbPoolV2Statuses >> { [lbPoolV2Status] }
    }
    LoadBalancerV2Status loadBalancerV2Status = Mock(LoadBalancerV2Status) {
      it.listenerStatuses >> { [listenerV2Status] }
    }
    LoadBalancerV2StatusTree loadBalancerV2StatusTree = Mock(LoadBalancerV2StatusTree) {
      it.loadBalancerV2Status >> { loadBalancerV2Status }
    }
    Port port = Mock(Port) {
      it.securityGroups >> { [] }
    }

    and:
    List<String> instanceKeys = [Keys.getInstanceKey(ipId, account, region)]
    Map<String, Object> ipAttributes = [instanceId: ipId]
    CacheData ipCacheData = Mock(CacheData) {
      it.attributes >> { ipAttributes }
    }
    Collection<CacheData> ipCacheDataList = [ipCacheData]

    and:
    List<String> ipKeys = [Keys.getFloatingIPKey(ipId, account, region)]
    Map<String, Object> instanceAttributes = [instanceId: ipId, ipv6: ipv6]
    CacheData instanceCacheData = Mock(CacheData) {
      it.attributes >> { instanceAttributes }
    }
    Collection<CacheData> instanceCacheDataList = [instanceCacheData]

    and:
    OpenstackLoadBalancer openstackLoadBalancer = Mock(OpenstackLoadBalancer)
    OpenstackLoadBalancer.metaClass.static.from = { LoadBalancerV2 lb,
                                                    Set<ListenerV2> listeners,
                                                    LbPoolV2 pools,
                                                    HealthMonitorV2 hm,
                                                    String a, String r -> openstackLoadBalancer
    }

    when:
    CacheResult result = cachingAgent.buildCacheResult(providerCache, [loadBalancer].toSet(),
      [listener].toSet(), [pool].toSet(), [healthMonitor].toSet(), [loadBalancerId: loadBalancerV2StatusTree], [vipPortId: port],
      new CacheResultBuilder(startTime: System.currentTimeMillis()))

    then:
    1 * providerCache.filterIdentifiers(INSTANCES.ns, Keys.getInstanceKey('*', account, region)) >> instanceKeys
    1 * providerCache.getAll(INSTANCES.ns, instanceKeys, _ as RelationshipCacheFilter) >> instanceCacheDataList
    1 * providerCache.filterIdentifiers(FLOATING_IPS.ns, Keys.getFloatingIPKey('*', account, region)) >> ipKeys
    1 * providerCache.getAll(FLOATING_IPS.ns, ipKeys, _ as RelationshipCacheFilter) >> ipCacheDataList

    and:
    1 * objectMapper.convertValue(openstackLoadBalancer, OpenstackInfrastructureProvider.ATTRIBUTES) >> lbAttributes

    and:
    result.cacheResults.get(LOAD_BALANCERS.ns).first().id == lbKey
    result.cacheResults.get(LOAD_BALANCERS.ns).first().attributes == lbAttributes
    noExceptionThrown()
  }

  void "test handles - #testCase"() {
    when:
    boolean result = cachingAgent.handles(type, cloudProvider)

    then:
    result == expected

    where:
    testCase         | type                                    | cloudProvider             | expected
    'wrong type'     | OnDemandAgent.OnDemandType.ServerGroup  | OpenstackCloudProvider.ID | false
    'wrong provider' | OnDemandAgent.OnDemandType.LoadBalancer | 'aws'                     | false
    'success'        | OnDemandAgent.OnDemandType.LoadBalancer | OpenstackCloudProvider.ID | true
  }

  void "test handle on demand no result - #testCase"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)

    when:
    OnDemandResult result = cachingAgent.handle(providerCache, data)

    then:
    result == null

    where:
    testCase                   | data
    'empty data'               | [:]
    'missing loadBalancerName' | [account: account, region: region]
    'wrong account'            | [loadBalancerName: 'name', account: 'abc', region: region]
    'wrong region'             | [loadBalancerName: 'name', account: account, region: 'abc']
  }

  void "test handle on demand no resource"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    String loadbalancerName = "test"
    String loadBalancerKey = Keys.getLoadBalancerKey(loadbalancerName, '*', account, region)
    Map<String, Object> data = [loadBalancerName: loadbalancerName, account: account, region: region]
    CacheResult cacheResult = new CacheResultBuilder(startTime: Long.MAX_VALUE).build()

    when:
    OnDemandResult result = cachingAgent.handle(providerCache, data)

    then:
    1 * provider.getLoadBalancerByName(region, loadbalancerName) >> { throw new OpenstackProviderException('test') }
    1 * cachingAgent.buildCacheResult(providerCache, [].toSet(), [].toSet(), [].toSet(), [].toSet(), [:] as Map, [:] as Map, _) >> cacheResult
    1 * cachingAgent.resolveKey(providerCache, LOAD_BALANCERS.ns, loadBalancerKey) >> loadBalancerKey
    1 * cachingAgent.processOnDemandCache(cacheResult, objectMapper, _, providerCache, loadBalancerKey)

    and:
    result.cacheResult == cacheResult
    result.evictions.get(LOAD_BALANCERS.ns) == [loadBalancerKey]
  }

  void "test handle on demand"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    CacheResult cacheResult = new CacheResultBuilder(startTime: Long.MAX_VALUE).build()
    String loadBalancerId = UUID.randomUUID().toString()
    String listenerId = UUID.randomUUID().toString()
    String poolId = UUID.randomUUID().toString()
    String healthId = UUID.randomUUID().toString()
    String lbName = 'myapp-lb'
    String subnetId = UUID.randomUUID().toString()
    String vipPortId = UUID.randomUUID().toString()
    String loadbalancerName = "test"
    String loadBalancerKey = Keys.getLoadBalancerKey(loadbalancerName, loadBalancerId, account, region)
    Map<String, Object> data = [loadBalancerName: loadbalancerName, account: account, region: region]
    LoadBalancerV2 loadBalancer = Mock(LoadBalancerV2) {
      it.id >> { loadBalancerId }
      it.name >> { lbName }
      it.vipPortId >> { vipPortId }
      it.vipSubnetId >> { subnetId }
      it.listeners >> [new ListItem(id: listenerId)]
    }
    ListenerV2 listener = Mock(ListenerV2) {
      it.id >> { listenerId }
      it.protocol >> { ListenerProtocol.HTTP }
      it.protocolPort >> { 80 }
      it.description >> { "HTTP:80:HTTP:8080" }
      it.defaultPoolId >> { poolId }
    }
    LbPoolV2 pool = Mock(LbPoolV2) {
      it.id >> { poolId }
      it.healthMonitorId >> { healthId }
    }
    Port port = Mock(Port) {
      it.securityGroups >> { [] }
    }
    HealthMonitorV2 healthMonitor = Mock(HealthMonitorV2)
    LoadBalancerV2StatusTree loadBalancerStatusTree = Mock(LoadBalancerV2StatusTree)
    when:
    OnDemandResult result = cachingAgent.handle(providerCache, data)

    then:
    1 * provider.getLoadBalancerByName(region, loadbalancerName) >> loadBalancer
    1 * provider.getListener(region, listenerId) >> listener
    1 * provider.getPool(region, poolId) >> pool
    1 * provider.getMonitor(region, healthId) >> healthMonitor
    1 * provider.getLoadBalancerStatusTree(region, loadBalancerId) >> loadBalancerStatusTree
    1 * provider.getPort(region, vipPortId) >> port
    1 * cachingAgent.buildCacheResult(providerCache, [loadBalancer].toSet(), [listener].toSet(), [pool].toSet(), [healthMonitor].toSet(), [(loadBalancerId) : loadBalancerStatusTree], [(vipPortId): port],  _) >> cacheResult
    1 * cachingAgent.resolveKey(providerCache, LOAD_BALANCERS.ns, loadBalancerKey) >> loadBalancerKey
    1 * cachingAgent.processOnDemandCache(cacheResult, objectMapper, _, providerCache, loadBalancerKey)

    and:
    result.cacheResult == cacheResult
    result.evictions.get(LOAD_BALANCERS.ns).isEmpty()
  }

  void 'test pending on demand requests'() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    Collection<Map> maps = Mock(Collection)

    when:
    Collection<Map> result = cachingAgent.pendingOnDemandRequests(providerCache)

    then:
    1 * cachingAgent.getAllOnDemandCacheByRegionAndAccount(providerCache, account, region) >> maps

    and:
    result == maps
  }
}
