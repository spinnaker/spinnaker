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
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackLoadBalancer
import redis.clients.jedis.exceptions.JedisException
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SERVER_GROUPS

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
    OpenstackLoadBalancerProvider loadBalancerProvider = Spy(OpenstackLoadBalancerProvider, constructorArgs: [cache, objectMapper, clusterProvider]) {
      fromCacheData(cacheData) >> { loadBalancer }
    }

    when:
    Set<OpenstackLoadBalancer> result = loadBalancerProvider.getApplicationLoadBalancers(app)

    then:
    1 * cache.filterIdentifiers(LOAD_BALANCERS.ns, Keys.getLoadBalancerKey(app, '*', '*', '*')) >> [lbKey].toSet()
    1 * cache.filterIdentifiers(LOAD_BALANCERS.ns, Keys.getLoadBalancerKey("$app-*", '*', '*', '*')) >> [lbKey].toSet()
    1 * cache.getAll(LOAD_BALANCERS.ns, [lbKey].toSet(), _ as RelationshipCacheFilter) >> cacheDataList
    result.size() == 1
    result[0] == loadBalancer
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
    OpenstackLoadBalancerProvider loadBalancerProvider = Spy(OpenstackLoadBalancerProvider, constructorArgs: [cache, objectMapper, clusterProvider]) {
      fromCacheData(cacheData) >> { loadBalancer }
    }
    List<String> filter = ['filter']

    when:
    Set<OpenstackLoadBalancer> result = loadBalancerProvider.getLoadBalancers(account, region, lbid)

    then:
    1 * cache.filterIdentifiers(LOAD_BALANCERS.ns, lbKey) >> filter
    1 * cache.getAll(LOAD_BALANCERS.ns, filter, _ as RelationshipCacheFilter) >> cacheDataList
    result.size() == 1
    result[0] == loadBalancer
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
    CacheData cacheData = Mock(CacheData)
    Map<String, Object> attributes = Mock(Map)
    String sgKey = Keys.getServerGroupKey(name, account, region)
    ServerGroup serverGroup = Mock(ServerGroup) {
      getName() >> { name }
      isDisabled() >> { false }
      getInstances() >> { [] }
    }
    OpenstackLoadBalancer loadBalancer = Mock(OpenstackLoadBalancer) {
      it.id >> { lbid }
      it.account >> { account }
      it.region >> { region }
      it.serverGroups >> { [new LoadBalancerServerGroup(name: name)] }
    }
    OpenstackLoadBalancerProvider loadBalancerProvider = new OpenstackLoadBalancerProvider(cache, objectMapper, clusterProvider)

    when:
    OpenstackLoadBalancer result = loadBalancerProvider.fromCacheData(cacheData)

    then:
    1 * cacheData.attributes >> attributes
    1 * objectMapper.convertValue(attributes, OpenstackLoadBalancer) >> loadBalancer
    1 * cacheData.relationships >> [(SERVER_GROUPS.ns):[sgKey]]
    1 * clusterProvider.getServerGroup(account, region, name) >> serverGroup
    result == loadBalancer
    noExceptionThrown()
  }

  def "test convert cache data to load balancer - exception"() {
    given:
    String lbid = 'lb1'
    String name = 'myapp-teststack-v002'
    CacheData cacheData = Mock(CacheData)
    Map<String, Object> attributes = Mock(Map)
    String sgKey = Keys.getServerGroupKey(name, account, region)
    OpenstackLoadBalancer loadBalancer = Mock(OpenstackLoadBalancer) {
      it.id >> { lbid }
      it.account >> { account }
      it.region >> { region }
      it.serverGroups >> { [new LoadBalancerServerGroup(name: name)] }
    }
    Throwable throwable = new JedisException('test')
    OpenstackLoadBalancerProvider loadBalancerProvider = new OpenstackLoadBalancerProvider(cache, objectMapper, clusterProvider)

    when:
    loadBalancerProvider.fromCacheData(cacheData)

    then:
    1 * cacheData.attributes >> attributes
    1 * objectMapper.convertValue(attributes, OpenstackLoadBalancer) >> loadBalancer
    1 * cacheData.relationships >> [(SERVER_GROUPS.ns):[sgKey]]
    1 * clusterProvider.getServerGroup(account, region, name) >> { throw throwable }
    Throwable thrownException = thrown(JedisException)
    throwable == thrownException
  }

}
