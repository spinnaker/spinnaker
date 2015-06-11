/*
 * Copyright 2014 Netflix, Inc.
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



package com.netflix.spinnaker.mort.aws.cache
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeKeyPairsResult
import com.amazonaws.services.ec2.model.KeyPairInfo
import com.netflix.spinnaker.mort.model.CacheService
import spock.lang.Specification
import spock.lang.Subject

class AmazonKeyPairCachingAgentSpec extends Specification {

  CacheService cacheService = Mock(CacheService)
  AmazonEC2 ec2 = Mock(AmazonEC2)

  @Subject
  AmazonKeyPairCachingAgent agent = new AmazonKeyPairCachingAgent(
          cacheService: cacheService,
          ec2: ec2,
          region: 'us-east-1',
          account: 'test')

  void "should add on initial run"() {
    when:
    agent.call()

    then:
    1 * ec2.describeKeyPairs() >> new DescribeKeyPairsResult(keyPairs: [
            new KeyPairInfo(keyName: 'key1', keyFingerprint: '1'),
            new KeyPairInfo(keyName: 'key2', keyFingerprint: '2')
    ])
    1 * cacheService.put('keyPairs:key1:test:us-east-1', new KeyPairInfo(keyName: 'key1', keyFingerprint: '1'))
    1 * cacheService.put('keyPairs:key2:test:us-east-1', new KeyPairInfo(keyName: 'key2', keyFingerprint: '2'))
    0 * _
  }

  void "should recache all on second run"() {
    when:
    agent.call()

    then:
    1 * ec2.describeKeyPairs() >> new DescribeKeyPairsResult(keyPairs: [
            new KeyPairInfo(keyName: 'key1', keyFingerprint: '1')
    ])
    1 * cacheService.put('keyPairs:key1:test:us-east-1', new KeyPairInfo(keyName: 'key1', keyFingerprint: '1'))
    0 * _

    when:
    agent.call()

    then:
    1 * ec2.describeKeyPairs() >> new DescribeKeyPairsResult(keyPairs: [
            new KeyPairInfo(keyName: 'key1', keyFingerprint: '1'),
            new KeyPairInfo(keyName: 'key2', keyFingerprint: '2')
    ])
    1 * cacheService.put('keyPairs:key1:test:us-east-1', new KeyPairInfo(keyName: 'key1', keyFingerprint: '1'))
    1 * cacheService.put('keyPairs:key2:test:us-east-1', new KeyPairInfo(keyName: 'key2', keyFingerprint: '2'))
    0 * _
  }

}
