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

package com.netflix.spinnaker.oort.data.aws.cachers

import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.ec2.model.Instance
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.data.aws.Loader
import com.netflix.spinnaker.oort.data.aws.MultiAccountCachingSupport
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import org.apache.directmemory.cache.CacheService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class InstanceCacher extends MultiAccountCachingSupport implements Loader {
  final ExecutorService executorService = Executors.newFixedThreadPool(50)

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  CacheService<String, Object> cacheService

  @Async("taskScheduler")
  @Scheduled(fixedRate = 30000l)
  void load() {
    invokeMultiAccountMultiRegionClosure loadInstancesCallable
  }

  final Closure loadInstancesCallable = { AmazonNamedAccount account, String region ->
    def ec2 = amazonClientProvider.getAmazonEC2(account.credentials, region)
    def result = ec2.describeInstances()
    List<Instance> instances = []
    while (true) {
      instances.addAll result.reservations*.instances?.flatten()
      if (result.nextToken) {
        result = ec2.describeInstances(new DescribeInstancesRequest().withNextToken(result.nextToken))
      } else {
        break
      }
    }

    for (instance in instances) {
      cacheService.put(Keys.getInstanceKey(instance.instanceId, region), instance)
    }
  }

}
