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
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackFloatingIP
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackLoadBalancer
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackNetwork
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackPort
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackSubnet
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackVip
import com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.model.common.ActionResponse
import org.openstack4j.model.network.ext.HealthMonitor
import org.openstack4j.model.network.ext.LbPool
import org.openstack4j.model.network.ext.Vip
import spock.lang.Shared
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.FLOATING_IPS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.NETWORKS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.PORTS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SERVER_GROUPS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SUBNETS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.VIPS

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

  void "test load data" () {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    CacheResult cacheResult = Mock(CacheResult)

    when:
    CacheResult result = cachingAgent.loadData(providerCache)

    then:
    1 * provider.getAllLoadBalancerPools(region) >> []
    1 * cachingAgent.buildLoadDataCache(providerCache, [], _) >> cacheResult

    and:
    result == cacheResult
    noExceptionThrown()
  }


  void "test load data exception"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    Throwable throwable = new OpenstackProviderException(ActionResponse.actionFailed('test', 1))

    when:
    cachingAgent.loadData(providerCache)

    then:
    1 * provider.getAllLoadBalancerPools(region) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException == throwable
  }

  void "test build cache"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    String lbId = UUID.randomUUID().toString()
    String healthId = UUID.randomUUID().toString()
    String vipId = UUID.randomUUID().toString()
    String portId = UUID.randomUUID().toString()
    String ipId = UUID.randomUUID().toString()
    String lbName = 'myapp-lb'
    String subnetId = UUID.randomUUID().toString()
    String networkId = UUID.randomUUID().toString()
    LbPool pool = Mock(LbPool) {
      it.id >> { lbId }
      it.name >> { lbName }
      it.vipId >> { vipId }
      it.subnetId >> { subnetId }
      it.healthMonitors >> { [healthId] }
    }
    HealthMonitor healthMonitor = Mock(HealthMonitor)
    Map<String, Object> lbAttributes = Mock(Map)
    String lbKey = Keys.getLoadBalancerKey(lbName, lbId, account, region)

    and:
    Map<String, Object> vipAttributes = Mock(Map)
    CacheData vipCacheData = Mock(CacheData) {
      it.attributes >> { vipAttributes }
    }
    OpenstackVip vip = Mock(OpenstackVip) {
      it.id >> { vipId }
    }

    and:
    List<String> portKeys = [Keys.getPortKey(portId, account, region)]
    Map<String, Object> portAttributes = [name:"vip-${vipId}"]
    CacheData portCacheData = Mock(CacheData) {
      it.attributes >> { portAttributes }
    }
    Collection<CacheData> portCacheDataList = [portCacheData]
    OpenstackPort port = Mock(OpenstackPort) {
      it.deviceId >> { ipId }
    }

    and:
    List<String> ipKeys = [Keys.getFloatingIPKey(ipId, account, region)]
    Map<String, Object> ipAttributes = [instanceId:ipId]
    CacheData ipCacheData = Mock(CacheData) {
      it.attributes >> { ipAttributes }
    }
    Collection<CacheData> ipCacheDataList = [ipCacheData]
    OpenstackFloatingIP ip = Mock(OpenstackFloatingIP)

    and:
    Map<String, Object> subnetAttributes = [id:subnetId]
    CacheData subnetCacheData = Mock(CacheData) {
      it.attributes >> { subnetAttributes }
    }
    OpenstackSubnet subnet = Mock(OpenstackSubnet)

    and:
    Map<String, Object> networkAttributes = [id:networkId]
    CacheData networkCacheData = Mock(CacheData) {
      it.attributes >> { networkAttributes }
    }
    OpenstackNetwork network = Mock(OpenstackNetwork)

    and:
    OpenstackLoadBalancer loadBalancer = Mock(OpenstackLoadBalancer)
    OpenstackLoadBalancer.metaClass.static.from = { LbPool p, OpenstackVip v, OpenstackSubnet s,
                                                    OpenstackNetwork n,
                                                    OpenstackFloatingIP i, Set<HealthMonitor> h,
                                                    String a, String r -> loadBalancer }

    when:
    CacheResult result = cachingAgent.buildCacheResult(providerCache, [pool], new CacheResultBuilder(startTime: System.currentTimeMillis()))

    then:
    1 * provider.getHealthMonitor(region, healthId) >> healthMonitor

    and:
    1 * providerCache.get(VIPS.ns, Keys.getVipKey(pool.vipId, account, region)) >> vipCacheData
    1 * objectMapper.convertValue(vipAttributes, OpenstackVip) >> vip

    and:
    1 * providerCache.filterIdentifiers(PORTS.ns, Keys.getPortKey('*', account, region)) >> portKeys
    1 * providerCache.getAll(PORTS.ns, portKeys, _ as RelationshipCacheFilter) >> portCacheDataList
    1 * objectMapper.convertValue(portAttributes, OpenstackPort) >> port

    and:
    1 * providerCache.filterIdentifiers(FLOATING_IPS.ns, Keys.getFloatingIPKey('*', account, region)) >> ipKeys
    1 * providerCache.getAll(FLOATING_IPS.ns, ipKeys, _ as RelationshipCacheFilter) >> ipCacheDataList
    1 * objectMapper.convertValue(ipAttributes, OpenstackFloatingIP) >> ip

    and:
    1 * providerCache.get(SUBNETS.ns, Keys.getSubnetKey(pool.subnetId, account, region)) >> subnetCacheData
    1 * objectMapper.convertValue(subnetAttributes, OpenstackSubnet) >> subnet

    and:
    1 * providerCache.get(NETWORKS.ns, Keys.getNetworkKey(ip.networkId, account, region)) >> networkCacheData
    1 * objectMapper.convertValue(networkAttributes, OpenstackNetwork) >> network

    and:
    1 * objectMapper.convertValue(loadBalancer, OpenstackInfrastructureProvider.ATTRIBUTES) >> lbAttributes

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
    testCase                  | data
    'empty data'              | [:]
    'missing loadBalancerName'| [account: account, region: region]
    'wrong account'           | [loadBalancerName: 'name', account: 'abc', region: region]
    'wrong region'            | [loadBalancerName: 'name', account: account, region: 'abc']
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
    1 * provider.getLoadBalancerPoolByName(region, loadbalancerName) >> { throw new OpenstackProviderException('test') }
    1 * cachingAgent.buildCacheResult(providerCache, [], _) >> cacheResult
    1 * cachingAgent.resolveKey(providerCache, LOAD_BALANCERS.ns, loadBalancerKey) >> loadBalancerKey
    1 * cachingAgent.processOnDemandCache(cacheResult, objectMapper, _, providerCache, loadBalancerKey)

    and:
    result.cacheResult ==  cacheResult
    result.evictions.get(LOAD_BALANCERS.ns) == [loadBalancerKey]
  }

  void "test handle on demand"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    String poolId = UUID.randomUUID().toString()
    String vipId = UUID.randomUUID().toString()
    String loadbalancerName = "test"
    String loadBalancerKey = Keys.getLoadBalancerKey(loadbalancerName, poolId, account, region)
    Map<String, Object> data = [loadBalancerName: loadbalancerName, account: account, region: region]
    CacheResult cacheResult = new CacheResultBuilder(startTime: Long.MAX_VALUE).build()
    LbPool lbPool = Mock(LbPool) {
      getId() >> poolId
      getVipId() >> vipId
    }

    when:
    OnDemandResult result = cachingAgent.handle(providerCache, data)

    then:
    1 * provider.getLoadBalancerPoolByName(region, loadbalancerName) >> lbPool
    1 * provider.getVip(region, vipId) >> Mock(Vip)
    1 * cachingAgent.buildCacheResult(providerCache, [lbPool], _) >> cacheResult
    1 * cachingAgent.resolveKey(providerCache, LOAD_BALANCERS.ns, loadBalancerKey) >> loadBalancerKey
    1 * cachingAgent.processOnDemandCache(cacheResult, objectMapper, _, providerCache, loadBalancerKey)

    and:
    result.cacheResult == cacheResult
    result.evictions.get(LOAD_BALANCERS.ns).isEmpty()
  }

  void 'test pending on demand requests' () {
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
