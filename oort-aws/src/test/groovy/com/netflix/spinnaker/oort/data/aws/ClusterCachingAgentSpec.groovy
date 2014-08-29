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
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.oort.data.aws.cachers.AbstractInfrastructureCachingAgent
import com.netflix.spinnaker.oort.data.aws.cachers.ClusterCachingAgent
import com.netflix.spinnaker.oort.model.aws.AmazonApplication
import com.netflix.spinnaker.oort.model.aws.AmazonCluster
import com.netflix.spinnaker.oort.model.aws.AmazonServerGroup

class ClusterCachingAgentSpec extends AbstractCachingAgentSpec {
  @Override
  AbstractInfrastructureCachingAgent getCachingAgent() {
    def acct = Mock(NetflixAmazonCredentials)
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
    def serverGroupKey1 = Keys.getServerGroupKey(asgName1, ACCOUNT, REGION)
    def serverGroupKey2 = Keys.getServerGroupKey(asgName2, ACCOUNT, REGION)


    when:
    "should fire the newAsg event when new asgs are found"
    agent.load()

    then:
    1 * amazonAutoScaling.describeAutoScalingGroups() >> result
    with(cacheService) {
      2 * retrieve(appKey, AmazonApplication) >> app
      2 * put(appKey, app)
      2 * retrieve(clusterKey, AmazonCluster) >> cluster
      2 * put(clusterKey, cluster)
      1 * put(serverGroupKey1, _)
      1 * put(serverGroupKey2, _)
    }

    when:
    "one asg goes missing, should fire the missingAsg event"
    agent.load()

    then:
    1 * amazonAutoScaling.describeAutoScalingGroups() >> result.withAutoScalingGroups([asg2])
    with(cacheService) {
      1 * free(serverGroupKey1)
      1 * keysByType(Keys.Namespace.SERVER_GROUPS) >> [serverGroupKey2]
      0 * free(clusterKey)
    }

    when:
    "nothing has changed, shouldn't do anything"
    agent.load()

    then:
    1 * amazonAutoScaling.describeAutoScalingGroups() >> result
    0 * cacheService.retrieve(_, _)
  }

  void "removes load balancer from application only when no more server groups in application use load balancer"() {
    setup:
    def asgName1 = "kato-main-v000"
    def asg1 = new AutoScalingGroup().withAutoScalingGroupName(asgName1)
    def asgName2 = "kato-main-v001"
    def asg2 = new AutoScalingGroup().withAutoScalingGroupName(asgName2)
    def asgName3 = "kato-main-v002"
    def asg3 = new AutoScalingGroup().withAutoScalingGroupName(asgName3)
    def loadBalancerName = 'kato-main'
    asg1.loadBalancerNames = [loadBalancerName]
    asg2.loadBalancerNames = [loadBalancerName]
    def result = new DescribeAutoScalingGroupsResult().withAutoScalingGroups([asg1, asg2, asg3])
    def app = new AmazonApplication(name: 'kato')
    def serverGroupKey1 = Keys.getServerGroupKey(asgName1, ACCOUNT, REGION)
    def serverGroupKey2 = Keys.getServerGroupKey(asgName2, ACCOUNT, REGION)
    def serverGroupKey3 = Keys.getServerGroupKey(asgName3, ACCOUNT, REGION)
    def loadBalancerServerGroupKey1 = Keys.getLoadBalancerServerGroupKey(loadBalancerName, ACCOUNT, asgName1, REGION)
    def loadBalancerServerGroupKey2 = Keys.getLoadBalancerServerGroupKey(loadBalancerName, ACCOUNT, asgName2, REGION)
    def applicationLoadBalancerKey = Keys.getApplicationLoadBalancerKey(app.name, asg1.loadBalancerNames[0], ACCOUNT, REGION)

    def appKey = Keys.getApplicationKey('kato')
    def clusterKey = Keys.getClusterKey('kato-main', 'kato', ACCOUNT)
    def cluster = new AmazonCluster(name: 'kato-main', accountName: ACCOUNT)


    when:
    "should cache application and load balancer server group relationships"
    agent.load()

    then:
    1 * amazonAutoScaling.describeAutoScalingGroups() >> result
    with(cacheService) {
      3 * retrieve(appKey, AmazonApplication) >> app
      3 * put(appKey, app)
      3 * retrieve(clusterKey, AmazonCluster) >> cluster
      3 * put(clusterKey, cluster)
      1 * put(serverGroupKey1, _)
      1 * put(serverGroupKey2, _)
      1 * put(serverGroupKey3, _)
    }

    when:
    "one asg goes missing, load balancer should still be attached to application"
    agent.load()

    then:
    1 * amazonAutoScaling.describeAutoScalingGroups() >> result.withAutoScalingGroups([asg2, asg3])
    with(cacheService) {
      1 * free(serverGroupKey1)
      1 * keysByType(Keys.Namespace.LOAD_BALANCER_SERVER_GROUPS) >> [loadBalancerServerGroupKey1, loadBalancerServerGroupKey2]
      1 * keysByType(Keys.Namespace.SERVER_GROUPS) >> [serverGroupKey1, serverGroupKey2, serverGroupKey3]
      1 * retrieve(serverGroupKey1, AmazonServerGroup) >> new AmazonServerGroup(asg: asg1)
      1 * free(loadBalancerServerGroupKey1)
      0 * free(applicationLoadBalancerKey)
    }

    when:
    "second asg goes missing, load balancer should be removed from application"
    agent.load()

    then:
    1 * amazonAutoScaling.describeAutoScalingGroups() >> result.withAutoScalingGroups([asg3])
    with(cacheService) {
      1 * free(serverGroupKey2)
      1 * keysByType(Keys.Namespace.LOAD_BALANCER_SERVER_GROUPS) >> [loadBalancerServerGroupKey2]
      1 * keysByType(Keys.Namespace.SERVER_GROUPS) >> [serverGroupKey2, serverGroupKey3]
      1 * retrieve(serverGroupKey2, AmazonServerGroup) >> new AmazonServerGroup(asg: asg2)
      1 * free(loadBalancerServerGroupKey2)
      1 * free(applicationLoadBalancerKey)
      0 * cacheService.retrieve(_, _)
    }

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
    def account = Mock(NetflixAmazonCredentials)
    account.getName() >> ACCOUNT
    def asgName = "kato-main-v000"
    def launchConfigName = "kato-main-v000-123456"
    def asg = new AutoScalingGroup().withAutoScalingGroupName(asgName).withLaunchConfigurationName(launchConfigName)
    def names = Names.parseName(asgName)
    [account: ACCOUNT, accountObj: account, asgName: asgName, asg: asg, names: names, launchConfigName: launchConfigName, region: REGION]
  }

}
