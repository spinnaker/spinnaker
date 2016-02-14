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

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.model.AmazonKeyPair
import spock.lang.Specification
import spock.lang.Subject

class AmazonKeyPairProviderSpec extends Specification {

  AmazonCloudProvider amazonCloudProvider = new AmazonCloudProvider()
  Cache cache = Mock(Cache)

  @Subject
  AmazonKeyPairProvider provider = new AmazonKeyPairProvider(amazonCloudProvider, cache)

  void "should retrieve all key Pairs"() {
    when:
    def result = provider.getAll()

    then:
    result == [
      new AmazonKeyPair(
        account: 'test',
        region: 'us-east-1',
        keyName: 'key1',
        keyFingerprint: '1'
      ),
      new AmazonKeyPair(
        account: 'prod',
        region: 'us-west-1',
        keyName: 'key2',
        keyFingerprint: '2'
      )
    ] as Set

    and:
    1 * cache.getAll(Keys.Namespace.KEY_PAIRS.ns, _ as CacheFilter) >> [
      kpData('test', 'us-east-1', 'key1', '1'),
      kpData('prod', 'us-west-1', 'key2', '2')
    ]
    0 * _
  }

  CacheData kpData(String account, String region, String key, String fingerprint) {
    def attributes = [
      account       : account,
      region        : region,
      keyName       : key,
      keyFingerprint: fingerprint
    ]
    new DefaultCacheData(Keys.getKeyPairKey(amazonCloudProvider, key, region, account), attributes, [:])
  }
}
