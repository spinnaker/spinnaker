/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.provider.view

import com.amazonaws.services.ecs.model.LoadBalancer
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsLoadbalancerCacheClient
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsTargetGroupCacheClient
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsLoadBalancerCache
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service
import com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer.EcsLoadBalancer
import com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer.EcsTargetGroup
import com.netflix.spinnaker.clouddriver.ecs.security.ECSCredentialsConfig
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials
import spock.lang.Specification
import spock.lang.Subject

class EcsLoadBalancerProviderSpec extends Specification {
  def ECS_ACCOUNT = 'ecsAccount'
  def AWS_ACCOUNT = 'awsAccount'

  def mockLBCache = Mock(EcsLoadbalancerCacheClient)
  def mockServiceCache = Mock(ServiceCacheClient)
  def mockTargetGroupCache = Mock(EcsTargetGroupCacheClient)
  def accountMapper = Mock(EcsAccountMapper)

  @Subject
  def provider = new EcsLoadBalancerProvider(
    mockLBCache,
    accountMapper,
    mockServiceCache,
    mockTargetGroupCache)

  def setup() {
    accountMapper.fromEcsAccountNameToAwsAccountName(ECS_ACCOUNT) >> AWS_ACCOUNT
  }

  def 'should retrieve an empty list'() {
    when:
    def retrievedList = provider.list()

    then:
    mockLBCache.findAll() >> Collections.emptyList()
    retrievedList.size() == 0
  }

  def 'should retrieve a list containing load balancers'() {
    given:
    def expectedNumberOfLoadbalancers = 2
    def givenList = []
    (1..expectedNumberOfLoadbalancers).forEach() {
      givenList << new EcsLoadBalancerCache(
        account: 'test-account-' + it,
        region: 'us-west-' + it,
        loadBalancerArn: 'arn',
        loadBalancerType: 'always-classic',
        cloudProvider: EcsCloudProvider.ID,
        listeners: [],
        scheme: 'scheme',
        availabilityZones: [],
        ipAddressType: 'ipv4',
        loadBalancerName: 'load-balancer-' + it,
        canonicalHostedZoneId: 'zone-id',
        vpcId: 'vpc-id-' + it,
        dnsname: 'dns-name',
        createdTime: System.currentTimeMillis(),
        subnets: [],
        securityGroups: [],
        targetGroups: ['target-group-' + it],
        serverGroups: []
      )
    }

    when:
    def retrievedList = provider.list()

    then:
    mockLBCache.findAll() >> givenList
    retrievedList.size() == expectedNumberOfLoadbalancers
    retrievedList*.getName().containsAll(givenList*.loadBalancerName)
  }

  def 'should retrieve application load balancers'() {
    given:
    def applicationName = 'myEcsApp'
    def tgArn1 = 'arn:aws:elasticloadbalancing:us-west-1:1234567890:targetgroup/test-tg-1/2136bac'
    def tgArn2 = 'arn:aws:elasticloadbalancing:us-west-1:1234567890:targetgroup/test-tg-2/2136bac'

    // define 2 ports to expose on our task
    LoadBalancer ecsLb1 = new LoadBalancer()
    ecsLb1.setContainerName("container-name")
    ecsLb1.setContainerPort(8080)
    ecsLb1.setTargetGroupArn(tgArn1)

    LoadBalancer ecsLb2 = new LoadBalancer()
    ecsLb2.setContainerName("container-name")
    ecsLb2.setContainerPort(443)
    ecsLb2.setTargetGroupArn(tgArn2)

    // add these to our service
    Service ecsService = new Service()
    ecsService.setServiceName('ecs-test-detail-000v')
    ecsService.setServiceArn('arn:aws:ecs:service/ecs-test-detail-000v')
    ecsService.setLoadBalancers([ecsLb1, ecsLb2])
    ecsService.setAccount('test-account')
    ecsService.setApplicationName(applicationName)

    // mock the cache entries for the TGs and their associated LBs
    EcsTargetGroup ecsTg1 = new EcsTargetGroup()
    ecsTg1.setTargetGroupArn(tgArn1)
    ecsTg1.setTargetGroupName('test-tg-1')

    EcsTargetGroup ecsTg2 = new EcsTargetGroup()
    ecsTg2.setTargetGroupArn(tgArn2)
    ecsTg2.setTargetGroupName('test-tg-2')

    EcsLoadBalancerCache ecsLoadBalancerCache1 = new EcsLoadBalancerCache(
      account: 'test-account',
      region: 'us-west-2',
      loadBalancerArn: 'arn:1',
      loadBalancerType: 'application',
      cloudProvider: EcsCloudProvider.ID,
      listeners: [],
      scheme: 'scheme',
      availabilityZones: [],
      ipAddressType: 'ipv4',
      loadBalancerName: 'load-balancer-name1',
      canonicalHostedZoneId: 'zone-id',
      vpcId: 'vpc-id',
      dnsname: 'dns-name',
      createdTime: System.currentTimeMillis(),
      subnets: [],
      securityGroups: [],
      targetGroups: [ecsTg1.getTargetGroupName()],
      serverGroups: []
    )
    EcsLoadBalancerCache ecsLoadBalancerCache2 = new EcsLoadBalancerCache(
      account: 'test-account',
      region: 'us-west-2',
      loadBalancerArn: 'arn:2',
      loadBalancerType: 'application',
      cloudProvider: EcsCloudProvider.ID,
      listeners: [],
      scheme: 'scheme',
      availabilityZones: [],
      ipAddressType: 'ipv4',
      loadBalancerName: 'load-balancer-name2',
      canonicalHostedZoneId: 'zone-id',
      vpcId: 'vpc-id',
      dnsname: 'dns-name',
      createdTime: System.currentTimeMillis(),
      subnets: [],
      securityGroups: [],
      targetGroups: [ecsTg2.getTargetGroupName()],
      serverGroups: []
    )

    when:
    def loadBalancerList = provider.getApplicationLoadBalancers(applicationName)

    then:
    mockServiceCache.getAll(_) >> Collections.singletonList(ecsService)
    mockTargetGroupCache.getAllKeys() >> ['fake-tg-key-1', 'fake-tg-key-2']
    mockTargetGroupCache.find(_) >> [ecsTg1, ecsTg2]
    mockLBCache.findWithTargetGroups(_) >> [ecsLoadBalancerCache1, ecsLoadBalancerCache2]

    loadBalancerList.size() == 2
    for (EcsLoadBalancer lb : loadBalancerList) {
      lb.targetGroupServices.size() == 1
      lb.targetGroups.size() == 1

      def tgArn = lb.targetGroups[0].getTargetGroupArn()
      lb.targetGroupServices[tgArn][0] == ecsService.getServiceName()
    }
  }

  def 'should retrieve load balancers mapped to multiple services'() {
    given:
    def applicationName = 'myEcsApp'
    def tgArn = 'arn:aws:elasticloadbalancing:us-west-1:1234567890:targetgroup/test-tg-1/2136bac'

    // define 2 ecs services load balanced behind the same target group
    LoadBalancer service1lb = new LoadBalancer()
    service1lb.setContainerName("container-name")
    service1lb.setContainerPort(8080)
    service1lb.setTargetGroupArn(tgArn)

    Service ecsService1 = new Service()
    ecsService1.setServiceName('ecs-test-one-000v')
    ecsService1.setServiceArn('arn:aws:ecs:service/ecs-test-one-000v')
    ecsService1.setLoadBalancers([service1lb])
    ecsService1.setAccount('test-account')
    ecsService1.setApplicationName(applicationName)

    LoadBalancer service2lb = new LoadBalancer()
    service2lb.setContainerName("container-name")
    service2lb.setContainerPort(8080)
    service2lb.setTargetGroupArn(tgArn)

    Service ecsService2 = new Service()
    ecsService2.setServiceName('ecs-test-two-000v')
    ecsService2.setServiceArn('arn:aws:ecs:service/ecs-test-two-000v')
    ecsService2.setLoadBalancers([service2lb])
    ecsService2.setAccount('test-account')
    ecsService2.setApplicationName(applicationName)

    // mock the cache entries for the TG and associated LB
    EcsTargetGroup ecsTg = new EcsTargetGroup()
    ecsTg.setTargetGroupArn(tgArn)
    ecsTg.setTargetGroupName('test-tg-1')

    EcsLoadBalancerCache ecsLoadBalancerCache = new EcsLoadBalancerCache(
      account: 'test-account',
      region: 'us-west-2',
      loadBalancerArn: 'arn:1',
      loadBalancerType: 'application',
      cloudProvider: EcsCloudProvider.ID,
      listeners: [],
      scheme: 'scheme',
      availabilityZones: [],
      ipAddressType: 'ipv4',
      loadBalancerName: 'load-balancer-name1',
      canonicalHostedZoneId: 'zone-id',
      vpcId: 'vpc-id',
      dnsname: 'dns-name',
      createdTime: System.currentTimeMillis(),
      subnets: [],
      securityGroups: [],
      targetGroups: [ecsTg.getTargetGroupName()],
      serverGroups: []
    )

    when:
    def loadBalancerList = provider.getApplicationLoadBalancers(applicationName)

    then:
    mockServiceCache.getAll(_) >> [ecsService1, ecsService2]
    mockTargetGroupCache.getAllKeys() >> ['fake-tg-key-1']
    mockTargetGroupCache.find(_) >> [ecsTg]
    mockLBCache.findWithTargetGroups(_) >> Collections.singletonList(ecsLoadBalancerCache)

    loadBalancerList.size() == 1

    def lb = loadBalancerList[0]
    lb.targetGroupServices.size() == 1
    lb.targetGroups.size() == 1
    def services = lb.targetGroupServices[tgArn]
    services.size() == 2
  }
}
