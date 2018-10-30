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

import com.amazonaws.services.ec2.model.Subnet
import com.amazonaws.services.ec2.model.Tag
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.awsobjectmapper.AmazonObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.model.AmazonSubnet
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider
import spock.lang.Specification
import spock.lang.Subject

class AmazonSubnetProviderSpec extends Specification {

  Cache cache = Mock(Cache)
  ObjectMapper mapper = new AmazonObjectMapper()

  @Subject
  AmazonSubnetProvider provider = new AmazonSubnetProvider(cache, mapper)

  void "should retrieve all subnets"() {
    when:
    def result = provider.getAll()

    then:
    result == [
      new AmazonSubnet(
        type: 'aws',
        id: 'subnet-00000001',
        state: 'available',
        vpcId: 'vpc-1',
        cidrBlock: '10',
        availableIpAddressCount: 1,
        account: 'test',
        accountId: '1',
        region: 'us-east-1',
        availabilityZone: 'us-east-1a',
        purpose: 'internal',
        target: 'EC2',
        deprecated: false,
      ),
      new AmazonSubnet(
        type: 'aws',
        id: 'subnet-00000002',
        state: 'available',
        vpcId: 'vpc-1',
        cidrBlock: '11',
        availableIpAddressCount: 2,
        account: 'prod',
        accountId: '2',
        region: 'us-west-1',
        availabilityZone: 'us-west-1a',
        purpose: 'external',
        target: 'EC2',
        deprecated: false,
      )
    ] as Set

    and:
    1 * cache.filterIdentifiers(Keys.Namespace.SUBNETS.ns, "aws:$Keys.Namespace.SUBNETS.ns:*:*:*")
    1 * cache.getAll(Keys.Namespace.SUBNETS.ns, _, _ as CacheFilter) >> [
      snData('test', '1', 'us-east-1',
        new Subnet(
          subnetId: 'subnet-00000001',
          state: 'available',
          vpcId: 'vpc-1',
          cidrBlock: '10',
          availableIpAddressCount: 1,
          availabilityZone: 'us-east-1a',
          tags: [new Tag(key: 'immutable_metadata', value: '{"purpose": "internal", "target": "EC2"}')]
        )),
      snData('prod', '2','us-west-1', new Subnet(
        subnetId: 'subnet-00000002',
        state: 'available',
        vpcId: 'vpc-1',
        cidrBlock: '11',
        availableIpAddressCount: 2,
        availabilityZone: 'us-west-1a',
        tags: [new Tag(key: 'immutable_metadata', value: '{"purpose": "external", "target": "EC2"}')]
      ))]
  }

  void "should parse purpose out of name tag"() {
    when:
    def result = provider.getAll()

    then:
    result == [
      new AmazonSubnet(
        type: 'aws',
        id: 'subnet-00000001',
        state: 'available',
        vpcId: 'vpc-1',
        cidrBlock: '10',
        availableIpAddressCount: 1,
        account: 'test',
        accountId: '1',
        region: 'us-east-1',
        availabilityZone: 'us-east-1a',
        purpose: 'external (vpc0)',
        target: 'EC2',
      )
    ] as Set

    and:
    1 * cache.filterIdentifiers(Keys.Namespace.SUBNETS.ns, "aws:$Keys.Namespace.SUBNETS.ns:*:*:*")
    1 * cache.getAll(Keys.Namespace.SUBNETS.ns, _, _ as CacheFilter) >> [snData('test', '1','us-east-1', new Subnet(
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
    ))]
  }

  void "should parse deprecated out of is_deprecated tag"() {
    when:
    def result = provider.getAll()

    then:
    result == [
        new AmazonSubnet(
            type: 'aws',
            id: 'subnet-00000001',
            state: 'available',
            vpcId: 'vpc-1',
            cidrBlock: '10',
            availableIpAddressCount: 1,
            account: 'test',
            accountId: 1,
            region: 'us-east-1',
            availabilityZone: 'us-east-1a',
            purpose: 'external (vpc0)',
            target: 'EC2',
            deprecated: true,
        )
    ] as Set

    and:
    1 * cache.filterIdentifiers(Keys.Namespace.SUBNETS.ns, "aws:$Keys.Namespace.SUBNETS.ns:*:*:*")
    1 * cache.getAll(Keys.Namespace.SUBNETS.ns, _, _ as CacheFilter) >> [snData('test', '1','us-east-1', new Subnet(
        subnetId: 'subnet-00000001',
        state: 'available',
        vpcId: 'vpc-1',
        cidrBlock: '10',
        availableIpAddressCount: 1,
        availabilityZone: 'us-east-1a',
        tags: [
            new Tag(key: 'name', value: 'vpc0.external.us-east-1d'),
            new Tag(key: 'immutable_metadata', value: '{"target": "EC2"}'),
            new Tag(key: 'is_deprecated', value: 'true')
        ]
    ))]
  }

  CacheData snData(String account, String accountId, String region, Subnet subnet) {
    Map<String, Object> attributes = mapper.convertValue(subnet, AwsInfrastructureProvider.ATTRIBUTES)
    attributes.putIfAbsent("accountId", accountId)
    new DefaultCacheData(Keys.getSubnetKey(subnet.subnetId, region, account),
      attributes,
      [:]
    )
  }

  void "should handle invalid immutable_metadata"() {
    when:
    def result = provider.getAll()

    then:
    result == [
      new AmazonSubnet(
        type: 'aws',
        id: 'subnet-00000001',
        state: 'available',
        vpcId: 'vpc-1',
        cidrBlock: '10',
        availableIpAddressCount: 1,
        account: 'test',
        accountId: '1',
        region: 'us-east-1',
        availabilityZone: 'us-east-1a',
        purpose: 'external (vpc0)',
      )
    ] as Set

    and:
    1 * cache.filterIdentifiers(Keys.Namespace.SUBNETS.ns, "aws:$Keys.Namespace.SUBNETS.ns:*:*:*")
    1 * cache.getAll(Keys.Namespace.SUBNETS.ns, _, _ as CacheFilter) >> [snData('test', '1', 'us-east-1', new Subnet(
      subnetId: 'subnet-00000001',
      state: 'available',
      vpcId: 'vpc-1',
      cidrBlock: '10',
      availableIpAddressCount: 1,
      availabilityZone: 'us-east-1a',
      tags: [
        new Tag(key: 'name', value: 'vpc0.external.us-east-1d'),
        new Tag(key: 'immutable_metadata', value: '{"JSON"="BROKEN","target": "EC2"}')
      ]
    ))]
  }
}
