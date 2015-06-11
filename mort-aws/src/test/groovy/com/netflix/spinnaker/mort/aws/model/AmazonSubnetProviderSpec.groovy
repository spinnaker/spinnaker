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

import com.amazonaws.services.ec2.model.Subnet
import com.amazonaws.services.ec2.model.Tag
import com.netflix.spinnaker.mort.aws.cache.Keys
import com.netflix.spinnaker.mort.model.CacheService
import spock.lang.Specification
import spock.lang.Subject

class AmazonSubnetProviderSpec extends Specification {

    @Subject
    AmazonSubnetProvider provider = new AmazonSubnetProvider(cacheService: Mock(CacheService))

    void "should retrieve all subnets"() {
        when:
        def result = provider.getAll()

        then:
        result == [
                new AmazonSubnet(
                    id: 'subnet-00000001',
                    state: 'available',
                    vpcId: 'vpc-1',
                    cidrBlock: '10',
                    availableIpAddressCount: 1,
                    account: 'test',
                    region: 'us-east-1',
                    availabilityZone: 'us-east-1a',
                    purpose: 'internal',
                    target: 'EC2',
                ),
                new AmazonSubnet(
                    id: 'subnet-00000002',
                    state: 'available',
                    vpcId: 'vpc-1',
                    cidrBlock: '11',
                    availableIpAddressCount: 2,
                    account: 'prod',
                    region: 'us-west-1',
                    availabilityZone: 'us-west-1a',
                    purpose: 'external',
                    target: 'EC2',
                )
        ] as Set

        and:
        1 * provider.cacheService.keysByType(Keys.Namespace.SUBNETS) >> [
                'subnets:subnet-00000001:test:us-east-1',
                'subnets:subnet-00000002:prod:us-west-1'
        ]
        1 * provider.cacheService.retrieve('subnets:subnet-00000001:test:us-east-1', Subnet) >> new Subnet(
                subnetId: 'subnet-00000001',
                state: 'available',
                vpcId: 'vpc-1',
                cidrBlock: '10',
                availableIpAddressCount: 1,
                availabilityZone: 'us-east-1a',
                tags: [new Tag(key: 'immutable_metadata', value: '{"purpose": "internal", "target": "EC2"}')]
        )
        1 * provider.cacheService.retrieve('subnets:subnet-00000002:prod:us-west-1', Subnet) >> new Subnet(
                subnetId: 'subnet-00000002',
                state: 'available',
                vpcId: 'vpc-1',
                cidrBlock: '11',
                availableIpAddressCount: 2,
                availabilityZone: 'us-west-1a',
                tags: [new Tag(key: 'immutable_metadata', value: '{"purpose": "external", "target": "EC2"}')]
        )
        0 * _
    }

    void "should parse purpose out of name tag"() {
        when:
        def result = provider.getAll()

        then:
        result == [
            new AmazonSubnet(
                id: 'subnet-00000001',
                state: 'available',
                vpcId: 'vpc-1',
                cidrBlock: '10',
                availableIpAddressCount: 1,
                account: 'test',
                region: 'us-east-1',
                availabilityZone: 'us-east-1a',
                purpose: 'external (vpc0)',
                target: 'EC2',
            )
        ] as Set

        and:
        1 * provider.cacheService.keysByType(Keys.Namespace.SUBNETS) >> [
            'subnets:subnet-00000001:test:us-east-1'
        ]
        1 * provider.cacheService.retrieve('subnets:subnet-00000001:test:us-east-1', Subnet) >> new Subnet(
            subnetId: 'subnet-00000001',
            state: 'available',
            vpcId: 'vpc-1',
            cidrBlock: '10',
            availableIpAddressCount: 1,
            availabilityZone: 'us-east-1a',
            tags: [
                new Tag(key: 'name', value: 'vpc0.external.us-east-1d'),
                new Tag(key: 'immutable_metadata', value: '{"target": "EC2"}')
            ]
        )
        0 * _
    }
}
