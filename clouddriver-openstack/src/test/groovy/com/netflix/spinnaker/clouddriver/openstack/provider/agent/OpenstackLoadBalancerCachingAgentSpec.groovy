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
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.cats.provider.ProviderCache
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
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.FLOATING_IPS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.NETWORKS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.PORTS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SUBNETS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.VIPS

class OpenstackLoadBalancerCachingAgentSpec extends Specification {

  OpenstackLoadBalancerCachingAgent cachingAgent
  OpenstackNamedAccountCredentials namedAccountCredentials
  OpenstackCredentials credentials
  ObjectMapper objectMapper
  OpenstackClientProvider provider
  final String region = 'east'
  final String account = 'account'
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
    cachingAgent = Spy(OpenstackLoadBalancerCachingAgent, constructorArgs: [namedAccountCredentials, region, objectMapper]) {
      it.accountName >> { account }
      it.clientProvider >> { provider }
    }
  }

  void "test load data"() {
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
    CacheResult result = cachingAgent.loadData(providerCache)

    then:
    1 * provider.getAllLoadBalancerPools(region) >> [pool]
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
}
