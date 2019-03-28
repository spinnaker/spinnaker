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

package com.netflix.spinnaker.clouddriver.openstack.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackFloatingIP
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackLoadBalancer
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackLoadBalancerSummary
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackNetwork
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackServerGroup
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackSubnet
import redis.clients.jedis.exceptions.JedisException
import spock.lang.Ignore
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.FLOATING_IPS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.NETWORKS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SERVER_GROUPS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SUBNETS

class OpenstackLoadBalancerProviderSpec extends Specification {

  String account = 'test'
  String region = 'east'

  OpenstackClusterProvider clusterProvider
  Cache cache
  ObjectMapper objectMapper

  void "setup"() {
    cache = Mock(Cache)
    objectMapper = Mock(ObjectMapper)
    clusterProvider = Mock(OpenstackClusterProvider)
  }

  void "test get all load balancers"() {
    given:
    String app = 'myapp'
    String cluster = "$app-teststack"
    String lbid = 'lb1'
    String lbName = "$app-lb"
    String name = "$cluster-v002"
    String lbKey = Keys.getLoadBalancerKey(lbName, lbid, account, region)
    CacheData cacheData = Mock(CacheData)
    Collection<CacheData> cacheDataList = [cacheData]
    OpenstackLoadBalancer loadBalancer = Mock(OpenstackLoadBalancer) {
      it.id >> { lbid }
      it.name >> { lbName }
      it.account >> { account }
      it.region >> { region }
      it.serverGroups >> { [new LoadBalancerServerGroup(name: name)] }
    }
    OpenstackFloatingIP floatingIP = Stub(OpenstackFloatingIP)
    OpenstackNetwork network = Stub(OpenstackNetwork)
    OpenstackSubnet subnet = Stub(OpenstackSubnet)
    OpenstackLoadBalancer.View view = buildLoadBalancerView(loadBalancer, floatingIP, network, subnet)
    OpenstackLoadBalancerProvider loadBalancerProvider = Spy(OpenstackLoadBalancerProvider, constructorArgs: [cache, objectMapper, clusterProvider]) {
      fromCacheData(cacheData) >> { view }
    }

    when:
    Set<OpenstackLoadBalancer.View> result = loadBalancerProvider.getApplicationLoadBalancers(app)

    then:
    1 * cache.filterIdentifiers(LOAD_BALANCERS.ns, Keys.getLoadBalancerKey(app, '*', '*', '*')) >> [lbKey].toSet()
    1 * cache.filterIdentifiers(LOAD_BALANCERS.ns, Keys.getLoadBalancerKey("$app-*", '*', '*', '*')) >> [lbKey].toSet()
    1 * cache.getAll(LOAD_BALANCERS.ns, [lbKey].toSet(), _ as RelationshipCacheFilter) >> cacheDataList
    result.size() == 1
    result[0] == view
    noExceptionThrown()
  }

  void "test get all load balancers - throw exception"() {
    given:
    String app = 'myapp'
    String lbid = 'lb1'
    String lbName = "$app-lb"
    String lbKey = Keys.getLoadBalancerKey(lbName, lbid, account, region)
    Throwable throwable = new JedisException('test')
    OpenstackLoadBalancerProvider loadBalancerProvider = Spy(OpenstackLoadBalancerProvider, constructorArgs: [cache, objectMapper, clusterProvider])

    when:
    loadBalancerProvider.getApplicationLoadBalancers(app)

    then:
    1 * cache.filterIdentifiers(LOAD_BALANCERS.ns, Keys.getLoadBalancerKey(app, '*', '*', '*')) >> [lbKey].toSet()
    1 * cache.filterIdentifiers(LOAD_BALANCERS.ns, Keys.getLoadBalancerKey("$app-*", '*', '*', '*')) >> [lbKey].toSet()
    1 * cache.getAll(LOAD_BALANCERS.ns, [lbKey].toSet(), _ as RelationshipCacheFilter) >> { throw throwable }
    Throwable thrownException = thrown(JedisException)
    throwable == thrownException
  }

  void 'test get load balancer by account, region, and name'() {
    given:
    String lbid = 'lb1'
    String name = 'myapp-teststack-v002'
    CacheData cacheData = Mock(CacheData)
    Collection<CacheData> cacheDataList = [cacheData]
    String lbKey = Keys.getLoadBalancerKey('*', lbid, account, region)
    OpenstackLoadBalancer loadBalancer = Mock(OpenstackLoadBalancer) {
      it.id >> { lbid }
      it.account >> { account }
      it.region >> { region }
      it.serverGroups >> { [new LoadBalancerServerGroup(name: name)] }
    }
    OpenstackFloatingIP floatingIP = Stub(OpenstackFloatingIP)
    OpenstackNetwork network = Stub(OpenstackNetwork)
    OpenstackSubnet subnet = Stub(OpenstackSubnet)
    OpenstackLoadBalancer.View view = buildLoadBalancerView(loadBalancer, floatingIP, network, subnet)
    OpenstackLoadBalancerProvider loadBalancerProvider = Spy(OpenstackLoadBalancerProvider, constructorArgs: [cache, objectMapper, clusterProvider]) {
      fromCacheData(cacheData) >> { view }
    }
    List<String> filter = ['filter']

    when:
    Set<OpenstackLoadBalancer> result = loadBalancerProvider.getLoadBalancers(account, region, lbid)

    then:
    1 * cache.filterIdentifiers(LOAD_BALANCERS.ns, lbKey) >> filter
    1 * cache.getAll(LOAD_BALANCERS.ns, filter, _ as RelationshipCacheFilter) >> cacheDataList
    result.size() == 1
    result[0] == view
    noExceptionThrown()
  }

  void "test get load balancer by account, region, and name - exception"() {
    given:
    String lbid = 'lb1'
    CacheData cacheData = Mock(CacheData)
    Collection<CacheData> cacheDataList = [cacheData]
    String lbKey = Keys.getLoadBalancerKey('*', lbid, account, region)
    List<String> filter = ['filter']
    Throwable throwable = new JedisException('test')
    OpenstackLoadBalancerProvider loadBalancerProvider = Spy(OpenstackLoadBalancerProvider, constructorArgs: [cache, objectMapper, clusterProvider])

    when:
    loadBalancerProvider.getLoadBalancers(account, region, lbid)

    then:
    1 * cache.filterIdentifiers(LOAD_BALANCERS.ns, lbKey) >> filter
    1 * cache.getAll(LOAD_BALANCERS.ns, filter, _ as RelationshipCacheFilter) >> { throw throwable }
    Throwable thrownException = thrown(JedisException)
    throwable == thrownException
  }

  def "test convert cache data to load balancer"() {
    given:
    String lbid = 'lb1'
    String name = 'myapp-teststack-v002'
    Map<String, Object> attributes = Mock(Map)
    CacheData ipCacheData = Mock(CacheData)
    ipCacheData.attributes >> Mock(Map)
    CacheData networkCacheData = Mock(CacheData)
    networkCacheData.attributes >> Mock(Map)
    CacheData subnetCacheData = Mock(CacheData)
    subnetCacheData.attributes >> Mock(Map)
    String sgKey = Keys.getServerGroupKey(name, account, region)
    String ipId = UUID.randomUUID().toString()
    String ipKey = Keys.getFloatingIPKey(ipId, account, region)
    String subnetId = UUID.randomUUID().toString()
    String subnetKey = Keys.getSubnetKey(subnetId, account, region)
    String networkId = UUID.randomUUID().toString()
    String networkKey = Keys.getNetworkKey(networkId, account, region)
    CacheData cacheData = Mock(CacheData)
    cacheData.relationships >> [(SERVER_GROUPS.ns): [sgKey], (FLOATING_IPS.ns): [ipKey], (SUBNETS.ns): [subnetKey], (NETWORKS.ns): [networkKey]]
    cacheData.attributes >> attributes
    OpenstackFloatingIP floatingIP = Stub(OpenstackFloatingIP)
    OpenstackNetwork network = Stub(OpenstackNetwork)
    OpenstackSubnet subnet = Stub(OpenstackSubnet)
    OpenstackServerGroup.View serverGroup = Mock(OpenstackServerGroup.View) {
      getName() >> { name }
      isDisabled() >> { false }
      getInstances() >> { [] }
    }
    OpenstackLoadBalancer loadBalancer = new OpenstackLoadBalancer(id: lbid, account: account, region: region, floatingIP: floatingIP, subnet: subnet, network: network, serverGroups: [new LoadBalancerServerGroup(name: name)] )
    OpenstackLoadBalancerProvider loadBalancerProvider = new OpenstackLoadBalancerProvider(cache, objectMapper, clusterProvider)

    when:
    OpenstackLoadBalancer.View result = loadBalancerProvider.fromCacheData(cacheData)

    then:
    1 * cache.getAll(FLOATING_IPS.ns, cacheData.relationships[(FLOATING_IPS.ns)] ?: []) >> [ipCacheData]
    1 * objectMapper.convertValue(_ as Map, OpenstackFloatingIP) >> floatingIP
    1 * cache.getAll(NETWORKS.ns, cacheData.relationships[(NETWORKS.ns)] ?: []) >> [networkCacheData]
    1 * objectMapper.convertValue(_ as Map, OpenstackNetwork) >> network
    1 * cache.getAll(SUBNETS.ns, cacheData.relationships[(SUBNETS.ns)] ?: []) >> [subnetCacheData]
    1 * objectMapper.convertValue(_ as Map, OpenstackSubnet) >> subnet
    1 * objectMapper.convertValue(attributes, OpenstackLoadBalancer) >> loadBalancer
    1 * clusterProvider.getServerGroup(account, region, name) >> serverGroup
    result == loadBalancer.view
    noExceptionThrown()
  }

  def "test convert cache data to load balancer - exception"() {
    given:
    Throwable throwable = new JedisException('test')
    String lbid = 'lb1'
    String name = 'myapp-teststack-v002'
    Map<String, Object> attributes = Mock(Map)
    CacheData ipCacheData = Mock(CacheData)
    ipCacheData.attributes >> Mock(Map)
    CacheData networkCacheData = Mock(CacheData)
    networkCacheData.attributes >> Mock(Map)
    CacheData subnetCacheData = Mock(CacheData)
    subnetCacheData.attributes >> Mock(Map)
    String sgKey = Keys.getServerGroupKey(name, account, region)
    String ipId = UUID.randomUUID().toString()
    String ipKey = Keys.getFloatingIPKey(ipId, account, region)
    String subnetId = UUID.randomUUID().toString()
    String subnetKey = Keys.getSubnetKey(subnetId, account, region)
    String networkId = UUID.randomUUID().toString()
    String networkKey = Keys.getNetworkKey(networkId, account, region)
    CacheData cacheData = Mock(CacheData)
    cacheData.relationships >> [(SERVER_GROUPS.ns): [sgKey], (FLOATING_IPS.ns): [ipKey], (SUBNETS.ns): [subnetKey], (NETWORKS.ns): [networkKey]]
    cacheData.attributes >> attributes
    OpenstackFloatingIP floatingIP = Stub(OpenstackFloatingIP)
    OpenstackNetwork network = Stub(OpenstackNetwork)
    OpenstackSubnet subnet = Stub(OpenstackSubnet)
    OpenstackLoadBalancer.View loadBalancer = Mock(OpenstackLoadBalancer.View) {
      it.id >> { lbid }
      it.account >> { account }
      it.region >> { region }
      it.ip >> { floatingIP.id }
      it.subnetId >> { subnet.id }
      it.subnetName >> { subnet.name }
      it.networkId >> { network.id }
      it.networkName >> { network.name }
      it.serverGroups >> { [new LoadBalancerServerGroup(name: name)] }
    }
    OpenstackLoadBalancerProvider loadBalancerProvider = new OpenstackLoadBalancerProvider(cache, objectMapper, clusterProvider)

    when:
    loadBalancerProvider.fromCacheData(cacheData)

    then:
    1 * cache.getAll(FLOATING_IPS.ns, cacheData.relationships[(FLOATING_IPS.ns)] ?: []) >> [ipCacheData]
    1 * objectMapper.convertValue(_ as Map, OpenstackFloatingIP) >> floatingIP
    1 * cache.getAll(NETWORKS.ns, cacheData.relationships[(NETWORKS.ns)] ?: []) >> [networkCacheData]
    1 * objectMapper.convertValue(_ as Map, OpenstackNetwork) >> network
    1 * cache.getAll(SUBNETS.ns, cacheData.relationships[(SUBNETS.ns)] ?: []) >> [subnetCacheData]
    1 * objectMapper.convertValue(_ as Map, OpenstackSubnet) >> subnet
    1 * objectMapper.convertValue(attributes, OpenstackLoadBalancer) >> loadBalancer
    1 * clusterProvider.getServerGroup(account, region, name) >> { throw throwable }
    Throwable thrownException = thrown(JedisException)
    throwable == thrownException
  }

  @Ignore
  OpenstackLoadBalancer.View buildLoadBalancerView(OpenstackLoadBalancer loadBalancer, OpenstackFloatingIP floatingIP, OpenstackNetwork network, OpenstackSubnet subnet) {
    new OpenstackLoadBalancer.View(id: loadBalancer.id, name: loadBalancer.name, description: loadBalancer.description,
      account: account, region: region,
      ip: floatingIP.id, subnetId: subnet.id,
      subnetName: subnet.name, networkId: network.id, networkName: network.name,
      serverGroups: [new LoadBalancerServerGroup(name: 'myapp-teststack-v002')])

  }

  
  def 'get load balancer by account, region, name'() {
    given:
    def provider = Spy(OpenstackLoadBalancerProvider, constructorArgs: [cache, objectMapper, clusterProvider])
    String name = 'id0'

    when:
    List<OpenstackLoadBalancer> result = provider.byAccountAndRegionAndName(account, region, name)

    then:
    1 * provider.getLoadBalancers(account, region, name) >> lbs
    result.size() == lbs.size()
    if (result.size() > 0) {
      assert result[0] == lbs[0]
    }
    noExceptionThrown()

    where:
    lbs << [[create(0)].toSet(), [].toSet()]
  }

  def 'get load balancer by account, region, name - throw exception'() {
    given:
    def provider = Spy(OpenstackLoadBalancerProvider, constructorArgs: [cache, objectMapper, clusterProvider])
    String name = 'id0'
    Throwable throwable = new JedisException('exception')

    when:
    provider.byAccountAndRegionAndName(account, region, name)

    then:
    1 * provider.getLoadBalancers(account, region, name) >> { throw throwable }
    Throwable thrownException = thrown(JedisException)
    thrownException == throwable
  }

  OpenstackLoadBalancer.View create(int i) {
    String account = 'test'
    String region = 'r1'
    String id = "id$i"
    String name = "name$i"
    String description = 'internal_port=8100'
    String status = 'up'
    String protocol = 'http'
    String algorithm = 'round_robin'
    String ip = '1.2.3.4'
    Integer externalPort = 80
    String subnet = "subnet$i"
    String network = "network$i"
    def healthMonitor = new OpenstackLoadBalancer.OpenstackHealthMonitor(id: "health$i", httpMethod: 'GET',
                                                                         maxRetries: 5, adminStateUp: 'UP', delay: 5, expectedCodes: [200])
    def serverGroups = [new LoadBalancerServerGroup(name: 'sg1', isDisabled: false,
                                                    instances: [new LoadBalancerInstance(id: 'id', zone: "zone$i", health: [state:'up', zone: "zone$i"])], cloudProvider: OpenstackCloudProvider.ID)]
    new OpenstackLoadBalancer.View(account: account, region: region, id: id, name: name, description: description,
                                   status: status, algorithm: algorithm, ip: ip, subnetId: subnet, subnetName: subnet, networkId: network, networkName: network,
                                   healthMonitor: healthMonitor, serverGroups: serverGroups)
  }

}
