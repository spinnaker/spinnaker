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

package com.netflix.spinnaker.oort.data.aws

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.oort.config.OortDefaults
import com.netflix.spinnaker.oort.data.aws.computers.DefaultApplicationLoader
import com.netflix.spinnaker.oort.model.aws.AmazonApplication
import com.netflix.spinnaker.oort.security.NamedAccountProvider
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import org.apache.directmemory.DirectMemory
import org.apache.directmemory.cache.CacheService
import org.springframework.context.ApplicationContext
import spock.lang.Shared
import spock.lang.Specification

import java.lang.Void as Should
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DefaultApplicationLoaderSpec extends Specification {

  @Shared
  DefaultApplicationLoader loader

  def setup() {
    loader = new DefaultApplicationLoader()
  }

  Should "call to aws for a list of asgs and derive applications from frigga-naming for all Amazon accounts and store them in the indexed cache, then fire a data load event"() {
    setup:
    def accountName = "account"
    def appName = "oort"
    def asgName = "$appName-stack-v000"
    def clientProvider = Mock(AmazonClientProvider)
    def autoScaling = Mock(AmazonAutoScaling)
    def accountProvider = Mock(NamedAccountProvider)
    def account = new AmazonNamedAccount(Mock(AWSCredentialsProvider), "test", null, null, null, null, ["us-west-1"])
    def mockCtx = Mock(ApplicationContext)
    def cache = Mock(CacheService)
    def defaults = new OortDefaults()
    loader.oortDefaults = defaults
    loader.applicationCacheService = cache
    loader.executorService = Executors.newSingleThreadExecutor()
    loader.applicationContext = mockCtx
    loader.amazonClientProvider = clientProvider
    loader.namedAccountProvider = accountProvider

    when:
    loader.load()
    loader.executorService.shutdown()
    loader.executorService.awaitTermination(5, TimeUnit.SECONDS)

    then:
    1 * accountProvider.getAccountNames() >> [accountName]
    1 * accountProvider.get(accountName) >> account
    1 * clientProvider.getAutoScaling(_, _) >> autoScaling
    1 * autoScaling.describeAutoScalingGroups() >> {
      def mock = Mock(AutoScalingGroup)
      mock.getAutoScalingGroupName() >> asgName
      new DescribeAutoScalingGroupsResult().withAutoScalingGroups(mock)
    }
    1 * cache.put(_, _, _)
    1 * mockCtx.publishEvent(_) >> { AmazonDataLoadEvent event ->
      assert event.autoScalingGroup.autoScalingGroupName == asgName
    }
  }

  Should "expire cluster names after expiration time"() {
    setup:
    def cache = new DirectMemory<String, AmazonApplication>().setNumberOfBuffers(1).setSize(50000000).setInitialCapacity(1000000).setConcurrencyLevel(4).setDisposalTime(600000).newCacheService()
    if (!cache.put("foo", new AmazonApplication())) {
      assert false
    }
    loader.applicationCacheService = cache
    loader.oortDefaults = new OortDefaults(applicationExpiration: 120000, clusterExpiration: 100)
    def account = Mock(AmazonNamedAccount)
    def asg1 = Mock(AutoScalingGroup)
    def asg2 = Mock(AutoScalingGroup)
    def asg3 = Mock(AutoScalingGroup)

    when:
    loader.appCreator(account, "region", asg1)

    then:
    1 * asg1.getAutoScalingGroupName() >> "my-stack-v000"
    _ * account.getName() >> "test"
    cache.retrieve("my").clusterNames.values()?.flatten() == ["my-stack"]

    when:
    Thread.start { sleep 300 }?.join()
    loader.appCreator(account, "region", asg2)
    loader.appCreator(account, "region", asg3)

    then:
    _ * account.getName() >> "prod"
    1 * asg2.getAutoScalingGroupName() >> "my-foo-v000"
    1 * asg3.getAutoScalingGroupName() >> "my-bar-v000"
    !cache.retrieve("my").clusterNames.containsKey("test")
    cache.retrieve("my").clusterNames.values()?.flatten() == ["my-bar", "my-foo"]
  }
}
