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

import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsRequest
import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.data.aws.Loader
import com.netflix.spinnaker.oort.data.aws.MultiAccountCachingSupport
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import org.apache.directmemory.cache.CacheService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Component
class LaunchConfigCacher extends MultiAccountCachingSupport implements Loader {
  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  CacheService<String, Object> cacheService

  @Autowired
  @Qualifier("loaderExecutorService")
  ExecutorService executorService

  @Async("taskScheduler")
  @Scheduled(fixedRateString = '${cacheRefreshMs}')
  void load() {
    invokeMultiAccountMultiRegionClosure loadLaunchConfigsCallable
  }

  final Closure loadLaunchConfigsCallable = { AmazonNamedAccount account, String region ->

    def autoScaling = amazonClientProvider.getAutoScaling(account.credentials, region)
    def result = autoScaling.describeLaunchConfigurations()
    List<LaunchConfiguration> launchConfigs = []
    while (true) {
      launchConfigs.addAll result.launchConfigurations
      if (result.nextToken) {
        result = autoScaling.describeLaunchConfigurations(new DescribeLaunchConfigurationsRequest().withNextToken(result.nextToken))
      } else {
        break
      }
    }
    for (launchConfig in launchConfigs) {
      cacheService.put(Keys.getLaunchConfigKey(launchConfig.launchConfigurationName, region), launchConfig)
    }
  }
}
