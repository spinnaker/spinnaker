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
import com.netflix.spinnaker.oort.model.aws.AmazonCluster
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount

class ClusterCachingAgentSpec extends AbstractCachingAgentSpec {
  @Override
  AbstractInfrastructureCachingAgent getCachingAgent() {
    def acct = Mock(AmazonNamedAccount)
    acct.getName() >> ACCOUNT
    new ClusterCachingAgent(acct, REGION)
  }

  void "load new asgs, remove ones that have disappeared, and do nothing when nothing has changed"() {
    setup:
    def asgName1 = "kato-main-v000"
    def asg1 = new AutoScalingGroup().withAutoScalingGroupName(asgName1)
    def asgName2 = "kato-main-v001"
    def asg2 = new AutoScalingGroup().withAutoScalingGroupName(asgName2)
    def result = new DescribeAutoScalingGroupsResult().withAutoScalingGroups(asg1, asg2)
    def app = new AmazonApplication(name: 'kato')
    def appKey = Keys.getApplicationKey('kato')
    def clusterKey = Keys.getClusterKey('kato-main', 'kato', ACCOUNT)
    def cluster = new AmazonCluster(name: 'kato-main', accountName: ACCOUNT)


    when:
    "should fire the newAsg event when new asgs are found"
    agent.load()

    then:
    1 * amazonAutoScaling.describeAutoScalingGroups() >> result
    2 * cacheService.retrieve(appKey, AmazonApplication) >> app
    2 * cacheService.put(appKey, app)
    2 * cacheService.retrieve(clusterKey, AmazonCluster) >> cluster
    2 * cacheService.put(clusterKey, cluster)
    1 * cacheService.put(Keys.getServerGroupKey('kato-main-v000', ACCOUNT, REGION), _)
    1 * cacheService.put(Keys.getServerGroupKey('kato-main-v001', ACCOUNT, REGION), _)

    when:
    "one asg goes missing, should fire the missingAsg event"
    agent.load()

    then:
    1 * amazonAutoScaling.describeAutoScalingGroups() >> result.withAutoScalingGroups([asg1])
    1 * cacheService.free(Keys.getServerGroupKey(asg2.autoScalingGroupName, ACCOUNT, REGION))
    1 * cacheService.keysByType(Keys.Namespace.SERVER_GROUPS) >> [Keys.getServerGroupKey(asg1.autoScalingGroupName, ACCOUNT, REGION)]
    0 * cacheService.free(clusterKey)

    when:
    "nothing has changed, shouldn't do anything"
    agent.load()

    then:
    1 * amazonAutoScaling.describeAutoScalingGroups() >> result
    0 * cacheService.retrieve(_, _)
  }

  void "store an application in cache"() {
    setup:
    def mocks = getCommonMocks()

    when:
    ((ClusterCachingAgent)agent).loadApp(mocks.names)

    then:
    1 * cacheService.put(Keys.getApplicationKey(mocks.names.app), _)
  }

  void "should store an asg in cache"() {
    setup:
    def mocks = getCommonMocks()

    when:
    ((ClusterCachingAgent)agent).loadServerGroups(mocks.accountObj, mocks.asg, mocks.names, mocks.region)

    then:
    1 * cacheService.put(Keys.getServerGroupKey(mocks.asgName, mocks.account, mocks.region), _)
  }

  void "should store cluster in cache"() {
    setup:
    def mocks = getCommonMocks()

    when:
    ((ClusterCachingAgent)agent).loadCluster(mocks.accountObj, mocks.asg, mocks.names, mocks.region)

    then:
    1 * cacheService.put(Keys.getClusterKey(mocks.names.cluster, mocks.names.app, mocks.account), _)
  }

  void "should remove server group from cache and clean-up cluster if no more server groups"() {
    setup:
    def mocks = getCommonMocks()
    def serverGroupKey = Keys.getServerGroupKey(mocks.names.group, mocks.account, mocks.region)
    def clusterKey = Keys.getClusterKey(mocks.names.cluster, mocks.names.app, mocks.account)

    when:
    ((ClusterCachingAgent)agent).removeServerGroup(mocks.accountObj, mocks.names.group, mocks.region)

    then:
    1 * cacheService.free(serverGroupKey)
    1 * cacheService.keysByType(Keys.Namespace.SERVER_GROUPS) >> []
    1 * cacheService.free(clusterKey)
  }

  private def getCommonMocks() {
    def account = Mock(AmazonNamedAccount)
    account.getName() >> ACCOUNT
    def asgName = "kato-main-v000"
    def launchConfigName = "kato-main-v000-123456"
    def asg = new AutoScalingGroup().withAutoScalingGroupName(asgName).withLaunchConfigurationName(launchConfigName)
    def names = Names.parseName(asgName)
    [account: ACCOUNT, accountObj: account, asgName: asgName, asg: asg, names: names, launchConfigName: launchConfigName, region: REGION]
  }

}
