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
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackApplication
import org.mockito.internal.util.collections.Sets
import redis.clients.jedis.exceptions.JedisException
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.APPLICATIONS
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.CLUSTERS

class OpenstackApplicationProviderSpec extends Specification {

  String account = 'test'

  OpenstackApplicationProvider provider
  Cache cache
  ObjectMapper objectMapper

  void "setup"() {
    objectMapper = Mock(ObjectMapper)
    cache = Mock(Cache)
    provider = new OpenstackApplicationProvider(cache, objectMapper)
  }

  void "test get applications"() {
    given:
    String appName = 'app'
    String cluster = "$appName-stack-detail-v000"
    String clusterKey = Keys.getClusterKey(account, appName, cluster)
    String dataKey = Keys.getApplicationKey(appName)
    Map<String, Object> relationships = [(CLUSTERS.ns) : [clusterKey]]
    Map<String, String> attributes = [application: appName]
    CacheData mockData = Mock(CacheData)
    Collection<CacheData> cacheData = [mockData]
    OpenstackApplication expected = new OpenstackApplication(appName, attributes, [(account): Sets.newSet(cluster)])
    Collection<String> filters = Mock(Collection)

    when:
    Set<OpenstackApplication> result = provider.getApplications(false)

    then:
    1 * cache.filterIdentifiers(APPLICATIONS.ns, "${OpenstackCloudProvider.ID}:*") >> filters
    1 * cache.getAll(APPLICATIONS.ns, filters, _) >> cacheData
    1 * mockData.id >> dataKey
    1 * mockData.attributes >> attributes
    1 * mockData.getRelationships() >> relationships
    1 * objectMapper.convertValue(attributes, OpenstackApplication.ATTRIBUTES) >> attributes
    result?.first() == expected
    noExceptionThrown()
  }

  void "test get applications no results"() {
    given:
    Collection<String> filters = Mock(Collection)

    when:
    Set<OpenstackApplication> result = provider.getApplications(false)

    then:
    1 * cache.filterIdentifiers(APPLICATIONS.ns, "${OpenstackCloudProvider.ID}:*") >> filters
    1 * cache.getAll(APPLICATIONS.ns, filters, _) >> []
    0 * _
    result.isEmpty()
    noExceptionThrown()
  }

  void "test get applications exception"() {
    given:
    Throwable throwable = new JedisException('test')
    Collection<String> filters = Mock(Collection)

    when:
    provider.getApplications(false)

    then:
    1 * cache.filterIdentifiers(APPLICATIONS.ns, "${OpenstackCloudProvider.ID}:*") >> filters
    1 * cache.getAll(APPLICATIONS.ns, filters, _) >> { throw throwable }
    JedisException exception = thrown(JedisException)
    exception == throwable
  }

  void "test get application"() {
    given:
    String appName = 'app'
    String cluster = "$appName-stack-detail-v000"
    String clusterKey = Keys.getClusterKey(account, appName, cluster)
    String dataKey = Keys.getApplicationKey(appName)
    Map<String, Object> relationships = [(CLUSTERS.ns) : [clusterKey]]
    Map<String, String> attributes = [application: appName]
    CacheData cacheData = Mock(CacheData)
    OpenstackApplication expected = new OpenstackApplication(appName, attributes, [(account): Sets.newSet(cluster)])

    when:
    OpenstackApplication result = provider.getApplication(appName)

    then:
    1 * cache.get(APPLICATIONS.ns, dataKey) >> cacheData
    1 * cacheData.id >> dataKey
    1 * cacheData.attributes >> attributes
    1 * objectMapper.convertValue(attributes, OpenstackApplication.ATTRIBUTES) >> attributes
    1 * cacheData.getRelationships() >> relationships
    result == expected
    noExceptionThrown()
  }

  void "test get application no result"() {
    given:
    String appName = 'appName'
    String appKey = Keys.getApplicationKey(appName)

    when:
    OpenstackApplication result = provider.getApplication(appName)

    then:
    1 * cache.get(APPLICATIONS.ns, appKey) >> null
    0 * _
    result == null
    noExceptionThrown()
  }

  void "test get application exception"() {
    given:
    String appName = 'appName'
    String appKey = Keys.getApplicationKey(appName)
    Throwable throwable = new JedisException('test')

    when:
    provider.getApplication(appName)

    then:
    1 * cache.get(APPLICATIONS.ns, appKey) >> { throw throwable }
    JedisException exception = thrown(JedisException)
    exception == throwable
  }
}
