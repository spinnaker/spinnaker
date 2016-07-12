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
import com.google.common.collect.Sets
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.model.Image
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackImage
import redis.clients.jedis.exceptions.JedisException
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.IMAGES

class OpenstackImageProviderSpec extends Specification {

  String account = 'test'
  String region = 'region'

  OpenstackImageProvider imageProvider
  Cache cache
  ObjectMapper objectMapper

  void "setup"() {
    cache = Mock(Cache)
    objectMapper = Mock(ObjectMapper)
    imageProvider = new OpenstackImageProvider(cache, objectMapper)
  }

  void "test get images by account"() {
    given:
    String id = UUID.randomUUID().toString()
    Collection<String> filters = Mock(Collection)
    CacheData cacheData = Mock(CacheData)
    Map<String, Object> attributes = Mock(Map)
    OpenstackImage openstackImage = Mock(OpenstackImage)

    when:
    Map<String, Set<Image>> result = imageProvider.listImagesByAccount()

    then:
    1 * cache.filterIdentifiers(IMAGES.ns, "$OpenstackCloudProvider.ID:*") >> filters
    1 * cache.getAll(IMAGES.ns, filters) >> [cacheData]
    1 * cacheData.id >> Keys.getImageKey(id, account, region)
    1 * cacheData.attributes >> attributes
    1 * objectMapper.convertValue(attributes, OpenstackImage) >> openstackImage
    result == [(account): Sets.newHashSet(openstackImage)]
    noExceptionThrown()
  }

  void "test get images by account - exception thrown"() {
    given:
    Collection<String> filters = Mock(Collection)
    Throwable throwable = new JedisException('test')

    when:
    imageProvider.listImagesByAccount()

    then:
    1 * cache.filterIdentifiers(IMAGES.ns, "$OpenstackCloudProvider.ID:*") >> filters
    1 * cache.getAll(IMAGES.ns, filters) >> { throw throwable }
    thrown(JedisException)
  }
}
