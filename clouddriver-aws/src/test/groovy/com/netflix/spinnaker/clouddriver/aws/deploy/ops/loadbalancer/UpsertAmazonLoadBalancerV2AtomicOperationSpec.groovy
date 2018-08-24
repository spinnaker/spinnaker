/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer

import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancingv2.model.*
import com.amazonaws.services.shield.AWSShield
import com.amazonaws.services.shield.model.CreateProtectionRequest
import com.netflix.spinnaker.config.AwsConfiguration
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerV2Description
import com.netflix.spinnaker.clouddriver.aws.model.AmazonLoadBalancerType
import com.netflix.spinnaker.clouddriver.aws.model.SubnetAnalyzer
import com.netflix.spinnaker.clouddriver.aws.model.SubnetTarget
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.aws.services.SecurityGroupService
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException
import spock.lang.Specification
import spock.lang.Subject

class UpsertAmazonLoadBalancerV2AtomicOperationSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def targetGroupName = 'target-group-foo'
  UpsertAmazonLoadBalancerV2Description description = new UpsertAmazonLoadBalancerV2Description(
    loadBalancerType: AmazonLoadBalancerType.APPLICATION,
    name: "foo-main-frontend",
    availabilityZones: ["us-east-1": ["us-east-1a"]],
    listeners: [
      new UpsertAmazonLoadBalancerV2Description.Listener(
        port: 80,
        protocol: ProtocolEnum.HTTP,
        defaultActions: [
          new UpsertAmazonLoadBalancerV2Description.Action(
            targetGroupName: targetGroupName
          )
        ]
      )
    ],
    securityGroups: ["foo"],
    credentials: TestCredential.named('bar'),
    targetGroups: [
      new UpsertAmazonLoadBalancerV2Description.TargetGroup(
        name: "target-group-foo",
        protocol: ProtocolEnum.HTTP,
        port: 80,
        healthCheckProtocol: ProtocolEnum.HTTP,
        healthCheckPort: 8080,
        attributes: [
          deregistrationDelay: 300,
          stickinessEnabled: false,
          stickinessType: "lb_cookie",
          stickinessDuration: 86400
        ]
      )
    ],
    subnetType: "internal",
  )
  def loadBalancerArn = "test:arn"
  def targetGroupArn = "test:target:group:arn"
  def targetGroup = new TargetGroup(targetGroupArn: targetGroupArn, targetGroupName: targetGroupName, port: 80, protocol: ProtocolEnum.HTTP)
  def targetGroupOld = new TargetGroup(targetGroupArn: targetGroupArn, targetGroupName: "target-group-foo-existing", port: 80, protocol: ProtocolEnum.HTTP)
  def loadBalancerOld = new LoadBalancer(loadBalancerName: "foo-main-frontend", loadBalancerArn: loadBalancerArn, type: "application")


  AWSShield awsShield = Mock(AWSShield)
  AmazonElasticLoadBalancing loadBalancing = Mock(AmazonElasticLoadBalancing)
  def mockAmazonClientProvider = Stub(AmazonClientProvider) {
    getAmazonElasticLoadBalancingV2(_, _, true) >> loadBalancing
    getAmazonShield(_, _) >> awsShield
  }
  def mockSecurityGroupService = Stub(SecurityGroupService) {
    getSecurityGroupIds(["foo"], null) >> ["foo": "sg-1234"]
  }
  def mockSubnetAnalyzer = Mock(SubnetAnalyzer)
  def regionScopedProvider = Stub(RegionScopedProviderFactory.RegionScopedProvider) {
    getSecurityGroupService() >> mockSecurityGroupService
    getSubnetAnalyzer() >> mockSubnetAnalyzer
  }
  def regionScopedProviderFactory = Stub(RegionScopedProviderFactory) {
    forRegion(_, "us-east-1") >> regionScopedProvider
  }
  @Subject operation = new UpsertAmazonLoadBalancerV2AtomicOperation(description)

  def setup() {
    operation.amazonClientProvider = mockAmazonClientProvider
    operation.regionScopedProviderFactory = regionScopedProviderFactory
    operation.deployDefaults = new AwsConfiguration.DeployDefaults()
  }

  void "should create load balancer"() {
    setup:
    def existingLoadBalancers = []
    def existingTargetGroups = []
    def existingListeners = []

    when:
    operation.operate([])

    then:
    1 * mockSubnetAnalyzer.getSubnetIdsForZones(['us-east-1a'], 'internal', SubnetTarget.ELB, 1) >> ["subnet-1"]
    1 * loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest(names: ["foo-main-frontend"])) >>
            new DescribeLoadBalancersResult(loadBalancers: existingLoadBalancers)
    1 * loadBalancing.createLoadBalancer(new CreateLoadBalancerRequest(
            name: "foo-main-frontend",
            subnets: ["subnet-1"],
            securityGroups: ["sg-1234"],
            scheme: "internal",
            type: "application"
    )) >> new CreateLoadBalancerResult(loadBalancers: [new LoadBalancer(dNSName: "dnsName1", loadBalancerArn: loadBalancerArn, type: "application")])
    1 * loadBalancing.setSecurityGroups(new SetSecurityGroupsRequest(
      loadBalancerArn: loadBalancerArn,
      securityGroups: ["sg-1234"]
    ))
    1 * loadBalancing.describeTargetGroups(new DescribeTargetGroupsRequest(loadBalancerArn: loadBalancerArn)) >> new DescribeTargetGroupsResult(targetGroups: existingTargetGroups)
    1 * loadBalancing.createTargetGroup(_ as CreateTargetGroupRequest) >> new CreateTargetGroupResult(targetGroups: [targetGroup])
    1 * loadBalancing.modifyTargetGroupAttributes(_ as ModifyTargetGroupAttributesRequest)
    1 * loadBalancing.describeListeners(new DescribeListenersRequest(loadBalancerArn: loadBalancerArn)) >> new DescribeListenersResult(listeners: existingListeners)
    1 * loadBalancing.createListener(new CreateListenerRequest(loadBalancerArn: loadBalancerArn, port: 80, protocol: "HTTP", defaultActions: [new Action(targetGroupArn: targetGroupArn, type: ActionTypeEnum.Forward, order: 1)]))
    0 * _
  }

  void "should create target group for existing load balancer"() {
    setup:
    def existingLoadBalancers = [ loadBalancerOld ]
    def existingTargetGroups = []
    def existingListeners = []

    when:
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest(names: ["foo-main-frontend"])) >>
      new DescribeLoadBalancersResult(loadBalancers: existingLoadBalancers)
    1 * loadBalancing.setSecurityGroups(new SetSecurityGroupsRequest(
      loadBalancerArn: loadBalancerArn,
      securityGroups: ["sg-1234"]
    ))
    1 * loadBalancing.describeTargetGroups(new DescribeTargetGroupsRequest(loadBalancerArn: loadBalancerArn)) >> new DescribeTargetGroupsResult(targetGroups: existingTargetGroups)
    1 * loadBalancing.createTargetGroup(_ as CreateTargetGroupRequest) >> new CreateTargetGroupResult(targetGroups: [targetGroup])
    1 * loadBalancing.modifyTargetGroupAttributes(_ as ModifyTargetGroupAttributesRequest)
    1 * loadBalancing.describeListeners(new DescribeListenersRequest(loadBalancerArn: loadBalancerArn)) >> new DescribeListenersResult(listeners: existingListeners)
    1 * loadBalancing.createListener(new CreateListenerRequest(loadBalancerArn: loadBalancerArn, port: 80, protocol: "HTTP", defaultActions: [new Action(targetGroupArn: targetGroupArn, type: ActionTypeEnum.Forward, order: 1)]))
    0 * _
  }

  void "should modify target group of existing load balancer"() {
    setup:
    def existingLoadBalancers = [ loadBalancerOld ]
    def existingTargetGroups = [ targetGroup ]
    def existingListeners = []

    when:
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest(names: ["foo-main-frontend"])) >>
      new DescribeLoadBalancersResult(loadBalancers: existingLoadBalancers)
    1 * loadBalancing.setSecurityGroups(new SetSecurityGroupsRequest(
      loadBalancerArn: loadBalancerArn,
      securityGroups: ["sg-1234"]
    ))
    1 * loadBalancing.describeTargetGroups(new DescribeTargetGroupsRequest(loadBalancerArn: loadBalancerArn)) >> new DescribeTargetGroupsResult(targetGroups: existingTargetGroups)
    1 * loadBalancing.modifyTargetGroup(_ as ModifyTargetGroupRequest)
    1 * loadBalancing.modifyTargetGroupAttributes(_ as ModifyTargetGroupAttributesRequest)
    1 * loadBalancing.describeListeners(new DescribeListenersRequest(loadBalancerArn: loadBalancerArn)) >> new DescribeListenersResult(listeners: existingListeners)
    1 * loadBalancing.createListener(new CreateListenerRequest(loadBalancerArn: loadBalancerArn, port: 80, protocol: "HTTP", defaultActions: [new Action(targetGroupArn: targetGroupArn, type: ActionTypeEnum.Forward, order: 1)]))
    0 * _
  }

  void "should remove missing target group of existing load balancer"() {
    setup:
    def existingLoadBalancers = [ loadBalancerOld ]
    def existingTargetGroups = [ targetGroupOld ]
    def existingListeners = []

    when:
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest(names: ["foo-main-frontend"])) >>
      new DescribeLoadBalancersResult(loadBalancers: existingLoadBalancers)
    1 * loadBalancing.setSecurityGroups(new SetSecurityGroupsRequest(
      loadBalancerArn: loadBalancerArn,
      securityGroups: ["sg-1234"]
    ))
    1 * loadBalancing.describeTargetGroups(new DescribeTargetGroupsRequest(loadBalancerArn: loadBalancerArn)) >> new DescribeTargetGroupsResult(targetGroups: existingTargetGroups)
    1 * loadBalancing.deleteTargetGroup(new DeleteTargetGroupRequest(targetGroupArn: targetGroupArn))
    1 * loadBalancing.createTargetGroup(_ as CreateTargetGroupRequest) >> new CreateTargetGroupResult(targetGroups: [targetGroup])
    1 * loadBalancing.modifyTargetGroupAttributes(_ as ModifyTargetGroupAttributesRequest)
    1 * loadBalancing.describeListeners(new DescribeListenersRequest(loadBalancerArn: loadBalancerArn)) >> new DescribeListenersResult(listeners: existingListeners)
    1 * loadBalancing.createListener(new CreateListenerRequest(loadBalancerArn: loadBalancerArn, port: 80, protocol: "HTTP", defaultActions: [new Action(targetGroupArn: targetGroupArn, type: ActionTypeEnum.Forward, order: 1)]))
    0 * _
  }

  void "should throw error updating a load balancer if listener targets a non-existent target group"() {
    setup:
    def existingLoadBalancers = [ loadBalancerOld ]
    def existingTargetGroups = [ targetGroupOld ]
    def existingListeners = []

    when:
    description.listeners[0].defaultActions[0].targetGroupName = "nope"
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest(names: ["foo-main-frontend"])) >>
      new DescribeLoadBalancersResult(loadBalancers: existingLoadBalancers)
    1 * loadBalancing.setSecurityGroups(new SetSecurityGroupsRequest(
      loadBalancerArn: loadBalancerArn,
      securityGroups: ["sg-1234"]
    ))
    1 * loadBalancing.describeTargetGroups(new DescribeTargetGroupsRequest(loadBalancerArn: loadBalancerArn)) >> new DescribeTargetGroupsResult(targetGroups: existingTargetGroups)
    1 * loadBalancing.deleteTargetGroup(new DeleteTargetGroupRequest(targetGroupArn: targetGroupArn))
    1 * loadBalancing.createTargetGroup(_ as CreateTargetGroupRequest) >> new CreateTargetGroupResult(targetGroups: [targetGroup])
    1 * loadBalancing.modifyTargetGroupAttributes(_ as ModifyTargetGroupAttributesRequest)
    1 * loadBalancing.describeListeners(new DescribeListenersRequest(loadBalancerArn: loadBalancerArn)) >> new DescribeListenersResult(listeners: existingListeners)
    1 * loadBalancing.createListener(new CreateListenerRequest(loadBalancerArn: loadBalancerArn, port: 80, protocol: "HTTP", defaultActions: []))
    0 * _
    thrown AtomicOperationException
  }

  void "should remove and recreate listeners that have changed on an existing load balancer"() {
    setup:
    def listenerArn = "test:listener:arn"
    def existingLoadBalancers = [ loadBalancerOld ]
    def existingTargetGroups = [ targetGroupOld ]
    def existingListeners = [ new Listener(listenerArn: listenerArn, defaultActions: [])]

    when:
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest(names: ["foo-main-frontend"])) >>
      new DescribeLoadBalancersResult(loadBalancers: existingLoadBalancers)
    1 * loadBalancing.setSecurityGroups(new SetSecurityGroupsRequest(loadBalancerArn: loadBalancerArn, securityGroups: ["sg-1234"]))
    1 * loadBalancing.describeTargetGroups(new DescribeTargetGroupsRequest(loadBalancerArn: loadBalancerArn)) >> new DescribeTargetGroupsResult(targetGroups: existingTargetGroups)
    1 * loadBalancing.deleteTargetGroup(new DeleteTargetGroupRequest(targetGroupArn: targetGroupArn))
    1 * loadBalancing.createTargetGroup(_ as CreateTargetGroupRequest) >> new CreateTargetGroupResult(targetGroups: [targetGroup])
    1 * loadBalancing.modifyTargetGroupAttributes(_ as ModifyTargetGroupAttributesRequest)
    1 * loadBalancing.describeListeners(new DescribeListenersRequest(loadBalancerArn: loadBalancerArn)) >> new DescribeListenersResult(listeners: existingListeners)
    1 * loadBalancing.describeRules(new DescribeRulesRequest(listenerArn: listenerArn)) >> new DescribeRulesResult(rules: [])
    1 * loadBalancing.deleteListener(new DeleteListenerRequest(listenerArn: listenerArn))
    1 * loadBalancing.createListener(new CreateListenerRequest(loadBalancerArn: loadBalancerArn, port: 80, protocol: "HTTP", defaultActions: [new Action(targetGroupArn: targetGroupArn, type: ActionTypeEnum.Forward, order: 1)]))
    0 * _
  }

  void "should attach shield protection to external loadbalancer"() {
    setup:
    description.credentials = TestCredential.named('bar', [shieldEnabled: true])
    description.isInternal = false
    description.subnetType = 'internet-facing'
    def existingLoadBalancers = []
    def existingTargetGroups = []
    def existingListeners = []

    when:
    operation.operate([])

    then:
    1 * mockSubnetAnalyzer.getSubnetIdsForZones(['us-east-1a'], 'internet-facing', SubnetTarget.ELB, 1) >> ["subnet-1"]
    1 * loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest(names: ["foo-main-frontend"])) >>
      new DescribeLoadBalancersResult(loadBalancers: existingLoadBalancers)
    1 * loadBalancing.createLoadBalancer(new CreateLoadBalancerRequest(
      name: "foo-main-frontend",
      subnets: ["subnet-1"],
      securityGroups: ["sg-1234"],
      type: "application"
    )) >> new CreateLoadBalancerResult(loadBalancers: [new LoadBalancer(dNSName: "dnsName1", loadBalancerArn: loadBalancerArn, type: "application")])
    1 * loadBalancing.setSecurityGroups(new SetSecurityGroupsRequest(
      loadBalancerArn: loadBalancerArn,
      securityGroups: ["sg-1234"]
    ))
    1 * loadBalancing.describeTargetGroups(new DescribeTargetGroupsRequest(loadBalancerArn: loadBalancerArn)) >> new DescribeTargetGroupsResult(targetGroups: existingTargetGroups)
    1 * loadBalancing.createTargetGroup(_ as CreateTargetGroupRequest) >> new CreateTargetGroupResult(targetGroups: [targetGroup])
    1 * loadBalancing.modifyTargetGroupAttributes(_ as ModifyTargetGroupAttributesRequest)
    1 * loadBalancing.describeListeners(new DescribeListenersRequest(loadBalancerArn: loadBalancerArn)) >> new DescribeListenersResult(listeners: existingListeners)
    1 * loadBalancing.createListener(new CreateListenerRequest(loadBalancerArn: loadBalancerArn, port: 80, protocol: "HTTP", defaultActions: [new Action(targetGroupArn: targetGroupArn, type: ActionTypeEnum.Forward, order: 1)]))
    1 * awsShield.createProtection(new CreateProtectionRequest(
      name: 'foo-main-frontend',
      resourceArn: loadBalancerArn
    ))
    0 * _
  }
}
