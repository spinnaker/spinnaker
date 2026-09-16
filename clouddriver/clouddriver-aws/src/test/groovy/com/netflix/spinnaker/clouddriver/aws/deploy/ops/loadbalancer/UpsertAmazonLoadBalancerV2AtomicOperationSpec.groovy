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

import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client
import software.amazon.awssdk.services.elasticloadbalancingv2.model.*
import software.amazon.awssdk.services.shield.ShieldClient
import software.amazon.awssdk.services.shield.model.CreateProtectionRequest
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
          stickinessEnabled  : false,
          stickinessType     : "lb_cookie",
          stickinessDuration : 86400
        ]
      )
    ],
    subnetType: "internal",
    idleTimeout: 60,
    deletionProtection: true
  )
  UpsertAmazonLoadBalancerV2Description updateDescription = new UpsertAmazonLoadBalancerV2Description(
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
        ]
      )
    ],
    subnetType: "internal",
    idleTimeout: 60,
    deletionProtection: true
  )
  UpsertAmazonLoadBalancerV2Description descriptionWithNoAttributes = new UpsertAmazonLoadBalancerV2Description(
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
        ]
      )
    ],
    subnetType: "internal",
    idleTimeout: 60,
    deletionProtection: true
  )
  UpsertAmazonLoadBalancerV2Description nlbDescription = new UpsertAmazonLoadBalancerV2Description(
    loadBalancerType: AmazonLoadBalancerType.NETWORK,
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
          stickinessEnabled  : false,
          stickinessType     : "lb_cookie",
          stickinessDuration : 86400
        ]
      )
    ],
    subnetType: "internal",
    idleTimeout: 60,
    deletionProtection: true,
    loadBalancingCrossZone: true
  )

  def loadBalancerArn = "test:arn"
  def targetGroupArn = "test:target:group:arn"
  def targetGroup = TargetGroup.builder().targetGroupArn(targetGroupArn).targetGroupName(targetGroupName).port(80).protocol(ProtocolEnum.HTTP).build()
  def targetGroupOld = TargetGroup.builder().targetGroupArn(targetGroupArn).targetGroupName("target-group-foo-existing").port(80).protocol(ProtocolEnum.HTTP).build()
  def loadBalancerOld = LoadBalancer.builder().loadBalancerName("foo-main-frontend").loadBalancerArn(loadBalancerArn).type("application").build()
  def loadBalancerAttributes = [LoadBalancerAttribute.builder().key("idle_timeout.timeout_seconds").value("60").build(), LoadBalancerAttribute.builder().key("deletion_protection.enabled").value("true").build()]

  ShieldClient awsShield = Mock(ShieldClient)
  ElasticLoadBalancingV2Client loadBalancing = Mock(ElasticLoadBalancingV2Client)
  def mockAmazonClientProvider = Stub(AmazonClientProvider) {
    getElasticLoadBalancingV2Client(_, _) >> loadBalancing
    getAmazonShieldV2(_, _) >> awsShield
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

  def ingressLoadBalancerBuilder = Mock(IngressLoadBalancerBuilder)

  @Subject
    operation = new UpsertAmazonLoadBalancerV2AtomicOperation(description)

  def setup() {
    operation.amazonClientProvider = mockAmazonClientProvider
    operation.regionScopedProviderFactory = regionScopedProviderFactory
    operation.deployDefaults = new AwsConfiguration.DeployDefaults(addAppGroupToServerGroup: true, createLoadBalancerIngressPermissions: true)
    operation.ingressLoadBalancerBuilder = ingressLoadBalancerBuilder
  }

  void "should create load balancer"() {
    setup:
    def existingLoadBalancers = []
    def existingTargetGroups = []
    def existingListeners = []
    description.vpcId = 'vpcId'

    when:
    operation.operate([])

    then:
    1 * mockSubnetAnalyzer.getSubnetIdsForZones(['us-east-1a'], 'internal', SubnetTarget.ELB, 1) >> ["subnet-1"]
    1 * loadBalancing.describeLoadBalancers(DescribeLoadBalancersRequest.builder().names(["foo-main-frontend"]).build()) >>
      DescribeLoadBalancersResponse.builder().loadBalancers(existingLoadBalancers).build()
    1 * loadBalancing.setIpAddressType(SetIpAddressTypeRequest.builder().loadBalancerArn(loadBalancerArn).ipAddressType('ipv4').build()) >> SetIpAddressTypeResponse.builder().ipAddressType('ipv4').build()
    1 * loadBalancing.createLoadBalancer(CreateLoadBalancerRequest.builder()
      .ipAddressType('ipv4')
      .name("foo-main-frontend")
      .subnets(["subnet-1"])
      .securityGroups(["sg-1234"])
      .scheme("internal")
      .type("application")
      .build()) >> CreateLoadBalancerResponse.builder().loadBalancers([LoadBalancer.builder().dnsName("dnsName1").loadBalancerArn(loadBalancerArn).type("application").build()]).build()
    1 * ingressLoadBalancerBuilder.ingressApplicationLoadBalancerGroup(
      'foo',
      'us-east-1',
      'bar',
      description.credentials,
      "vpcId",
      { it.toList().sort() == [80, 8080] },
      _) >> new IngressLoadBalancerBuilder.IngressLoadBalancerGroupResult("sg-1234", "kato-elb")
    1 * loadBalancing.setSecurityGroups(SetSecurityGroupsRequest.builder()
      .loadBalancerArn(loadBalancerArn)
      .securityGroups(["sg-1234"])
      .build())
    1 * loadBalancing.describeTargetGroups(DescribeTargetGroupsRequest.builder().loadBalancerArn(loadBalancerArn).build()) >> DescribeTargetGroupsResponse.builder().targetGroups(existingTargetGroups).build()
    1 * loadBalancing.createTargetGroup(_ as CreateTargetGroupRequest) >> CreateTargetGroupResponse.builder().targetGroups([targetGroup]).build()
    1 * loadBalancing.modifyTargetGroupAttributes(_ as ModifyTargetGroupAttributesRequest)
    1 * loadBalancing.describeListeners(DescribeListenersRequest.builder().loadBalancerArn(loadBalancerArn).build()) >> DescribeListenersResponse.builder().listeners(existingListeners).build()
    1 * loadBalancing.createListener(CreateListenerRequest.builder().loadBalancerArn(loadBalancerArn).port(80).protocol("HTTP").defaultActions([Action.builder().targetGroupArn(targetGroupArn).type(ActionTypeEnum.FORWARD).order(1).build()]).build())
    1 * loadBalancing.describeLoadBalancerAttributes(_) >> DescribeLoadBalancerAttributesResponse.builder().attributes(loadBalancerAttributes).build()
    0 * _
  }

  void "should create target group for existing load balancer"() {
    setup:
    def existingLoadBalancers = [loadBalancerOld]
    def existingTargetGroups = []
    def existingListeners = []

    when:
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(DescribeLoadBalancersRequest.builder().names(["foo-main-frontend"]).build()) >>
      DescribeLoadBalancersResponse.builder().loadBalancers(existingLoadBalancers).build()
    1 * loadBalancing.setSecurityGroups(SetSecurityGroupsRequest.builder()
      .loadBalancerArn(loadBalancerArn)
      .securityGroups(["sg-1234"])
      .build())
    1 * loadBalancing.describeTargetGroups(DescribeTargetGroupsRequest.builder().loadBalancerArn(loadBalancerArn).build()) >> DescribeTargetGroupsResponse.builder().targetGroups(existingTargetGroups).build()
    1 * loadBalancing.createTargetGroup(_ as CreateTargetGroupRequest) >> CreateTargetGroupResponse.builder().targetGroups([targetGroup]).build()
    1 * loadBalancing.modifyTargetGroupAttributes(_ as ModifyTargetGroupAttributesRequest)
    1 * loadBalancing.describeListeners(DescribeListenersRequest.builder().loadBalancerArn(loadBalancerArn).build()) >> DescribeListenersResponse.builder().listeners(existingListeners).build()
    1 * loadBalancing.createListener(CreateListenerRequest.builder().loadBalancerArn(loadBalancerArn).port(80).protocol("HTTP").defaultActions([Action.builder().targetGroupArn(targetGroupArn).type(ActionTypeEnum.FORWARD).order(1).build()]).build())
    1 * loadBalancing.describeLoadBalancerAttributes(_) >> DescribeLoadBalancerAttributesResponse.builder().attributes(loadBalancerAttributes).build()
    1 * loadBalancing.setIpAddressType(SetIpAddressTypeRequest.builder().loadBalancerArn(loadBalancerArn).ipAddressType('ipv4').build()) >> SetIpAddressTypeResponse.builder().ipAddressType('ipv4').build()
    0 * _
  }

  void "should create target group attributes passed for existing load balancer"() {
    setup:
    def existingLoadBalancers = [loadBalancerOld]
    def existingTargetGroups = []
    def existingListeners = []

    when:
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(DescribeLoadBalancersRequest.builder().names(["foo-main-frontend"]).build()) >>
      DescribeLoadBalancersResponse.builder().loadBalancers(existingLoadBalancers).build()
    1 * loadBalancing.setSecurityGroups(SetSecurityGroupsRequest.builder()
      .loadBalancerArn(loadBalancerArn)
      .securityGroups(["sg-1234"])
      .build())
    1 * loadBalancing.setIpAddressType(SetIpAddressTypeRequest.builder().loadBalancerArn(loadBalancerArn).ipAddressType('ipv4').build()) >> SetIpAddressTypeResponse.builder().ipAddressType('ipv4').build()
    1 * loadBalancing.describeTargetGroups(DescribeTargetGroupsRequest.builder().loadBalancerArn(loadBalancerArn).build()) >> DescribeTargetGroupsResponse.builder().targetGroups(existingTargetGroups).build()
    1 * loadBalancing.createTargetGroup(_ as CreateTargetGroupRequest) >> CreateTargetGroupResponse.builder().targetGroups([targetGroup]).build()
    1 * loadBalancing.describeListeners(DescribeListenersRequest.builder().loadBalancerArn(loadBalancerArn).build()) >> DescribeListenersResponse.builder().listeners(existingListeners).build()
    1 * loadBalancing.createListener(CreateListenerRequest.builder().loadBalancerArn(loadBalancerArn).port(80).protocol("HTTP").defaultActions([Action.builder().targetGroupArn(targetGroupArn).type(ActionTypeEnum.FORWARD).order(1).build()]).build())
    1 * loadBalancing.describeLoadBalancerAttributes(_) >> DescribeLoadBalancerAttributesResponse.builder().attributes(loadBalancerAttributes).build()
    1 * loadBalancing.modifyTargetGroupAttributes(_) >> { ModifyTargetGroupAttributesRequest request ->
      assert request.attributes().find { it.key() == 'deregistration_delay.timeout_seconds' }.value() == "300"
      assert request.attributes().find { it.key() == 'stickiness.enabled' }.value() == "false"
      assert request.attributes().find { it.key() == 'stickiness.type' }.value() == "lb_cookie"
      assert request.attributes().find { it.key() == 'stickiness.lb_cookie.duration_seconds' }.value() == "86400"
      assert request.targetGroupArn() == "test:target:group:arn"
      return ModifyTargetGroupAttributesResponse.builder().build()
    }
    0 * _
  }

  void "should create target group attributes with defaults for existing load balancer"() {
    @Subject createOperation = new UpsertAmazonLoadBalancerV2AtomicOperation(descriptionWithNoAttributes)
    setup:
    def existingLoadBalancers = [loadBalancerOld]
    def existingTargetGroups = []
    def existingListeners = []
    createOperation.amazonClientProvider = mockAmazonClientProvider
    createOperation.regionScopedProviderFactory = regionScopedProviderFactory
    createOperation.deployDefaults = new AwsConfiguration.DeployDefaults(addAppGroupToServerGroup: true, createLoadBalancerIngressPermissions: true)
    createOperation.ingressLoadBalancerBuilder = ingressLoadBalancerBuilder
    when:
    createOperation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(DescribeLoadBalancersRequest.builder().names(["foo-main-frontend"]).build()) >>
      DescribeLoadBalancersResponse.builder().loadBalancers(existingLoadBalancers).build()
    1 * loadBalancing.setSecurityGroups(SetSecurityGroupsRequest.builder()
      .loadBalancerArn(loadBalancerArn)
      .securityGroups(["sg-1234"])
      .build())
    1 * loadBalancing.describeTargetGroups(DescribeTargetGroupsRequest.builder().loadBalancerArn(loadBalancerArn).build()) >> DescribeTargetGroupsResponse.builder().targetGroups(existingTargetGroups).build()
    1 * loadBalancing.createTargetGroup(_ as CreateTargetGroupRequest) >> CreateTargetGroupResponse.builder().targetGroups([targetGroup]).build()
    1 * loadBalancing.describeListeners(DescribeListenersRequest.builder().loadBalancerArn(loadBalancerArn).build()) >> DescribeListenersResponse.builder().listeners(existingListeners).build()
    1 * loadBalancing.createListener(CreateListenerRequest.builder().loadBalancerArn(loadBalancerArn).port(80).protocol("HTTP").defaultActions([Action.builder().targetGroupArn(targetGroupArn).type(ActionTypeEnum.FORWARD).order(1).build()]).build())
    1 * loadBalancing.describeLoadBalancerAttributes(_) >> DescribeLoadBalancerAttributesResponse.builder().attributes(loadBalancerAttributes).build()
    1 * loadBalancing.modifyTargetGroupAttributes(_) >> { ModifyTargetGroupAttributesRequest request ->
      assert request.attributes().find { it.key() == 'deregistration_delay.timeout_seconds' }.value() == "300"
      assert request.attributes().find { it.key() == 'stickiness.enabled' }.value() == "false"
      assert request.attributes().find { it.key() == 'stickiness.type' }.value() == "lb_cookie"
      assert request.attributes().find { it.key() == 'stickiness.lb_cookie.duration_seconds' }.value() == "86400"
      assert request.targetGroupArn() == "test:target:group:arn"
      return ModifyTargetGroupAttributesResponse.builder().build()
    }
    1 * loadBalancing.setIpAddressType(SetIpAddressTypeRequest.builder().loadBalancerArn(loadBalancerArn).ipAddressType('ipv4').build()) >> SetIpAddressTypeResponse.builder().ipAddressType('ipv4').build()
    0 * _
  }

  void "should create target group attributes with defaults for existing nlb"() {
    @Subject createNlbOperation = new UpsertAmazonLoadBalancerV2AtomicOperation(nlbDescription)
    setup:
    createNlbOperation.amazonClientProvider = mockAmazonClientProvider
    createNlbOperation.regionScopedProviderFactory = regionScopedProviderFactory
    createNlbOperation.deployDefaults = new AwsConfiguration.DeployDefaults(addAppGroupToServerGroup: true, createLoadBalancerIngressPermissions: true)
    createNlbOperation.ingressLoadBalancerBuilder = ingressLoadBalancerBuilder
    def existingLoadBalancers = []
    def existingTargetGroups = []
    def existingListeners = []
    def nlbLoadBalancerAttributes = [LoadBalancerAttribute.builder().key("idle_timeout.timeout_seconds").value("60").build(),
                                     LoadBalancerAttribute.builder().key("deletion_protection.enabled").value("true").build(),
                                     LoadBalancerAttribute.builder().key("load_balancing.cross_zone.enabled").value("false").build()]

    nlbDescription.vpcId = 'vpcId'

    when:
    createNlbOperation.operate([])

    then:
    1 * mockSubnetAnalyzer.getSubnetIdsForZones(['us-east-1a'], 'internal', SubnetTarget.ELB, 1) >> ["subnet-1"]
    1 * loadBalancing.describeLoadBalancers(DescribeLoadBalancersRequest.builder().names(["foo-main-frontend"]).build()) >>
      DescribeLoadBalancersResponse.builder().loadBalancers(existingLoadBalancers).build()
    1 * loadBalancing.setIpAddressType(SetIpAddressTypeRequest.builder().loadBalancerArn(loadBalancerArn).ipAddressType('ipv4').build()) >> SetIpAddressTypeResponse.builder().ipAddressType('ipv4').build()
    1 * loadBalancing.createLoadBalancer(CreateLoadBalancerRequest.builder()
      .ipAddressType('ipv4')
      .name("foo-main-frontend")
      .subnets(["subnet-1"])
      .scheme("internal")
      .type("network")
      .build()) >> CreateLoadBalancerResponse.builder().loadBalancers([LoadBalancer.builder().dnsName("dnsName1").loadBalancerArn(loadBalancerArn).type("network").build()]).build()
    1 * ingressLoadBalancerBuilder.ingressApplicationLoadBalancerGroup(
      'foo',
      'us-east-1',
      'bar',
      nlbDescription.credentials,
      "vpcId",
      { it.toList().sort() == [80, 8080] },
      _) >> new IngressLoadBalancerBuilder.IngressLoadBalancerGroupResult("sg-1234", "kato-elb")
    1 * loadBalancing.describeTargetGroups(DescribeTargetGroupsRequest.builder().loadBalancerArn(loadBalancerArn).build()) >> DescribeTargetGroupsResponse.builder().targetGroups(existingTargetGroups).build()
    1 * loadBalancing.createTargetGroup(_ as CreateTargetGroupRequest) >> CreateTargetGroupResponse.builder().targetGroups([targetGroup]).build()
    1 * loadBalancing.modifyLoadBalancerAttributes(_) >> { ModifyLoadBalancerAttributesRequest request ->
      assert request.attributes().find { it.key() == 'load_balancing.cross_zone.enabled' }.value()
      assert request.loadBalancerArn() == "test:arn"
      return ModifyLoadBalancerAttributesResponse.builder().build()
    }
    1 * loadBalancing.modifyTargetGroupAttributes(_) >> { ModifyTargetGroupAttributesRequest request ->
      assert request.attributes().find { it.key() == 'deregistration_delay.timeout_seconds' }.value() == "300"
      assert request.attributes().find { it.key() == 'proxy_protocol_v2.enabled' }.value()
      assert request.attributes().find { it.key() == 'deregistration_delay.connection_termination.enabled' }.value()
      assert request.targetGroupArn() == "test:target:group:arn"
      return ModifyTargetGroupAttributesResponse.builder().build()
    }
    1 * loadBalancing.describeListeners(DescribeListenersRequest.builder().loadBalancerArn(loadBalancerArn).build()) >> DescribeListenersResponse.builder().listeners(existingListeners).build()
    1 * loadBalancing.createListener(CreateListenerRequest.builder().loadBalancerArn(loadBalancerArn).port(80).protocol("HTTP").defaultActions([Action.builder().targetGroupArn(targetGroupArn).type(ActionTypeEnum.FORWARD).order(1).build()]).build())
    1 * loadBalancing.describeLoadBalancerAttributes(_) >> DescribeLoadBalancerAttributesResponse.builder().attributes(nlbLoadBalancerAttributes).build()
    0 * _
  }

  void "should create nlb and update cross_zone_enabled attribute only when it is updated"() {
    @Subject createNlbOperation = new UpsertAmazonLoadBalancerV2AtomicOperation(nlbDescription)
    setup:
    createNlbOperation.amazonClientProvider = mockAmazonClientProvider
    createNlbOperation.regionScopedProviderFactory = regionScopedProviderFactory
    createNlbOperation.deployDefaults = new AwsConfiguration.DeployDefaults(addAppGroupToServerGroup: true, createLoadBalancerIngressPermissions: true)
    createNlbOperation.ingressLoadBalancerBuilder = ingressLoadBalancerBuilder
    def loadBalancerOld = LoadBalancer.builder().loadBalancerName("foo-main-frontend").loadBalancerArn(loadBalancerArn).type("network").build()
    def existingLoadBalancers = [loadBalancerOld]
    def existingTargetGroups = []
    def existingListeners = []
    def nlbLoadBalancerAttributes = [LoadBalancerAttribute.builder().key("idle_timeout.timeout_seconds").value("60").build(),
                                     LoadBalancerAttribute.builder().key("deletion_protection.enabled").value("true").build(),
                                     LoadBalancerAttribute.builder().key("load_balancing.cross_zone.enabled").value("true").build()]

    nlbDescription.vpcId = 'vpcId'

    when:
    createNlbOperation.operate([])

    then:

    1 * loadBalancing.describeLoadBalancers(DescribeLoadBalancersRequest.builder().names(["foo-main-frontend"]).build()) >>
      DescribeLoadBalancersResponse.builder().loadBalancers(existingLoadBalancers).build()
    1 * loadBalancing.describeTargetGroups(DescribeTargetGroupsRequest.builder().loadBalancerArn(loadBalancerArn).build()) >> DescribeTargetGroupsResponse.builder().targetGroups(existingTargetGroups).build()
    1 * loadBalancing.createTargetGroup(_ as CreateTargetGroupRequest) >> CreateTargetGroupResponse.builder().targetGroups([targetGroup]).build()
    0 * loadBalancing.modifyLoadBalancerAttributes(_)
    1 * loadBalancing.describeListeners(DescribeListenersRequest.builder().loadBalancerArn(loadBalancerArn).build()) >> DescribeListenersResponse.builder().listeners(existingListeners).build()
    1 * loadBalancing.createListener(CreateListenerRequest.builder().loadBalancerArn(loadBalancerArn).port(80).protocol("HTTP").defaultActions([Action.builder().targetGroupArn(targetGroupArn).type(ActionTypeEnum.FORWARD).order(1).build()]).build())
    1 * loadBalancing.describeLoadBalancerAttributes(_) >> DescribeLoadBalancerAttributesResponse.builder().attributes(nlbLoadBalancerAttributes).build()
    1 * loadBalancing.modifyTargetGroupAttributes(_ as ModifyTargetGroupAttributesRequest)
    1 * loadBalancing.setIpAddressType(SetIpAddressTypeRequest.builder().loadBalancerArn(loadBalancerArn).ipAddressType('ipv4').build()) >> SetIpAddressTypeResponse.builder().ipAddressType('ipv4').build()
    0 * _
  }

  void "should modify target group of existing load balancer"() {
    setup:
    def existingLoadBalancers = [loadBalancerOld]
    def existingTargetGroups = [targetGroup]
    def existingListeners = []

    when:
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(DescribeLoadBalancersRequest.builder().names(["foo-main-frontend"]).build()) >>
      DescribeLoadBalancersResponse.builder().loadBalancers(existingLoadBalancers).build()
    1 * loadBalancing.setSecurityGroups(SetSecurityGroupsRequest.builder()
      .loadBalancerArn(loadBalancerArn)
      .securityGroups(["sg-1234"])
      .build())
    1 * loadBalancing.describeTargetGroups(DescribeTargetGroupsRequest.builder().loadBalancerArn(loadBalancerArn).build()) >> DescribeTargetGroupsResponse.builder().targetGroups(existingTargetGroups).build()
    1 * loadBalancing.modifyTargetGroup(_ as ModifyTargetGroupRequest)
    1 * loadBalancing.modifyTargetGroupAttributes(_ as ModifyTargetGroupAttributesRequest)
    1 * loadBalancing.describeListeners(DescribeListenersRequest.builder().loadBalancerArn(loadBalancerArn).build()) >> DescribeListenersResponse.builder().listeners(existingListeners).build()
    1 * loadBalancing.createListener(CreateListenerRequest.builder().loadBalancerArn(loadBalancerArn).port(80).protocol("HTTP").defaultActions([Action.builder().targetGroupArn(targetGroupArn).type(ActionTypeEnum.FORWARD).order(1).build()]).build())
    1 * loadBalancing.describeLoadBalancerAttributes(_) >> DescribeLoadBalancerAttributesResponse.builder().attributes(loadBalancerAttributes).build()
    1 * loadBalancing.setIpAddressType(SetIpAddressTypeRequest.builder().loadBalancerArn(loadBalancerArn).ipAddressType('ipv4').build()) >> SetIpAddressTypeResponse.builder().ipAddressType('ipv4').build()
    0 * _
  }

  void "should modify only target group attributes that are passed of existing load balancer"() {
    @Subject updateOperation = new UpsertAmazonLoadBalancerV2AtomicOperation(updateDescription)

    setup:
    def existingLoadBalancers = [loadBalancerOld]
    def existingTargetGroups = [targetGroup]
    def existingListeners = []
    updateOperation.amazonClientProvider = mockAmazonClientProvider
    updateOperation.regionScopedProviderFactory = regionScopedProviderFactory
    updateOperation.deployDefaults = new AwsConfiguration.DeployDefaults(addAppGroupToServerGroup: true, createLoadBalancerIngressPermissions: true)
    updateOperation.ingressLoadBalancerBuilder = ingressLoadBalancerBuilder

    when:
    updateOperation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(DescribeLoadBalancersRequest.builder().names(["foo-main-frontend"]).build()) >>
      DescribeLoadBalancersResponse.builder().loadBalancers(existingLoadBalancers).build()
    1 * loadBalancing.setSecurityGroups(SetSecurityGroupsRequest.builder()
      .loadBalancerArn(loadBalancerArn)
      .securityGroups(["sg-1234"])
      .build())
    1 * loadBalancing.describeTargetGroups(DescribeTargetGroupsRequest.builder().loadBalancerArn(loadBalancerArn).build()) >> DescribeTargetGroupsResponse.builder().targetGroups(existingTargetGroups).build()
    1 * loadBalancing.modifyTargetGroup(_ as ModifyTargetGroupRequest)
    1 * loadBalancing.modifyTargetGroupAttributes(_) >> { ModifyTargetGroupAttributesRequest request ->
      assert request.attributes().find { it.key() == 'deregistration_delay.timeout_seconds' }.value() == "300"
      assert request.attributes().find { it.key() == 'stickiness.enabled' } == null
      assert request.attributes().find { it.key() == 'load_balancing.cross_zone.enabled' } == null
      assert request.targetGroupArn() == "test:target:group:arn"
      return ModifyTargetGroupAttributesResponse.builder().build()
    }
    1 * loadBalancing.describeListeners(DescribeListenersRequest.builder().loadBalancerArn(loadBalancerArn).build()) >> DescribeListenersResponse.builder().listeners(existingListeners).build()
    1 * loadBalancing.createListener(CreateListenerRequest.builder().loadBalancerArn(loadBalancerArn).port(80).protocol("HTTP").defaultActions([Action.builder().targetGroupArn(targetGroupArn).type(ActionTypeEnum.FORWARD).order(1).build()]).build())
    1 * loadBalancing.describeLoadBalancerAttributes(_) >> DescribeLoadBalancerAttributesResponse.builder().attributes(loadBalancerAttributes).build()
    1 * loadBalancing.setIpAddressType(SetIpAddressTypeRequest.builder().loadBalancerArn(loadBalancerArn).ipAddressType('ipv4').build()) >> SetIpAddressTypeResponse.builder().ipAddressType('ipv4').build()
    0 * _
  }

  void "should remove missing target group of existing load balancer"() {
    setup:
    def existingLoadBalancers = [loadBalancerOld]
    def existingTargetGroups = [targetGroupOld]
    def existingListeners = []

    when:
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(DescribeLoadBalancersRequest.builder().names(["foo-main-frontend"]).build()) >>
      DescribeLoadBalancersResponse.builder().loadBalancers(existingLoadBalancers).build()
    1 * loadBalancing.setSecurityGroups(SetSecurityGroupsRequest.builder()
      .loadBalancerArn(loadBalancerArn)
      .securityGroups(["sg-1234"])
      .build())
    1 * loadBalancing.describeTargetGroups(DescribeTargetGroupsRequest.builder().loadBalancerArn(loadBalancerArn).build()) >> DescribeTargetGroupsResponse.builder().targetGroups(existingTargetGroups).build()
    1 * loadBalancing.deleteTargetGroup(DeleteTargetGroupRequest.builder().targetGroupArn(targetGroupArn).build())
    1 * loadBalancing.createTargetGroup(_ as CreateTargetGroupRequest) >> CreateTargetGroupResponse.builder().targetGroups([targetGroup]).build()
    1 * loadBalancing.modifyTargetGroupAttributes(_ as ModifyTargetGroupAttributesRequest)
    1 * loadBalancing.describeListeners(DescribeListenersRequest.builder().loadBalancerArn(loadBalancerArn).build()) >> DescribeListenersResponse.builder().listeners(existingListeners).build()
    1 * loadBalancing.createListener(CreateListenerRequest.builder().loadBalancerArn(loadBalancerArn).port(80).protocol("HTTP").defaultActions([Action.builder().targetGroupArn(targetGroupArn).type(ActionTypeEnum.FORWARD).order(1).build()]).build())
    1 * loadBalancing.describeLoadBalancerAttributes(_) >> DescribeLoadBalancerAttributesResponse.builder().attributes(loadBalancerAttributes).build()
    1 * loadBalancing.setIpAddressType(SetIpAddressTypeRequest.builder().loadBalancerArn(loadBalancerArn).ipAddressType('ipv4').build()) >> SetIpAddressTypeResponse.builder().ipAddressType('ipv4').build()
    0 * _
  }

  void "should throw error updating a load balancer if listener targets a non-existent target group"() {
    setup:
    def existingLoadBalancers = [loadBalancerOld]
    def existingTargetGroups = [targetGroupOld]
    def existingListeners = []

    when:
    description.listeners[0].defaultActions[0].targetGroupName = "nope"
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(DescribeLoadBalancersRequest.builder().names(["foo-main-frontend"]).build()) >>
      DescribeLoadBalancersResponse.builder().loadBalancers(existingLoadBalancers).build()
    1 * loadBalancing.setSecurityGroups(SetSecurityGroupsRequest.builder()
      .loadBalancerArn(loadBalancerArn)
      .securityGroups(["sg-1234"])
      .build())
    1 * loadBalancing.describeTargetGroups(DescribeTargetGroupsRequest.builder().loadBalancerArn(loadBalancerArn).build()) >> DescribeTargetGroupsResponse.builder().targetGroups(existingTargetGroups).build()
    1 * loadBalancing.deleteTargetGroup(DeleteTargetGroupRequest.builder().targetGroupArn(targetGroupArn).build())
    1 * loadBalancing.createTargetGroup(_ as CreateTargetGroupRequest) >> CreateTargetGroupResponse.builder().targetGroups([targetGroup]).build()
    1 * loadBalancing.modifyTargetGroupAttributes(_ as ModifyTargetGroupAttributesRequest)
    1 * loadBalancing.describeListeners(DescribeListenersRequest.builder().loadBalancerArn(loadBalancerArn).build()) >> DescribeListenersResponse.builder().listeners(existingListeners).build()
    1 * loadBalancing.createListener(CreateListenerRequest.builder().loadBalancerArn(loadBalancerArn).port(80).protocol("HTTP").defaultActions([]).build())
    1 * loadBalancing.describeLoadBalancerAttributes(_) >> DescribeLoadBalancerAttributesResponse.builder().attributes(loadBalancerAttributes).build()
    1 * loadBalancing.setIpAddressType(SetIpAddressTypeRequest.builder().loadBalancerArn(loadBalancerArn).ipAddressType('ipv4').build()) >> SetIpAddressTypeResponse.builder().ipAddressType('ipv4').build()
    0 * _
    thrown AtomicOperationException
  }

  void "should remove and recreate listeners that have changed on an existing load balancer"() {
    setup:
    def listenerArn = "test:listener:arn"
    def existingLoadBalancers = [loadBalancerOld]
    def existingTargetGroups = [targetGroupOld]
    def existingListeners = [Listener.builder().listenerArn(listenerArn).defaultActions([]).build()]

    when:
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(DescribeLoadBalancersRequest.builder().names(["foo-main-frontend"]).build()) >>
      DescribeLoadBalancersResponse.builder().loadBalancers(existingLoadBalancers).build()
    1 * loadBalancing.setSecurityGroups(SetSecurityGroupsRequest.builder().loadBalancerArn(loadBalancerArn).securityGroups(["sg-1234"]).build())
    1 * loadBalancing.describeTargetGroups(DescribeTargetGroupsRequest.builder().loadBalancerArn(loadBalancerArn).build()) >> DescribeTargetGroupsResponse.builder().targetGroups(existingTargetGroups).build()
    1 * loadBalancing.deleteTargetGroup(DeleteTargetGroupRequest.builder().targetGroupArn(targetGroupArn).build())
    1 * loadBalancing.createTargetGroup(_ as CreateTargetGroupRequest) >> CreateTargetGroupResponse.builder().targetGroups([targetGroup]).build()
    1 * loadBalancing.modifyTargetGroupAttributes(_ as ModifyTargetGroupAttributesRequest)
    1 * loadBalancing.describeListeners(DescribeListenersRequest.builder().loadBalancerArn(loadBalancerArn).build()) >> DescribeListenersResponse.builder().listeners(existingListeners).build()
    1 * loadBalancing.describeRules(DescribeRulesRequest.builder().listenerArn(listenerArn).build()) >> DescribeRulesResponse.builder().rules([]).build()
    1 * loadBalancing.deleteListener(DeleteListenerRequest.builder().listenerArn(listenerArn).build())
    1 * loadBalancing.createListener(CreateListenerRequest.builder().loadBalancerArn(loadBalancerArn).port(80).protocol("HTTP").defaultActions([Action.builder().targetGroupArn(targetGroupArn).type(ActionTypeEnum.FORWARD).order(1).build()]).build())
    1 * loadBalancing.describeLoadBalancerAttributes(_) >> DescribeLoadBalancerAttributesResponse.builder().attributes(loadBalancerAttributes).build()
    1 * loadBalancing.setIpAddressType(SetIpAddressTypeRequest.builder().loadBalancerArn(loadBalancerArn).ipAddressType('ipv4').build()) >> SetIpAddressTypeResponse.builder().ipAddressType('ipv4').build()
    0 * _
  }

  void "should attach shield protection to external loadbalancer"() {
    setup:
    description.credentials = TestCredential.named('bar', [shieldEnabled: true])
    description.isInternal = false
    description.subnetType = 'internet-facing'
    description.vpcId = 'vpcId'
    def existingLoadBalancers = []
    def existingTargetGroups = []
    def existingListeners = []

    when:
    operation.operate([])

    then:
    1 * ingressLoadBalancerBuilder.ingressApplicationLoadBalancerGroup(_, _, _, _, _, _, _) >> new IngressLoadBalancerBuilder.IngressLoadBalancerGroupResult("sg-1234", "foo-elb")
    1 * mockSubnetAnalyzer.getSubnetIdsForZones(['us-east-1a'], 'internet-facing', SubnetTarget.ELB, 1) >> ["subnet-1"]
    1 * loadBalancing.describeLoadBalancers(DescribeLoadBalancersRequest.builder().names(["foo-main-frontend"]).build()) >>
      DescribeLoadBalancersResponse.builder().loadBalancers(existingLoadBalancers).build()
    1 * loadBalancing.setIpAddressType(SetIpAddressTypeRequest.builder().loadBalancerArn(loadBalancerArn).ipAddressType('ipv4').build()) >> SetIpAddressTypeResponse.builder().ipAddressType('ipv4').build()
    1 * loadBalancing.createLoadBalancer(CreateLoadBalancerRequest.builder()
      .ipAddressType("ipv4")
      .name("foo-main-frontend")
      .subnets(["subnet-1"])
      .securityGroups(["sg-1234"])
      .type("application")
      .build()) >> CreateLoadBalancerResponse.builder().loadBalancers([LoadBalancer.builder().dnsName("dnsName1").loadBalancerArn(loadBalancerArn).type("application").build()]).build()
    1 * loadBalancing.setSecurityGroups(SetSecurityGroupsRequest.builder()
      .loadBalancerArn(loadBalancerArn)
      .securityGroups(["sg-1234"])
      .build())
    1 * loadBalancing.describeTargetGroups(DescribeTargetGroupsRequest.builder().loadBalancerArn(loadBalancerArn).build()) >> DescribeTargetGroupsResponse.builder().targetGroups(existingTargetGroups).build()
    1 * loadBalancing.createTargetGroup(_ as CreateTargetGroupRequest) >> CreateTargetGroupResponse.builder().targetGroups([targetGroup]).build()
    1 * loadBalancing.modifyTargetGroupAttributes(_ as ModifyTargetGroupAttributesRequest)
    1 * loadBalancing.describeListeners(DescribeListenersRequest.builder().loadBalancerArn(loadBalancerArn).build()) >> DescribeListenersResponse.builder().listeners(existingListeners).build()
    1 * loadBalancing.createListener(CreateListenerRequest.builder().loadBalancerArn(loadBalancerArn).port(80).protocol("HTTP").defaultActions([Action.builder().targetGroupArn(targetGroupArn).type(ActionTypeEnum.FORWARD).order(1).build()]).build())
    1 * awsShield.createProtection(CreateProtectionRequest.builder()
      .name('foo-main-frontend')
      .resourceArn(loadBalancerArn)
      .build())
    1 * loadBalancing.describeLoadBalancerAttributes(_) >> DescribeLoadBalancerAttributesResponse.builder().attributes(loadBalancerAttributes).build()
    0 * _
  }
}
