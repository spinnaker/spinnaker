/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackCluster
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackLoadBalancer
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackServerGroup
import org.mockito.internal.util.collections.Sets
import redis.clients.jedis.exceptions.JedisException
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.APPLICATIONS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.CLUSTERS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SERVER_GROUPS

@Unroll
class OpenstackClusterProviderSpec extends Specification {

  @Shared
  String account = 'test'

  OpenstackClusterProvider provider
  OpenstackInstanceProvider instanceProvider
  Cache cache
  ObjectMapper objectMapper

  void "setup"() {
    objectMapper = new ObjectMapper()
    cache = Mock(Cache)
    instanceProvider = Mock(OpenstackInstanceProvider)
    provider = Spy(OpenstackClusterProvider, constructorArgs: [cache, objectMapper, instanceProvider])
  }

  void "test all get clusters"() {
    given:
    CacheData cacheData = Mock(CacheData)
    Map<String, Object> attributes = [name: 'name', accountName: account]

    when:
    Map<String, Set<OpenstackCluster>> result = provider.getClusters()

    then:
    1 * cache.getAll(CLUSTERS.ns) >> [cacheData]
    1 * cacheData.attributes >> attributes
    result == [(account): [new OpenstackCluster(attributes)].toSet()]
    noExceptionThrown()
  }

  void "test get clusters exception"() {
    given:
    Throwable throwable = new JedisException('test')

    when:
    provider.getClusters()

    then:
    1 * cache.getAll(CLUSTERS.ns) >> { throw throwable }
    Throwable thrownException = thrown(JedisException)
    throwable == thrownException
  }

  void "test get clusters internal"() {
    given:
    boolean details = false
    String appName = 'app'
    String appKey = Keys.getApplicationKey(appName)
    CacheData appCache = Mock(CacheData)
    Map<String, Collection<String>> relationships = Mock(Map)
    Collection<String> clusterKeys = Mock(Collection)
    CacheData clusterData = Mock(CacheData)
    Collection<CacheData> clusters = [clusterData]
    OpenstackCluster cluster = Mock(OpenstackCluster)

    when:
    Map<String, Set<OpenstackCluster>> result = provider.getClustersInternal(appName, details)

    then:
    1 * cache.get(APPLICATIONS.ns, appKey) >> appCache
    1 * appCache.relationships >> relationships
    1 * relationships.get(CLUSTERS.ns) >> clusterKeys
    1 * cache.getAll(CLUSTERS.ns, clusterKeys, _) >> clusters
    1 * provider.clusterFromCacheData(_, _) >> cluster
    1 * cluster.accountName >> account

    and:
    result == [(account): [cluster].toSet()]
  }

  void "test get clusters internal exception"() {
    given:
    boolean details = false
    String appName = 'app'
    String appKey = Keys.getApplicationKey(appName)
    Throwable throwable = new JedisException('test')

    when:
    provider.getClustersInternal(appName, details)

    then:
    1 * cache.get(APPLICATIONS.ns, appKey) >> { throw throwable }
    Throwable thrownException = thrown(JedisException)
    throwable == thrownException
  }

  void "test get cluster summaries by app"() {
    given:
    String appName = 'app'
    Map<String, Set<OpenstackCluster>> attributes = Mock(Map)

    when:
    Map<String, Set<OpenstackCluster>> result = provider.getClusterSummaries(appName)

    then:
    1 * provider.getClustersInternal(appName, false) >> attributes
    result == attributes
    noExceptionThrown()
  }

  void "test get cluster details by app"() {
    given:
    String appName = 'app'
    Map<String, Set<OpenstackCluster>> attributes = Mock(Map)

    when:
    Map<String, Set<OpenstackCluster>> result = provider.getClusterDetails(appName)

    then:
    1 * provider.getClustersInternal(appName, true) >> attributes
    result == attributes
    noExceptionThrown()
  }

  void "test get clusters by app and account - #testCase"() {
    given:
    String appName = 'app'

    when:
    Set<OpenstackCluster> result = provider.getClusters(appName, account)

    then:
    1 * provider.getClusterDetails(appName) >> details
    result == expected
    noExceptionThrown()

    where:
    testCase       | details                                          | expected
    'normal'       | [(account): Sets.newSet(new OpenstackCluster())] | Sets.newSet(new OpenstackCluster())
    'empty'        | [(account): Sets.newSet()]                       | Sets.newSet()
    'null'         | [(account): null]                                | null
    'missing'      | [:]                                              | null
    'null details' | null                                             | null
  }

  void "test get cluster by app, account, name - #testCase"() {
    given:
    String appName = 'app'
    String name = 'name'

    when:
    OpenstackCluster result = provider.getCluster(appName, account, name)

    then:
    1 * provider.getClusters(appName, account) >> details
    result == expected
    noExceptionThrown()

    where:
    testCase    | details                                          | expected
    'normal'    | Sets.newSet(new OpenstackCluster(name: 'name'))  | new OpenstackCluster(name: 'name')
    'missing'   | Sets.newSet(new OpenstackCluster(name: 'namez')) | null
    'empty set' | Sets.newSet()                                    | null
    'null set'  | null                                             | null
  }

  void "test server group - #testCase"() {
    given:
    String name = 'name'
    String region = 'region'
    String serverGroupKey = Keys.getServerGroupKey(name, account, region)

    when:
    ServerGroup result = provider.getServerGroup(account, region, name)

    then:
    1 * cache.get(SERVER_GROUPS.ns, serverGroupKey, _) >> cacheData
    if (cacheData) {
      1 * provider.serverGroupFromCacheData(cacheData) >> expected
    }
    result == expected
    noExceptionThrown()

    where:
    testCase  | cacheData       | expected
    'normal'  | Mock(CacheData) | Mock(OpenstackServerGroup)
    'no data' | null            | null
  }

  void "test server group exception"() {
    given:
    String name = 'name'
    String region = 'region'
    String serverGroupKey = Keys.getServerGroupKey(name, account, region)
    Throwable throwable = new JedisException('test')

    when:
    provider.getServerGroup(account, region, name)

    then:
    1 * cache.get(SERVER_GROUPS.ns, serverGroupKey, _) >> { throw throwable }
    Throwable thrownException = thrown(JedisException)
    throwable == thrownException
  }

  void "test cluster from cache data"() {
    given:
    boolean details = true
    CacheData cacheData = Mock(CacheData)
    Map<String, Collection<String>> relationships = Mock(Map)
    Collection<String> serverGroupKeys = Mock(Collection)
    Map<String, Object> attributes = [accountName: account, name: 'name']
    CacheData serverGroupCache = Mock(CacheData)
    OpenstackServerGroup openstackServerGroup = Mock(OpenstackServerGroup)
    OpenstackLoadBalancer openstackLoadBalancer = Mock(OpenstackLoadBalancer)

    when:
    OpenstackCluster result = provider.clusterFromCacheData(cacheData, details)

    then:
    1 * cacheData.attributes >> attributes
    1 * cacheData.relationships >> relationships
    1 * relationships.get(SERVER_GROUPS.ns) >> serverGroupKeys
    1 * cache.getAll(SERVER_GROUPS.ns, serverGroupKeys, _) >> [serverGroupCache]
    1 * provider.serverGroupFromCacheData(serverGroupCache) >> openstackServerGroup
    1 * provider.loadBalancersFromCacheData(serverGroupCache) >> [openstackLoadBalancer]

    and:
    result == new OpenstackCluster(accountName: account, name: 'name', serverGroups: [openstackServerGroup].toSet(), loadBalancers: [openstackLoadBalancer].toSet())
    noExceptionThrown()
  }

  void "test cluster from cache data - no server groups"() {
    given:
    boolean details = true
    CacheData cacheData = Mock(CacheData)
    Map<String, Collection<String>> relationships = Mock(Map)
    Collection<String> serverGroupKeys = null
    Map<String, Object> attributes = [accountName: account, name: 'name']

    when:
    OpenstackCluster result = provider.clusterFromCacheData(cacheData, details)

    then:
    1 * cacheData.attributes >> attributes
    1 * cacheData.relationships >> relationships
    1 * relationships.get(SERVER_GROUPS.ns) >> serverGroupKeys

    and:
    result == new OpenstackCluster(accountName: account, name: 'name')
    noExceptionThrown()
  }

  void "test cluster from cache data - exception"() {
    given:
    boolean details = true
    CacheData cacheData = Mock(CacheData)
    Map<String, Collection<String>> relationships = Mock(Map)
    Collection<String> serverGroupKeys = Mock(Collection)
    Map<String, Object> attributes = [accountName: account, name: 'name']
    Throwable throwable = new JedisException('test')

    when:
    provider.clusterFromCacheData(cacheData, details)

    then:
    1 * cacheData.attributes >> attributes
    1 * cacheData.relationships >> relationships
    1 * relationships.get(SERVER_GROUPS.ns) >> serverGroupKeys
    1 * cache.getAll(SERVER_GROUPS.ns, serverGroupKeys, _) >> { throw throwable }

    and:
    Throwable thrownException = thrown(JedisException)
    throwable == thrownException
  }

  void "test server group from cache data no keys"() {
    given:
    CacheData cacheData = Mock(CacheData)
    Map<String, Collection<String>> relationships = Mock(Map)
    Collection<String> instanceKeys = null
    Map<String, Object> attributes = [account: account, name: 'name', region: 'region']

    when:
    OpenstackServerGroup result = provider.serverGroupFromCacheData(cacheData)

    then:
    1 * cacheData.attributes >> attributes
    1 * cacheData.relationships >> relationships
    1 * relationships.get(INSTANCES.ns) >> instanceKeys
    0 * instanceProvider.getInstances(instanceKeys)

    and:
    result == new OpenstackServerGroup(attributes)
    noExceptionThrown()
  }

  void "test server group from cache data"() {
    given:
    CacheData cacheData = Mock(CacheData)
    Map<String, Collection<String>> relationships = Mock(Map)
    Collection<String> instanceKeys = Mock(Collection)
    Map<String, Object> attributes = [account: account, name: 'name', region: 'region']
    Instance instance = Mock(Instance)
    Set<Instance> instances = [instance].toSet()
    String zone = 'zone1'

    when:
    OpenstackServerGroup result = provider.serverGroupFromCacheData(cacheData)

    then:
    1 * cacheData.attributes >> attributes
    1 * cacheData.relationships >> relationships
    1 * relationships.get(INSTANCES.ns) >> instanceKeys
    1 * instanceProvider.getInstances(instanceKeys) >> instances
    1 * instance.zone >> zone

    and:
    result == new OpenstackServerGroup(account: account, name: 'name', region: 'region', instances: instances, zones: [zone].toSet())
    noExceptionThrown()
  }

  void "test server group from cache data exception"() {
    given:
    CacheData cacheData = Mock(CacheData)
    Map<String, Collection<String>> relationships = Mock(Map)
    Collection<String> instanceKeys = Mock(Collection)
    Map<String, Object> attributes = [account: account, name: 'name', region: 'region']
    Throwable throwable = new JedisException('test')

    when:
    provider.serverGroupFromCacheData(cacheData)

    then:
    1 * cacheData.attributes >> attributes
    1 * cacheData.relationships >> relationships
    1 * relationships.get(INSTANCES.ns) >> instanceKeys
    1 * instanceProvider.getInstances(instanceKeys) >> { throw throwable }

    and:
    Throwable thrownException = thrown(JedisException)
    throwable == thrownException
  }

  void "test load balancer from cache data - #testCase"() {
    given:
    CacheData cacheData = Mock(CacheData)
    Map<String, Collection<String>> relationships = Mock(Map)
    CacheData loadBalancerCache = Mock(CacheData)
    Map<String, Object> attributes = [account: account, name: 'name', region: 'region']

    when:
    List<OpenstackLoadBalancer> result = provider.loadBalancersFromCacheData(cacheData)

    then:
    1 * cacheData.relationships >> relationships
    1 * relationships.get(LOAD_BALANCERS.ns) >> loadbalancerKeys
    if (loadbalancerKeys) {
      1 * cache.getAll(LOAD_BALANCERS.ns, loadbalancerKeys) >> [loadBalancerCache]
      1 * loadBalancerCache.attributes >> attributes
    }

    and:
    result == expected
    noExceptionThrown()

    where:
    testCase       | loadbalancerKeys | expected
    'no instances' | null             | []
    'some'         | Mock(Collection) | [new OpenstackLoadBalancer(account: account, name: 'name', region: 'region')]
  }

  void "test load balancer from cache data"() {
    given:
    CacheData cacheData = Mock(CacheData)
    Map<String, Collection<String>> relationships = Mock(Map)
    Collection<String> loadbalancerKeys = Mock(Collection)
    Throwable throwable = new JedisException('test')

    when:
    provider.loadBalancersFromCacheData(cacheData)

    then:
    1 * cacheData.relationships >> relationships
    1 * relationships.get(LOAD_BALANCERS.ns) >> loadbalancerKeys
    1 * cache.getAll(LOAD_BALANCERS.ns, loadbalancerKeys) >> { throw throwable }

    and:
    Throwable thrownException = thrown(JedisException)
    throwable == thrownException
  }
}
