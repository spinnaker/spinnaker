/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.netflix.spinnaker.clouddriver.openstack.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Timer
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.openstack.provider.agent.AbstractOpenstackCachingAgent
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.clouddriver.cache.OnDemandAgent.OnDemandType.LoadBalancer
import static com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider.getID
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.ON_DEMAND

@Unroll
class OnDemandAwareSpec extends Specification {

  OnDemandAware onDemandAware
  ObjectMapper objectMapper
  OpenstackNamedAccountCredentials namedAccountCredentials

  @Shared
  String region = 'region'
  @Shared
  String account = 'account'

  void 'setup'() {
    namedAccountCredentials = Mock(OpenstackNamedAccountCredentials)
    objectMapper = new ObjectMapper()
    onDemandAware = new DefaultOnDemandAware(namedAccountCredentials, region)
  }

  void 'should use on demand data empty'() {
    given:
    CacheResultBuilder cacheResultBuilder = new CacheResultBuilder(startTime: Long.MAX_VALUE)
    String serverGroupKey = UUID.randomUUID().toString()

    when:
    boolean result = onDemandAware.shouldUseOnDemandData(cacheResultBuilder, serverGroupKey)

    then:
    !result
  }

  void 'should use on demand data - #testCase'() {
    given:
    String serverGroupKey = UUID.randomUUID().toString()
    CacheResultBuilder cacheResultBuilder = new CacheResultBuilder(startTime: 5)
    cacheResultBuilder.onDemand.toKeep[serverGroupKey] = new DefaultCacheData('id', attributes, [:])

    when:
    boolean result = onDemandAware.shouldUseOnDemandData(cacheResultBuilder, serverGroupKey)

    then:
    result == expectedResult

    where:
    testCase               | attributes     | expectedResult
    'cache time greater'   | [cacheTime: 6] | true
    'cache time equal'     | [cacheTime: 5] | true
    'cache time less than' | [cacheTime: 4] | false
  }

  void 'move on demand data to namespace'() {
    given:
    String serverGroupKey = UUID.randomUUID().toString()
    CacheResultBuilder cacheResultBuilder = new CacheResultBuilder()
    Map cacheData = ['test': [[id: serverGroupKey, attributes: ['key1': 'value1'], relationships: ['key2': ['value2']]]]]
    String cacheDataString = objectMapper.writeValueAsString(cacheData)
    cacheResultBuilder.onDemand.toKeep[serverGroupKey] = [attributes: [cacheResults: cacheDataString]]

    when:
    onDemandAware.moveOnDemandDataToNamespace(objectMapper, onDemandAware.typeReference, cacheResultBuilder, serverGroupKey)

    then:
    cacheResultBuilder.onDemand.toKeep[serverGroupKey] == null
    cacheResultBuilder.namespace('test').keep(serverGroupKey).id == serverGroupKey
    cacheResultBuilder.namespace('test').keep(serverGroupKey).attributes == cacheData['test'].first().attributes
    cacheResultBuilder.namespace('test').keep(serverGroupKey).relationships == cacheData['test'].first().relationships
  }

  void 'get all on demand cache by region and account'() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    String lbKey = Keys.getLoadBalancerKey('name', 'id', account, region)
    Collection<String> keys = [lbKey]
    Map<String, Object> attributes = [cacheTime: System.currentTimeMillis(), processedCount: 10, processedTime: System.currentTimeMillis()]
    CacheData cacheData = new DefaultCacheData(lbKey, attributes, [:])

    when:
    Collection<Map> result = onDemandAware.getAllOnDemandCacheByRegionAndAccount(providerCache, account, region)

    then:
    1 * providerCache.getIdentifiers(ON_DEMAND.ns) >> keys
    1 * providerCache.getAll(ON_DEMAND.ns, keys) >> [cacheData]

    and:
    result == [[details: Keys.parse(lbKey), cacheTime: attributes.cacheTime, processedCount: attributes.processedCount, processedTime: attributes.processedTime]]
  }

  void 'get all on demand cache by region and account - empty'() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)

    when:
    Collection<Map> result = onDemandAware.getAllOnDemandCacheByRegionAndAccount(providerCache, account, region)

    then:
    1 * providerCache.getIdentifiers(ON_DEMAND.ns) >> []
    1 * providerCache.getAll(ON_DEMAND.ns, []) >> []

    and:
    result == []
  }

  void 'build On Demand Cache - #testCase'() {
    given:
    Object object = Mock(Object)
    String onDemandType = 'type'
    CacheResult cacheResult = Mock(CacheResult)
    String namespace = 'namespace'

    when:
    OnDemandAgent.OnDemandResult result = onDemandAware.buildOnDemandCache(object, onDemandType, cacheResult, namespace, key)

    then:
    result.sourceAgentType == onDemandType
    result.cacheResult == cacheResult
    result.evictions.isEmpty()

    where:
    testCase      | key
    'with key'    | 'key'
    'without key' | null
  }

  void 'build On Demand Cache with evictions'() {
    given:
    Object object = null
    String onDemandType = 'type'
    CacheResult cacheResult = Mock(CacheResult)
    String namespace = 'namespace'
    String key = 'key'

    when:
    OnDemandAgent.OnDemandResult result = onDemandAware.buildOnDemandCache(object, onDemandType, cacheResult, namespace, key)

    then:
    result.sourceAgentType == onDemandType
    result.cacheResult == cacheResult
    result.evictions[namespace] == [key]
  }

  void 'resolve key - #testCase'() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    String namespace = 'namespace'

    when:
    String result = onDemandAware.resolveKey(providerCache, namespace, key)

    then:
    calls * providerCache.filterIdentifiers(namespace, key) >> lookupKeys

    and:
    result == expected
    noExceptionThrown()

    where:
    testCase               | key    | calls | lookupKeys | expected
    'no asterik'           | 'key'  | 0     | []         | 'key'
    'asterik - one result' | 'key*' | 1     | ['keykey'] | 'keykey'
  }

  void 'resolve key exception - #testCase'() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    String namespace = 'namespace'

    when:
    onDemandAware.resolveKey(providerCache, namespace, key)

    then:
    calls * providerCache.filterIdentifiers(namespace, key) >> lookupKeys

    and:
    thrown(UnresolvableKeyException)

    where:
    testCase             | key    | calls | lookupKeys
    'asterik - empty'    | 'key*' | 1     | []
    'asterik - multiple' | 'key*' | 1     | ['keya', 'keyb']
  }

  void 'process on demand cache - evict deleted items'() {
    given:
    CacheResult cacheResult = Mock(CacheResult)
    OnDemandMetricsSupport onDemandMetricsSupport = Mock(OnDemandMetricsSupport)
    ProviderCache providerCache = Mock(ProviderCache)
    String key = 'key'

    when:
    onDemandAware.processOnDemandCache(cacheResult, objectMapper, onDemandMetricsSupport, providerCache, key)

    then:
    1 * cacheResult.cacheResults >> [:]
    1 * providerCache.evictDeletedItems(ON_DEMAND.ns, [key])
  }

  void 'process on demand cache - put cache data'() {
    given:
    CacheResult cacheResult = Mock(CacheResult)
    Registry registry = Stub(Registry) {
      timer(_, _) >> Mock(Timer)
    }

    OnDemandMetricsSupport onDemandMetricsSupport = new OnDemandMetricsSupport(registry, onDemandAware, "${ID}:${LoadBalancer}")
    ProviderCache providerCache = Mock(ProviderCache)
    String key = 'key'
    CacheData cacheData = new DefaultCacheData('id', [:], [:])
    Map<String, Collection<CacheData>> results = ['test': [cacheData]]

    when:
    onDemandAware.processOnDemandCache(cacheResult, objectMapper, onDemandMetricsSupport, providerCache, key)

    then:
    _ * cacheResult.cacheResults >> results
    1 * providerCache.putCacheData(ON_DEMAND.ns, _)

  }

  void 'build Load Data Cache - keep cache'() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    String key = 'key'
    List<String> keys = [key]
    Closure<CacheResult> closure = { CacheResultBuilder builder -> builder.build() }
    CacheData cacheData = Mock(CacheData)
    Map<String, Object> attributes = Mock(Map)

    when:
    CacheResult result = onDemandAware.buildLoadDataCache(providerCache, keys, closure)

    then:
    1 * providerCache.getAll(ON_DEMAND.ns, keys) >> [cacheData]
    _ * cacheData.attributes >> attributes
    1 * attributes.get('cacheTime') >> System.currentTimeMillis() + 100
    1 * attributes.get('processedCount') >> 0

    and:
    !result.cacheResults[ON_DEMAND.ns].isEmpty()
    result.evictions.isEmpty()
  }

  void 'build Load Data Cache - evict cache'() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    String key = 'key'
    List<String> keys = [key]
    Closure<CacheResult> closure = { CacheResultBuilder builder -> builder.build() }
    CacheData cacheData = Mock(CacheData)
    Map<String, Object> attributes = Mock(Map)

    when:
    CacheResult result = onDemandAware.buildLoadDataCache(providerCache, keys, closure)

    then:
    1 * providerCache.getAll(ON_DEMAND.ns, keys) >> [cacheData]
    _ * cacheData.attributes >> attributes
    1 * attributes.get('cacheTime') >> System.currentTimeMillis() - 100
    1 * attributes.get('processedCount') >> 1

    and:
    result.cacheResults[ON_DEMAND.ns].isEmpty()
    !result.evictions.isEmpty()
  }

  public class DefaultOnDemandAware extends AbstractOpenstackCachingAgent implements OnDemandAgent {

    Collection<AgentDataType> providedDataTypes = Collections.emptyList()
    String agentType = 'agentType'
    String onDemandAgentType = 'agentTypeOndemand'

    DefaultOnDemandAware(OpenstackNamedAccountCredentials account, String region) {
      super(account, region)
    }

    @Override
    CacheResult loadData(ProviderCache providerCache) {
      return null
    }

    @Override
    OnDemandMetricsSupport getMetricsSupport() {
      return null
    }

    @Override
    boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
      return false
    }

    @Override
    OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
      return null
    }

    @Override
    Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
      return null
    }
  }
}
