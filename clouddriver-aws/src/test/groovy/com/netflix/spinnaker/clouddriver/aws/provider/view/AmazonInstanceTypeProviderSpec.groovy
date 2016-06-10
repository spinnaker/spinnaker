/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.model.AmazonInstanceType
import spock.lang.Specification
import spock.lang.Subject

class AmazonInstanceTypeProviderSpec extends Specification {

  Cache cache = Mock(Cache)
  @Subject
  AmazonInstanceTypeProvider provider = new AmazonInstanceTypeProvider(cache, new ObjectMapper())

  void "should retrieve all instance types"() {
    when:
    def result = provider.getAll()

    then:
    result == [
      new AmazonInstanceType(
        account: 'test',
        region: 'us-east-1',
        name: 'm1.large',
      ),
      new AmazonInstanceType(
        account: 'prod',
        region: 'us-west-1',
        name: 'm1.medium',
      )
    ] as Set

    and:
    1 * cache.getAll(Keys.Namespace.INSTANCE_TYPES.ns, _ as CacheFilter) >> [
      itData('m1.large', [
        account         : 'test',
        region          : 'us-east-1',
        name            : 'm1.large']),
      itData('m1.medium', [
        account         : 'prod',
        region          : 'us-west-1',
        name            : 'm1.medium']),
    ]
  }

  private CacheData itData(String instanceType, Map params) {
    def defaults = [
      account           : 'prod',
      region            : 'us-east-1',
      name              : 'm1.xlarge',
    ]

    def attributes = defaults + params
    new DefaultCacheData(Keys.getInstanceTypeKey(instanceType, attributes.region, attributes.account), attributes, [:])
  }
}
