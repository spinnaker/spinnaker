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

import com.netflix.spinnaker.mort.aws.cache.Keys
import com.netflix.spinnaker.mort.model.CacheService
import spock.lang.Specification
import spock.lang.Subject

class AmazonInstanceTypeProviderSpec extends Specification {

    @Subject
    AmazonInstanceTypeProvider provider = new AmazonInstanceTypeProvider(cacheService: Mock(CacheService))

    void "should retrieve all instance types"() {
        when:
        def result = provider.getAll()

        then:
        result == [
                new AmazonInstanceType(
                        account: 'test',
                        region: 'us-east-1',
                        name: 'm1.large',
                        availabilityZone: 'us-east-1a'
                ),
                new AmazonInstanceType(
                        account: 'prod',
                        region: 'us-west-1',
                        name: 'm1.medium',
                        availabilityZone: 'us-west-1b'
                )
        ] as Set

        and:
        1 * provider.cacheService.keysByType(Keys.Namespace.INSTANCE_TYPES) >> [
                'keyPairs:itp1:test:us-east-1',
                'keyPairs:itp2:prod:us-west-1'
        ]
        1 * provider.cacheService.retrieve('keyPairs:itp1:test:us-east-1', AmazonInstanceType) >> new AmazonInstanceType(
                account: 'test',
                region: 'us-east-1',
                name: 'm1.large',
                availabilityZone: 'us-east-1a'
        )
        1 * provider.cacheService.retrieve('keyPairs:itp2:prod:us-west-1', AmazonInstanceType) >> new AmazonInstanceType(
                account: 'prod',
                region: 'us-west-1',
                name: 'm1.medium',
                availabilityZone: 'us-west-1b'
        )
        0 * _
    }

}
