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

package com.netflix.spinnaker.oort.data.aws.computers

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.amazoncomponents.security.AmazonCredentials
import com.netflix.frigga.Names
import com.netflix.spinnaker.oort.config.OortDefaults
import com.netflix.spinnaker.oort.data.aws.AmazonDataLoadEvent
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.data.aws.Loader
import com.netflix.spinnaker.oort.data.aws.MultiAccountCachingSupport
import com.netflix.spinnaker.oort.model.aws.AmazonApplication
import com.netflix.spinnaker.oort.security.NamedAccountProvider
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import groovy.transform.CompileStatic
import org.apache.directmemory.cache.CacheService
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

import java.util.concurrent.ExecutorService

@CompileStatic
@Component
class DefaultApplicationLoader extends MultiAccountCachingSupport implements Loader {
  private static final Logger log = Logger.getLogger(this)
  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  NamedAccountProvider namedAccountProvider

  @Autowired
  ApplicationContext applicationContext

  @Autowired
  CacheService<String, Object> cacheService

  @Autowired
  OortDefaults oortDefaults

  @Autowired
  ExecutorService executorService

  @Async("taskScheduler")
  @Scheduled(fixedRateString = '${cacheRefreshMs}')
  void load() {
    log.info "Beginning caching Amazon applications..."
    def accounts = (Collection<AmazonNamedAccount>) namedAccountProvider.accountNames.collectMany({ String name ->
      def namedAccount = namedAccountProvider.get(name)
      (namedAccount.type == AmazonCredentials) ? [namedAccount] : []
    } as Closure<Collection<AmazonNamedAccount>>)

    def callables = []
    for (account in accounts) {
      for (region in account.regions) {
        callables << loadData.curry(account, region)
      }
    }

    executorService.invokeAll(callables)
  }

  private final Closure loadData = { AmazonNamedAccount account, String region ->
    try {
      def autoScaling = amazonClientProvider.getAutoScaling(account.credentials, region)
      def result = autoScaling.describeAutoScalingGroups()
      List<AutoScalingGroup> autoScalingGroups = []
      while (true) {
        autoScalingGroups.addAll result.autoScalingGroups
        if (result.nextToken) {
          result = autoScaling.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest().withNextToken(result.nextToken))
        } else {
          break
        }
      }

      def serverGroupNames = []
      for (asg in autoScalingGroups) {
        executorService.submit(appCreator.curry(account, region, asg))
        executorService.submit(eventPublisher.curry(region, account, asg))
        serverGroupNames << asg.autoScalingGroupName
      }

      def cacheKeys = cacheService.map.keySet()
      def cachedServerGroupKeys = cacheKeys.findAll { it.startsWith("serverGroups") && it.contains(":$region:") && it.contains(":${account.name}:")}.collect { it.split(':')[-1]}
      def vanishedGroups = cachedServerGroupKeys - serverGroupNames
      for (String serverGroupName in vanishedGroups) {
        def names = Names.parseName(serverGroupName)
        def ptr = cacheService.getPointer(Keys.getServerGroupKey(names.group, account.name, region))
        if (ptr) {
          log.info "Cleaning up $names.group."
          cacheService.free(ptr)
        }
      }
    } catch (e) {
      log.error "THERE WAS AN ERROR WHILE LOADING APPLICATIONS!!", e
    }
  }

  private final Closure eventPublisher = { String region, AmazonNamedAccount account, AutoScalingGroup asg ->
    applicationContext.publishEvent(new AmazonDataLoadEvent(this, region, account, asg))
  }

  protected final Closure appCreator = { AmazonNamedAccount account, String region, AutoScalingGroup asg ->
    AmazonApplication application
    try {
      def names = Names.parseName(asg.autoScalingGroupName)
      def appName = names.app.toLowerCase()
      if (!appName) return
      application = (AmazonApplication) cacheService.retrieve(Keys.getApplicationKey(appName)) ?: new AmazonApplication(name: appName)
      if (!cacheService.put(Keys.getApplicationKey(application.name), application)) {
        log.info("Not enough space to save application!!")
      }
    } catch (e) {
      // these are cache misses
    }
  }
}
