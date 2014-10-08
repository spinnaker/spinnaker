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
import com.amazonaws.services.ec2.model.DescribeSubnetsResult
import com.amazonaws.services.ec2.model.Subnet
import com.netflix.spinnaker.mort.model.CacheService
import spock.lang.Specification
import spock.lang.Subject

class AmazonSubnetCachingAgentSpec extends Specification {

  CacheService cacheService = Mock(CacheService)
  AmazonEC2 ec2 = Mock(AmazonEC2)

  @Subject
  AmazonSubnetCachingAgent agent = new AmazonSubnetCachingAgent(
          cacheService: cacheService,
          ec2: ec2,
          region: 'us-east-1',
          account: 'test')

  void "should add on initial run"() {
    when:
    agent.call()

    then:
    1 * ec2.describeSubnets() >> new DescribeSubnetsResult(subnets: [
            new Subnet(subnetId: 'subnetId1'),
            new Subnet(subnetId: 'subnetId2')
    ])
    1 * cacheService.put('subnets:subnetId1:test:us-east-1', new Subnet(subnetId: 'subnetId1'))
    1 * cacheService.put('subnets:subnetId2:test:us-east-1', new Subnet(subnetId: 'subnetId2'))
    0 * _
  }

  void "should recache all on second run"() {
      when:
      agent.call()

      then:
      1 * ec2.describeSubnets() >> new DescribeSubnetsResult(subnets: [
              new Subnet(subnetId: 'subnetId1')
      ])
      1 * cacheService.put('subnets:subnetId1:test:us-east-1', new Subnet(subnetId: 'subnetId1'))
      0 * _

      when:
      agent.call()

      then:
      1 * ec2.describeSubnets() >> new DescribeSubnetsResult(subnets: [
              new Subnet(subnetId: 'subnetId1'),
              new Subnet(subnetId: 'subnetId2')
      ])
      1 * cacheService.put('subnets:subnetId1:test:us-east-1', new Subnet(subnetId: 'subnetId1'))
      1 * cacheService.put('subnets:subnetId2:test:us-east-1', new Subnet(subnetId: 'subnetId2'))
      0 * _
  }

}
