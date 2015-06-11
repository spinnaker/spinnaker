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
import com.amazonaws.services.ec2.model.DescribeReservedInstancesOfferingsRequest
import com.amazonaws.services.ec2.model.DescribeReservedInstancesOfferingsResult
import com.amazonaws.services.ec2.model.ReservedInstancesOffering
import com.netflix.spinnaker.mort.aws.model.AmazonInstanceType
import com.netflix.spinnaker.mort.model.CacheService
import spock.lang.Specification
import spock.lang.Subject

class AmazonInstanceTypeCachingAgentSpec extends Specification {

  CacheService cacheService = Mock(CacheService)
  AmazonEC2 ec2 = Mock(AmazonEC2)

  @Subject
  AmazonInstanceTypeCachingAgent agent = new AmazonInstanceTypeCachingAgent(
          cacheService: cacheService,
          ec2: ec2,
          region: 'us-east-1',
          account: 'test')

  void "should add to cache"() {
    when:
    agent.call()

    then:
    1 * ec2.describeReservedInstancesOfferings(new DescribeReservedInstancesOfferingsRequest()) >> new DescribeReservedInstancesOfferingsResult(
        reservedInstancesOfferings: [
            new ReservedInstancesOffering(reservedInstancesOfferingId: '1', instanceType: 'm1')
        ]
    )
    1 * cacheService.put('instanceTypes:1:test:us-east-1', new AmazonInstanceType(name: 'm1', account: 'test', region: 'us-east-1'))
    0 * _
  }

  void "should add all from paged results"() {
      when:
      agent.call()

      then:
      1 * ec2.describeReservedInstancesOfferings(new DescribeReservedInstancesOfferingsRequest()) >> new DescribeReservedInstancesOfferingsResult(
              reservedInstancesOfferings: [
                      new ReservedInstancesOffering(reservedInstancesOfferingId: '1', instanceType: 'm1'),
                      new ReservedInstancesOffering(reservedInstancesOfferingId: '2', instanceType: 'm2')
              ],
              nextToken: 'moar'
      )
      1 * ec2.describeReservedInstancesOfferings(new DescribeReservedInstancesOfferingsRequest(nextToken: 'moar')) >> new DescribeReservedInstancesOfferingsResult(
              reservedInstancesOfferings: [
                      new ReservedInstancesOffering(reservedInstancesOfferingId: '3', instanceType: 'm3')
              ]
      )
      1 * cacheService.put('instanceTypes:1:test:us-east-1', new AmazonInstanceType(name: 'm1', account: 'test', region: 'us-east-1'))
      1 * cacheService.put('instanceTypes:2:test:us-east-1', new AmazonInstanceType(name: 'm2', account: 'test', region: 'us-east-1'))
      1 * cacheService.put('instanceTypes:3:test:us-east-1', new AmazonInstanceType(name: 'm3', account: 'test', region: 'us-east-1'))
      0 * _
  }

}
