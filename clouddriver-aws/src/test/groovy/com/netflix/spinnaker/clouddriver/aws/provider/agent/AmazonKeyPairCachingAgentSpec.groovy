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

package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeKeyPairsResult
import com.amazonaws.services.ec2.model.KeyPairInfo
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import spock.lang.Specification
import spock.lang.Subject

class AmazonKeyPairCachingAgentSpec extends Specification {
  static final String account = 'test'
  static final String region = 'us-east-1'

  AmazonCloudProvider amazonCloudProvider = new AmazonCloudProvider()

  AmazonEC2 ec2 = Mock(AmazonEC2)

  ProviderCache providerCache = Mock(ProviderCache)

  NetflixAmazonCredentials creds = Stub(NetflixAmazonCredentials) {
    getName() >> account
  }

  AmazonClientProvider amazonClientProvider = Stub(AmazonClientProvider) {
    getAmazonEC2(creds, region) >> ec2
  }

  @Subject
  AmazonKeyPairCachingAgent agent = new AmazonKeyPairCachingAgent(
    amazonCloudProvider, amazonClientProvider, creds, region)

  void "should add on loadData"() {
    when:
    def result = agent.loadData(providerCache)

    then:
    1 * ec2.describeKeyPairs() >> new DescribeKeyPairsResult(keyPairs: [
      new KeyPairInfo(keyName: 'key1', keyFingerprint: '1'),
      new KeyPairInfo(keyName: 'key2', keyFingerprint: '2')
    ])
    with (result.cacheResults.get(Keys.Namespace.KEY_PAIRS.ns)) { List<CacheData> cd ->
      cd.size() == 2
      def k1 = cd.find { it.id == Keys.getKeyPairKey(amazonCloudProvider, 'key1', region, account) }
      k1.attributes.keyName == 'key1'
      k1.attributes.keyFingerprint == '1'
      def k2 = cd.find { it.id == Keys.getKeyPairKey(amazonCloudProvider, 'key2', region, account) }
      k2.attributes.keyName == 'key2'
      k2.attributes.keyFingerprint == '2'
    }
    0 * _
  }
}
