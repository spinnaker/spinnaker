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


package com.netflix.spinnaker.mort.aws.model
import com.amazonaws.services.ec2.model.KeyPair
import com.netflix.spinnaker.mort.aws.cache.Keys
import com.netflix.spinnaker.mort.model.CacheService
import spock.lang.Specification
import spock.lang.Subject

class AmazonKeyPairProviderSpec extends Specification {

    @Subject
    AmazonKeyPairProvider provider = new AmazonKeyPairProvider(cacheService: Mock(CacheService))

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
        1 * provider.cacheService.keysByType(Keys.Namespace.KEY_PAIRS) >> [
                'keyPairs:key1:test:us-east-1',
                'keyPairs:key2:prod:us-west-1'
        ]
        1 * provider.cacheService.retrieve('keyPairs:key1:test:us-east-1', KeyPair) >> new KeyPair(
                keyName: 'key1',
                keyFingerprint: '1'
        )
        1 * provider.cacheService.retrieve('keyPairs:key2:prod:us-west-1', KeyPair) >> new KeyPair(
                keyName: 'key2',
                keyFingerprint: '2'
        )
        0 * _
    }

}
