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
import com.amazonaws.services.ec2.model.ReservedInstancesOffering
import com.netflix.spinnaker.mort.aws.model.AmazonInstanceType
import com.netflix.spinnaker.mort.model.CacheService
import com.netflix.spinnaker.mort.model.CachingAgent
import groovy.transform.Immutable
import groovy.util.logging.Slf4j
import rx.Subscriber

@Immutable(knownImmutables = ["ec2", "cacheService"])
@Slf4j
class AmazonInstanceTypeCachingAgent implements CachingAgent {
  final String account
  final String region
  final AmazonEC2 ec2
  final CacheService cacheService

  @Override
  String getDescription() {
      "[$account:$region:itp]"
  }

  @Override
  int getIntervalMultiplier() {
      100
  }

  @Override
  void call() {
      log.info "$description - Caching..."

      def observable = rx.Observable.create(new rx.Observable.OnSubscribe<ReservedInstancesOffering>() {
          @Override
          void call(Subscriber<? super ReservedInstancesOffering> subscriber) {
              def request = new DescribeReservedInstancesOfferingsRequest()
              while (true) {
                  def result = ec2.describeReservedInstancesOfferings(request)
                  result.reservedInstancesOfferings.each {
                      subscriber.onNext(it)
                  }
                  if (result.nextToken) {
                      request.withNextToken(result.nextToken)
                  } else {
                      subscriber.onCompleted()
                      break
                  }
              }
          }
      })

      observable.subscribe {
          cacheService.put(Keys.getInstanceTypeKey(it.reservedInstancesOfferingId, region, account),
                  new AmazonInstanceType(
                          account: account,
                          region: region,
                          name: it.instanceType,
                          availabilityZone: it.availabilityZone,
                          productDescription: it.productDescription,
                          durationSeconds: it.duration
                  )
          )
      }
  }
}
