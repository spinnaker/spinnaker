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

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult
import com.netflix.frigga.Names
import com.netflix.spinnaker.oort.data.aws.cachers.AbstractInfrastructureCachingAgent
import com.netflix.spinnaker.oort.data.aws.cachers.ClusterCachingAgent
import com.netflix.spinnaker.oort.model.aws.AmazonApplication
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import reactor.event.Event

class ClusterCachingAgentSpec extends AbstractCachingAgentSpec {
  @Override
  AbstractInfrastructureCachingAgent getCachingAgent() {
    new ClusterCachingAgent(Mock(AmazonNamedAccount), "us-east-1")
  }

  void "load new asgs, remove ones that have disappeared, and do nothing when nothing has changed"() {
    setup:
    def asgName1 = "kato-main-v000"
    def asg1 = new AutoScalingGroup().withAutoScalingGroupName(asgName1)
    def asgName2 = "kato-main-v001"
    def asg2 = new AutoScalingGroup().withAutoScalingGroupName(asgName2)
    def result = new DescribeAutoScalingGroupsResult().withAutoScalingGroups(asg1, asg2)

    when:
    "should fire the newAsg event when new asgs are found"
    agent.load()

    then:
    1 * amazonAutoScaling.describeAutoScalingGroups() >> result
    2 * reactor.notify("newAsg", _)

    when:
    "one asg goes missing, should fire the missingAsg event"
    agent.load()

    then:
    1 * amazonAutoScaling.describeAutoScalingGroups() >> result.withAutoScalingGroups([asg1])
    0 * reactor.notify("newAsg", _)
    1 * reactor.notify("missingAsg", _) >> { name, event ->
      assert event.data.asgName == asgName2
    }

    when:
    "nothing has changed, shouldn't do anything"
    agent.load()

    then:
    1 * amazonAutoScaling.describeAutoScalingGroups() >> result
    0 * reactor.notify(_, _)
  }

  void "store an application in cache"() {
    setup:
    def mocks = getCommonMocks()

    when:
    ((ClusterCachingAgent)agent).loadApp(mocks.event)

    then:
    1 * cacheService.put(Keys.getApplicationKey(mocks.names.app), _)
  }

  void "should store an asg in cache"() {
    setup:
    def mocks = getCommonMocks()

    when:
    ((ClusterCachingAgent)agent).loadServerGroups(mocks.event)

    then:
    1 * cacheService.put(Keys.getServerGroupKey(mocks.asgName, mocks.account, mocks.region), _)
  }

  void "should store cluster in cache"() {
    setup:
    def mocks = getCommonMocks()

    when:
    ((ClusterCachingAgent)agent).loadCluster(mocks.event)

    then:
    1 * cacheService.put(Keys.getClusterKey(mocks.names.cluster, mocks.names.app, mocks.account), _)
  }

  void "should remove server group from cache"() {
    setup:
    def mocks = getCommonMocks()

    when:
    ((ClusterCachingAgent)agent).removeServerGroup(Event.wrap(new ClusterCachingAgent.RemoveServerGroupNotification(mocks.accountObj, mocks.names.group, mocks.region)))

    then:
    1 * cacheService.free(Keys.getServerGroupKey(mocks.names.group, mocks.account, mocks.region))
  }

  private def getCommonMocks() {
    def account = Mock(AmazonNamedAccount)
    def accountName = "test"
    account.getName() >> accountName
    def asgName = "kato-main-v000"
    def launchConfigName = "kato-main-v000-123456"
    def asg = new AutoScalingGroup().withAutoScalingGroupName(asgName).withLaunchConfigurationName(launchConfigName)
    def names = Names.parseName(asgName)
    def region = "us-east-1"
    def event = Event.wrap(new ClusterCachingAgent.FriggaWrappedAutoScalingGroup(account, asg, names, region))
    [account: accountName, accountObj: account, asgname: asgName, names: names, launchConfigName: launchConfigName, region: region, event: event]
  }

}
