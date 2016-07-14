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
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackInstanceType
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.INSTANCE_TYPES

class OpenstackInstanceTypeProviderSpec extends Specification {

  String account = 'test'
  String region = 'east'

  OpenstackInstanceTypeProvider instanceProvider
  Cache cache
  ObjectMapper objectMapper

  void "setup"() {
    cache = Mock(Cache)
    objectMapper = Mock(ObjectMapper)
    instanceProvider = new OpenstackInstanceTypeProvider(cache, objectMapper)
  }

  void "test get all"() {
    given:
    CacheData cacheData = Mock(CacheData)
    Map<String, Object> attributes = Mock(Map)
    OpenstackInstanceType openstackInstanceType = Mock(OpenstackInstanceType)

    when:
    Set<OpenstackInstanceType> result = instanceProvider.getAll()

    then:
    1 * cache.getAll(INSTANCE_TYPES.ns, _) >> [cacheData]
    1 * cacheData.attributes >> attributes
    1 * objectMapper.convertValue(attributes, OpenstackInstanceType) >> openstackInstanceType
    result == [openstackInstanceType].toSet()
    noExceptionThrown()
  }

  void "test get all exception thrown"() {
    given:
    Throwable throwable = new RuntimeException('test')

    when:
    instanceProvider.getAll()

    then:
    1 * cache.getAll(INSTANCE_TYPES.ns, _) >> { throw throwable }
    thrown(RuntimeException)
  }
}
