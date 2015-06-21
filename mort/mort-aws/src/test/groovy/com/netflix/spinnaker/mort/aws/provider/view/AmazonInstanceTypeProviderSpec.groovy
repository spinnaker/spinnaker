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

package com.netflix.spinnaker.mort.aws.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.mort.aws.cache.Keys
import com.netflix.spinnaker.mort.aws.model.AmazonInstanceType
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
        availabilityZone: 'us-east-1a',
        productDescription: 'sweet instance',
        durationSeconds: 9001
      ),
      new AmazonInstanceType(
        account: 'prod',
        region: 'us-west-1',
        name: 'm1.medium',
        availabilityZone: 'us-west-1b',
        productDescription: 'sweet instance',
        durationSeconds: 9001
      )
    ] as Set

    and:
    1 * cache.getAll(Keys.Namespace.INSTANCE_TYPES.ns, _ as CacheFilter) >> [
      itData('1', [
        account         : 'test',
        region          : 'us-east-1',
        name            : 'm1.large',
        availabilityZone: 'us-east-1a']),
      itData('2', [
        account         : 'prod',
        region          : 'us-west-1',
        name            : 'm1.medium',
        availabilityZone: 'us-west-1b'])
    ]
  }

  private CacheData itData(String offeringId, Map params) {
    def defaults = [
      account           : 'prod',
      region            : 'us-east-1',
      name              : 'm1.xlarge',
      availabilityZone  : 'us-east-1a',
      productDescription: 'sweet instance',
      durationSeconds   : 9001
    ]

    def attributes = defaults + params
    new DefaultCacheData(Keys.getInstanceTypeKey(offeringId, attributes.region, attributes.account), attributes, [:])
  }
}
