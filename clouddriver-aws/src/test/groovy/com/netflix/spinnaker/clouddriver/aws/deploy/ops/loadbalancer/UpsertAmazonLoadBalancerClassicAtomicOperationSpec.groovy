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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.SecurityGroup
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.ApplySecurityGroupsToLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.ConfigureHealthCheckRequest
import com.amazonaws.services.elasticloadbalancing.model.ConnectionDraining
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerListenersRequest
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerResult
import com.amazonaws.services.elasticloadbalancing.model.CrossZoneLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerListenersRequest
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancerAttributesRequest
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancerAttributesResult
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult
import com.amazonaws.services.elasticloadbalancing.model.HealthCheck
import com.amazonaws.services.elasticloadbalancing.model.Listener
import com.amazonaws.services.elasticloadbalancing.model.ListenerDescription
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerAttributes
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.amazonaws.services.elasticloadbalancing.model.ModifyLoadBalancerAttributesRequest
import com.amazonaws.services.shield.AWSShield
import com.amazonaws.services.shield.model.CreateProtectionRequest
import com.netflix.spinnaker.config.AwsConfiguration
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerClassicDescription
import com.netflix.spinnaker.clouddriver.aws.model.SubnetAnalyzer
import com.netflix.spinnaker.clouddriver.aws.model.SubnetTarget
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.aws.services.SecurityGroupService
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.*

class UpsertAmazonLoadBalancerClassicAtomicOperationSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  UpsertAmazonLoadBalancerClassicDescription description = new UpsertAmazonLoadBalancerClassicDescription(
          name: "kato-main-frontend",
          availabilityZones: ["us-east-1": ["us-east-1a"]],
          listeners: [
                  new UpsertAmazonLoadBalancerClassicDescription.Listener(
                          externalProtocol: UpsertAmazonLoadBalancerClassicDescription.Listener.ListenerType.HTTP,
                          externalPort: 80,
                          internalPort: 8501
                  )
          ],
          securityGroups: ["foo"],
          credentials: TestCredential.named('bar'),
          healthCheck: "HTTP:7001/health",
          healthCheckPort: 7001
  )
  AmazonElasticLoadBalancing loadBalancing = Mock(AmazonElasticLoadBalancing)
  AWSShield awsShield = Mock(AWSShield)
  def mockAmazonClientProvider = Stub(AmazonClientProvider) {
    getAmazonElasticLoadBalancing(_, _, true) >> loadBalancing
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

  def securityGroupLookup = Mock(SecurityGroupLookup)
  def securityGroupLookupFactory = Stub(SecurityGroupLookupFactory) {
    getInstance("us-east-1") >> securityGroupLookup
  }

  def elbSecurityGroup = new SecurityGroup()
    .withVpcId(description.vpcId)
    .withGroupId("sg-1234")
    .withGroupName("kato-elb")

  def applicationSecurityGroup = new SecurityGroup()
    .withVpcId(description.vpcId)
    .withGroupId("sg-1111")
    .withGroupName("kato")

  def elbSecurityGroupUpdater = Mock(SecurityGroupUpdater)
  def appSecurityGroupUpdater = Mock(SecurityGroupUpdater)

  @Subject operation = new UpsertAmazonLoadBalancerAtomicOperation(description)

  def setup() {
    operation.deployDefaults = new AwsConfiguration.DeployDefaults(addAppGroupToServerGroup: true, createLoadBalancerIngressPermissions: true)
    operation.amazonClientProvider = mockAmazonClientProvider
    operation.regionScopedProviderFactory = regionScopedProviderFactory
    operation.securityGroupLookupFactory = securityGroupLookupFactory
  }

  void "should create load balancer"() {
    given:
    def existingLoadBalancers = []
    description.vpcId = "vpcId"

    when:
    description.subnetType = 'internal'
    operation.operate([])

    then:
    1 * securityGroupLookup.getSecurityGroupByName('bar', 'kato-elb', 'vpcId') >> Optional.of(elbSecurityGroupUpdater)
    1 * securityGroupLookup.getSecurityGroupByName('bar', 'kato', 'vpcId') >> Optional.of(appSecurityGroupUpdater)
    1 * elbSecurityGroupUpdater.getSecurityGroup() >> elbSecurityGroup
    1 * appSecurityGroupUpdater.getSecurityGroup() >> applicationSecurityGroup
    1 * appSecurityGroupUpdater.addIngress(_) >> {
      def permissions = it[0] as List<IpPermission>
      assert permissions.size() == 2
      assert 7001 in permissions*.fromPort && 8501 in permissions*.fromPort
      assert 7001 in permissions*.toPort && 8501 in permissions*.toPort
      assert elbSecurityGroup.groupId in permissions[0].userIdGroupPairs*.groupId
      assert elbSecurityGroup.groupId in permissions[1].userIdGroupPairs*.groupId
    }

    and:
    1 * mockSubnetAnalyzer.getSubnetIdsForZones(['us-east-1a'], 'internal', SubnetTarget.ELB, 1) >> ["subnet-1"]
    1 * loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest(loadBalancerNames: ["kato-main-frontend"])) >>
            new DescribeLoadBalancersResult(loadBalancerDescriptions: existingLoadBalancers)
    1 * loadBalancing.createLoadBalancer(new CreateLoadBalancerRequest(
            loadBalancerName: "kato-main-frontend",
            listeners: [
                    new Listener(protocol: "HTTP", loadBalancerPort: 80, instanceProtocol: "HTTP", instancePort: 8501)
            ],
            availabilityZones: [],
            subnets: ["subnet-1"],
            securityGroups: ["sg-1234"],
            scheme: "internal",
            tags: []
    )) >> new CreateLoadBalancerResult(dNSName: "dnsName1")
    1 * loadBalancing.configureHealthCheck(new ConfigureHealthCheckRequest(
            loadBalancerName: "kato-main-frontend",
            healthCheck: new HealthCheck(
                    target: "HTTP:7001/health",
                    interval: 10,
                    timeout: 5,
                    unhealthyThreshold: 2,
                    healthyThreshold: 10
            )
    ))
    1 * loadBalancing.modifyLoadBalancerAttributes(new ModifyLoadBalancerAttributesRequest(
            loadBalancerName: "kato-main-frontend",
            loadBalancerAttributes: new LoadBalancerAttributes(
                    crossZoneLoadBalancing: new CrossZoneLoadBalancing(enabled: true),
                    connectionDraining: new ConnectionDraining(enabled: false),
                    additionalAttributes: []
            )
    ))
    0 * _
  }

  void "should fail updating a load balancer with no security groups in VPC"() {
    given:
    def existingLoadBalancers = [
      new LoadBalancerDescription(loadBalancerName: "kato-main-frontend", vPCId: "test-vpc").withListenerDescriptions(
        new ListenerDescription().withListener(new Listener(protocol: "HTTP", loadBalancerPort: 80, instanceProtocol: "HTTP", instancePort: 8501))
      )
    ]

    and:
    loadBalancing.describeLoadBalancers(
      new DescribeLoadBalancersRequest(loadBalancerNames: ["kato-main-frontend"])
    ) >> new DescribeLoadBalancersResult(loadBalancerDescriptions: existingLoadBalancers)

    and: 'auto-creating groups fails'
    description.securityGroups = []
    description.vpcId = "vpcId"
    securityGroupLookupFactory.getInstance("us-east-1") >> securityGroupLookup
    _* securityGroupLookup.getSecurityGroupByName(_ as String, _ as String, _ as String) >> {
      throw new Exception()
    }

    when:
    operation.operate([])

    then:
    thrown(IllegalArgumentException)

    when: "in EC2 classic"
    existingLoadBalancers = [
      new LoadBalancerDescription(loadBalancerName: "kato-main-frontend").withListenerDescriptions(
        new ListenerDescription().withListener(new Listener(protocol: "HTTP", loadBalancerPort: 80, instanceProtocol: "HTTP", instancePort: 8501))
      )
    ]

    and:
    loadBalancing.describeLoadBalancers(
      new DescribeLoadBalancersRequest(loadBalancerNames: ["kato-main-frontend"])
    ) >> new DescribeLoadBalancersResult(loadBalancerDescriptions: existingLoadBalancers)

    then:
    notThrown(IllegalArgumentException)
  }

  void "should update existing load balancer"() {
    def existingLoadBalancers = [
      new LoadBalancerDescription(loadBalancerName: "kato-main-frontend")
        .withListenerDescriptions(
        new ListenerDescription().withListener(new Listener(protocol: "HTTP", loadBalancerPort: 80, instanceProtocol: "HTTP", instancePort: 8501))
      )
    ]

    given:
    description.listeners.add(
      new UpsertAmazonLoadBalancerClassicDescription.Listener(
        externalProtocol: UpsertAmazonLoadBalancerClassicDescription.Listener.ListenerType.HTTP,
        externalPort: 8080,
        internalPort: 8080
      ))
    description.crossZoneBalancing = true

    when:
    operation.operate([])

    then: 'should not auto create elb sg on update'
    0 * appSecurityGroupUpdater.addIngress(_)

    and:
    1 * loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest(loadBalancerNames: ["kato-main-frontend"])) >>
            new DescribeLoadBalancersResult(loadBalancerDescriptions: existingLoadBalancers)
    1 * loadBalancing.createLoadBalancerListeners(new CreateLoadBalancerListenersRequest(
            loadBalancerName: "kato-main-frontend",
            listeners: [ new Listener(protocol: "HTTP", loadBalancerPort: 8080, instanceProtocol: "HTTP", instancePort: 8080) ]
    ))
    1 * loadBalancing.applySecurityGroupsToLoadBalancer(new ApplySecurityGroupsToLoadBalancerRequest(
            loadBalancerName: "kato-main-frontend",
            securityGroups: ["sg-1234"]
    ))
    1 * loadBalancing.configureHealthCheck(new ConfigureHealthCheckRequest(
            loadBalancerName: "kato-main-frontend",
            healthCheck: new HealthCheck(
                    target: "HTTP:7001/health",
                    interval: 10,
                    timeout: 5,
                    unhealthyThreshold: 2,
                    healthyThreshold: 10
            )
    ))
    1 * loadBalancing.describeLoadBalancerAttributes(new DescribeLoadBalancerAttributesRequest(loadBalancerName: "kato-main-frontend")) >>
            new DescribeLoadBalancerAttributesResult(loadBalancerAttributes:
              new LoadBalancerAttributes(
                crossZoneLoadBalancing: new CrossZoneLoadBalancing(enabled: false),
                connectionDraining: new ConnectionDraining(enabled: false)))
    1 * loadBalancing.modifyLoadBalancerAttributes(new ModifyLoadBalancerAttributesRequest(
            loadBalancerName: "kato-main-frontend",
            loadBalancerAttributes: new LoadBalancerAttributes(
                    crossZoneLoadBalancing: new CrossZoneLoadBalancing(enabled: true),
                    additionalAttributes: []
            )
    ))
    0 * _
  }

  @Unroll
  void "should use existing loadbalancer attributes to #desc if not explicitly provided in description"() {
    def existingLoadBalancers = [
      new LoadBalancerDescription(loadBalancerName: "kato-main-frontend")
        .withListenerDescriptions(
        new ListenerDescription().withListener(new Listener(protocol: "HTTP", loadBalancerPort: 80, instanceProtocol: "HTTP", instancePort: 8501))
      )
    ]

    given:
    description.crossZoneBalancing = descriptionCrossZone
    description.connectionDraining = descriptionDraining
    description.deregistrationDelay = descriptionTimeout

    when:
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest(loadBalancerNames: ["kato-main-frontend"])) >>
      new DescribeLoadBalancersResult(loadBalancerDescriptions: existingLoadBalancers)
    1 * loadBalancing.applySecurityGroupsToLoadBalancer(new ApplySecurityGroupsToLoadBalancerRequest(
      loadBalancerName: "kato-main-frontend",
      securityGroups: ["sg-1234"]
    ))
    1 * loadBalancing.configureHealthCheck(new ConfigureHealthCheckRequest(
      loadBalancerName: "kato-main-frontend",
      healthCheck: new HealthCheck(
        target: "HTTP:7001/health",
        interval: 10,
        timeout: 5,
        unhealthyThreshold: 2,
        healthyThreshold: 10
      )
    ))
    1 * loadBalancing.describeLoadBalancerAttributes(new DescribeLoadBalancerAttributesRequest(loadBalancerName: "kato-main-frontend")) >>
      new DescribeLoadBalancerAttributesResult(loadBalancerAttributes:
        new LoadBalancerAttributes(
          crossZoneLoadBalancing: new CrossZoneLoadBalancing(enabled: existingCrossZone),
          connectionDraining: new ConnectionDraining(enabled: existingDraining, timeout: existingTimeout)))
    expectedInv * loadBalancing.modifyLoadBalancerAttributes(new ModifyLoadBalancerAttributesRequest(
      loadBalancerName: "kato-main-frontend",
      loadBalancerAttributes: expectedAttributes))
    0 * _


    where:
    desc                | expectedInv | existingCrossZone | descriptionCrossZone | existingDraining | existingTimeout | descriptionDraining | descriptionTimeout
    "make no changes"   | 0           | true              | null                 | true             | 300             | null                | null
    "enable cross zone" | 1           | false             | true                 | true             | 123             | null                | null
    "enable draining"   | 1           | true              | null                 | false            | 300             | true                | null
    "modify timeout"    | 1           | true              | null                 | false            | 300             | null                | 150

    expectedAttributes = expectedAttributes(existingCrossZone, descriptionCrossZone, existingDraining, existingTimeout, descriptionDraining, descriptionTimeout)
  }

  private LoadBalancerAttributes expectedAttributes(existingCrossZone, descriptionCrossZone, existingDraining, existingTimeout, descriptionDraining, descriptionTimeout) {
    CrossZoneLoadBalancing czlb = null
    if (existingCrossZone != descriptionCrossZone && descriptionCrossZone != null) {
      czlb = new CrossZoneLoadBalancing(enabled:  descriptionCrossZone)
    }

    ConnectionDraining cd = null
    if ((descriptionDraining != null || descriptionTimeout != null) && (existingDraining != descriptionDraining || existingTimeout != descriptionTimeout)) {
      cd = new ConnectionDraining(enabled: [descriptionDraining, existingDraining].findResult(Closure.IDENTITY), timeout: [descriptionTimeout, existingTimeout].findResult(Closure.IDENTITY))
    }
    if (cd == null && czlb == null) {
      return null
    }
    LoadBalancerAttributes lba = new LoadBalancerAttributes().withAdditionalAttributes(Collections.emptyList())
    if (cd != null) {
      lba.setConnectionDraining(cd)
    }
    if (czlb != null) {
      lba.setCrossZoneLoadBalancing(czlb)
    }
    return lba
  }

  void "should attempt to apply all listener modifications regardless of individual failures"() {
    given:
    def existingLoadBalancers = [
      new LoadBalancerDescription(loadBalancerName: "kato-main-frontend")
        .withListenerDescriptions(
        new ListenerDescription().withListener(new Listener(protocol: "HTTP", loadBalancerPort: 80, instanceProtocol: "HTTP", instancePort: 8501))
      )
    ]
    description.listeners.clear()
    description.listeners.add(
      new UpsertAmazonLoadBalancerClassicDescription.Listener(
        externalProtocol: UpsertAmazonLoadBalancerClassicDescription.Listener.ListenerType.TCP,
        externalPort: 22,
        internalPort: 22
      ))
    description.listeners.add(
      new UpsertAmazonLoadBalancerClassicDescription.Listener(
        externalProtocol: UpsertAmazonLoadBalancerClassicDescription.Listener.ListenerType.HTTP,
        externalPort: 80,
        internalPort: 8502
      ))

    when:
    operation.operate([])

    then:
    thrown(AtomicOperationException)

    1 * loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest(loadBalancerNames: ["kato-main-frontend"])) >>
      new DescribeLoadBalancersResult(loadBalancerDescriptions: existingLoadBalancers)
    1 * loadBalancing.createLoadBalancerListeners(new CreateLoadBalancerListenersRequest(
      loadBalancerName: "kato-main-frontend",
      listeners: [ new Listener(protocol: "TCP", loadBalancerPort: 22, instanceProtocol: "TCP", instancePort: 22) ]
    )) >> { throw new AmazonServiceException("AmazonServiceException") }
    1 * loadBalancing.deleteLoadBalancerListeners(new DeleteLoadBalancerListenersRequest(
      loadBalancerName: "kato-main-frontend", loadBalancerPorts: [80]
    ))
    1 * loadBalancing.createLoadBalancerListeners(new CreateLoadBalancerListenersRequest(
      loadBalancerName: "kato-main-frontend",
      listeners: [ new Listener(protocol: "HTTP", loadBalancerPort: 80, instanceProtocol: "HTTP", instancePort: 8502) ]
    ))
    1 * loadBalancing.applySecurityGroupsToLoadBalancer(new ApplySecurityGroupsToLoadBalancerRequest(
      loadBalancerName: "kato-main-frontend",
      securityGroups: ["sg-1234"]
    ))
    1 * loadBalancing.createLoadBalancerListeners(new CreateLoadBalancerListenersRequest(
      loadBalancerName: 'kato-main-frontend',
      listeners: [ new Listener(protocol: 'HTTP', loadBalancerPort: 80, instanceProtocol: 'HTTP', instancePort: 8501) ]
    ))
    0 * _
  }

  void "should respect crossZone balancing directive"() {
    given:
    def loadBalancer = new LoadBalancerDescription(loadBalancerName: "kato-main-frontend")
    "when requesting crossZone to be disabled, we'll turn it off"
    description.crossZoneBalancing = false
    description.vpcId = "vpcId"

    when:
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest(loadBalancerNames: ["kato-main-frontend"])) >>
            new DescribeLoadBalancersResult(loadBalancerDescriptions: [loadBalancer])
    1 * loadBalancing.describeLoadBalancerAttributes(new DescribeLoadBalancerAttributesRequest(loadBalancerName: "kato-main-frontend")) >>
            new DescribeLoadBalancerAttributesResult(loadBalancerAttributes:  new LoadBalancerAttributes(crossZoneLoadBalancing: new CrossZoneLoadBalancing(enabled: true)))
    1 * loadBalancing.modifyLoadBalancerAttributes(_) >> {  ModifyLoadBalancerAttributesRequest request ->
      assert !request.loadBalancerAttributes.crossZoneLoadBalancing.enabled
    }
  }

  void "should handle VPC ELB creation backward compatibility"() {
    given:
    description.subnetType = "internal"
    description.setIsInternal(null)
    description.vpcId = "vpcId"

    when:
    operation.operate([])

    then:
    1 * securityGroupLookup.getSecurityGroupByName('bar', 'kato-elb', 'vpcId') >> Optional.of(elbSecurityGroupUpdater)
    1 * securityGroupLookup.getSecurityGroupByName('bar', 'kato', 'vpcId') >> Optional.of(appSecurityGroupUpdater)
    1 * elbSecurityGroupUpdater.getSecurityGroup() >> elbSecurityGroup
    1 * appSecurityGroupUpdater.getSecurityGroup() >> applicationSecurityGroup
    1 * appSecurityGroupUpdater.addIngress(_)

    and:
    1 * loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest(loadBalancerNames: ["kato-main-frontend"])) >> null
    1 * loadBalancing.createLoadBalancer(new CreateLoadBalancerRequest(
            loadBalancerName: "kato-main-frontend",
            listeners: [
                    new Listener(protocol: "HTTP", loadBalancerPort: 80, instanceProtocol: "HTTP", instancePort: 8501)
            ],
            subnets: ["subnet1"],
            securityGroups: ["sg-1234"],
            tags: [],
            scheme: "internal"
    )) >> new CreateLoadBalancerResult(dNSName: "dnsName1")
    1 * mockSubnetAnalyzer.getSubnetIdsForZones(["us-east-1a"], "internal", SubnetTarget.ELB, 1) >> ["subnet1"]
  }

  void "should handle VPC ELB creation"() {
    given:
    description.subnetType = "internal"
    description.setIsInternal(true)
    description.vpcId = "vpcId"

    when:
    operation.operate([])

    then:
    1 * securityGroupLookup.getSecurityGroupByName('bar', 'kato-elb', 'vpcId') >> Optional.of(elbSecurityGroupUpdater)
    1 * securityGroupLookup.getSecurityGroupByName('bar', 'kato', 'vpcId') >> Optional.of(appSecurityGroupUpdater)
    1 * elbSecurityGroupUpdater.getSecurityGroup() >> elbSecurityGroup
    1 * appSecurityGroupUpdater.getSecurityGroup() >> applicationSecurityGroup
    1 * appSecurityGroupUpdater.addIngress(_)

    and:
    1 * loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest(loadBalancerNames: ["kato-main-frontend"])) >> null
    1 * loadBalancing.createLoadBalancer(new CreateLoadBalancerRequest(
            loadBalancerName: "kato-main-frontend",
            listeners: [
                    new Listener(protocol: "HTTP", loadBalancerPort: 80, instanceProtocol: "HTTP", instancePort: 8501)
            ],
            subnets: ["subnet1"],
            securityGroups: ["sg-1234"],
            tags: [],
            scheme: "internal"
    )) >> new CreateLoadBalancerResult(dNSName: "dnsName1")
    1 * mockSubnetAnalyzer.getSubnetIdsForZones(["us-east-1a"], "internal", SubnetTarget.ELB, 1) >> ["subnet1"]
  }

  void "should use clusterName if name not provided"() {
    given:
    description.clusterName = "kato-test"
    description.name = null
    description.vpcId = "vpcId"

    when:
    operation.operate([])

    then:
    1 * securityGroupLookup.getSecurityGroupByName('bar', 'kato-elb', 'vpcId') >> Optional.of(elbSecurityGroupUpdater)
    1 * securityGroupLookup.getSecurityGroupByName('bar', 'kato', 'vpcId') >> Optional.of(appSecurityGroupUpdater)
    1 * elbSecurityGroupUpdater.getSecurityGroup() >> elbSecurityGroup
    1 * appSecurityGroupUpdater.getSecurityGroup() >> applicationSecurityGroup
    1 * appSecurityGroupUpdater.addIngress(_)

    and:
    1 * loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest(loadBalancerNames: ["kato-test-frontend"])) >>
            new DescribeLoadBalancersResult(loadBalancerDescriptions: [])
    1 * loadBalancing.createLoadBalancer() { createLoadBalancerRequest ->
      createLoadBalancerRequest.loadBalancerName == "kato-test-frontend"
    } >> new CreateLoadBalancerResult(dNSName: "dnsName1")
  }

  void "should reset existing listeners on a load balancer that already exists"() {
    given:
    def listener = new ListenerDescription().withListener(new Listener("HTTP", 111, 80))
    def loadBalancer = new LoadBalancerDescription(listenerDescriptions: [listener])
    description.vpcId = "vpcId"

    when:
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(_) >> new DescribeLoadBalancersResult(loadBalancerDescriptions: [loadBalancer])
    1 * loadBalancing.describeLoadBalancerAttributes(_) >> new DescribeLoadBalancerAttributesResult()
    1 * loadBalancing.deleteLoadBalancerListeners(new DeleteLoadBalancerListenersRequest(loadBalancerPorts: [111]))
    1 * loadBalancing.createLoadBalancerListeners(new CreateLoadBalancerListenersRequest(
            listeners: [ new Listener(loadBalancerPort: 80, instancePort: 8501, protocol: "HTTP", instanceProtocol: "HTTP") ]
    ))
  }

  void "should ignore the old listener of pre-2012 ELBs"() {
    given:
    def oldListener = new ListenerDescription().withListener(new Listener(null, 0, 0))
    def listener = new ListenerDescription().withListener(new Listener("HTTP", 111, 80))
    def loadBalancer = new LoadBalancerDescription(listenerDescriptions: [oldListener, listener])
    description.vpcId = "vpcId"

    when:
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(_) >> new DescribeLoadBalancersResult(loadBalancerDescriptions: [loadBalancer])
    1 * loadBalancing.describeLoadBalancerAttributes(_) >> new DescribeLoadBalancerAttributesResult()
    1 * loadBalancing.deleteLoadBalancerListeners(new DeleteLoadBalancerListenersRequest(loadBalancerPorts: [111]))
    0 * loadBalancing.deleteLoadBalancerListeners(_)
    1 * loadBalancing.createLoadBalancerListeners(new CreateLoadBalancerListenersRequest(
      listeners: [ new Listener(loadBalancerPort: 80, instancePort: 8501, protocol: "HTTP", instanceProtocol: "HTTP") ]
    ))
    0 * loadBalancing.createLoadBalancerListeners(_)
  }

  void "should permit ingress from application elb security group to application security group"() {
    given: 'an application load balancer'
    def applicationName = "foo"
    description.name = applicationName
    description.application = applicationName
    description.securityGroups = []
    description.vpcId = "vpcId"
    description.listeners = [
      new UpsertAmazonLoadBalancerClassicDescription.Listener(
        externalPort: 80,
        externalProtocol: "HTTP",
        internalPort: 7001,
        internalProtocol: "HTTP"
      )
    ]

    elbSecurityGroupUpdater.getSecurityGroup() >> elbSecurityGroup
    appSecurityGroupUpdater.getSecurityGroup() >> applicationSecurityGroup
    securityGroupLookup.getSecurityGroupByName(
      description.credentialAccount,
      applicationName + "-elb",
      description.vpcId
    ) >> Optional.of(elbSecurityGroupUpdater)

    securityGroupLookup.getSecurityGroupByName(
      description.credentialAccount,
      applicationName,
      description.vpcId
    ) >> Optional.of(appSecurityGroupUpdater)

    when:
    operation.operate([])

    then:
    1 * appSecurityGroupUpdater.addIngress(_) >> {
      def permissions = it[0] as List<IpPermission>
      assert permissions.size() == 1
      assert permissions[0].fromPort == 7001 && permissions[0].toPort == 7001
      assert elbSecurityGroup.groupId in permissions[0].userIdGroupPairs*.groupId
    }

    1 * loadBalancing.createLoadBalancer(_ as CreateLoadBalancerRequest) >> new CreateLoadBalancerResult(dNSName: 'dnsName1')
  }

  void "should auto-create application load balancer security group"() {
    given: "an elb with a healthCheck port"
    description.securityGroups = []
    description.vpcId = "vpcId"

    when:
    operation.operate([])

    then: "an application elb group should be created and ingressed properly"
    1 * securityGroupLookup.getSecurityGroupByName('bar', 'kato-elb', 'vpcId') >> Optional.empty()
    1 * securityGroupLookup.createSecurityGroup(_) >> elbSecurityGroupUpdater
    1 * securityGroupLookup.getSecurityGroupByName('bar', 'kato', 'vpcId') >> Optional.of(appSecurityGroupUpdater)
    1 * elbSecurityGroupUpdater.getSecurityGroup() >> elbSecurityGroup
    1 * appSecurityGroupUpdater.getSecurityGroup() >> applicationSecurityGroup
    1 * appSecurityGroupUpdater.addIngress(_) >> {
      def permissions = it[0] as List<IpPermission>
      assert permissions.size() == 2
      assert permissions*.fromPort == [8501, 7001] && permissions*.toPort == [8501, 7001]
      assert elbSecurityGroup.groupId in permissions[0].userIdGroupPairs*.groupId
      assert elbSecurityGroup.groupId in permissions[1].userIdGroupPairs*.groupId
    }

    1 * loadBalancing.createLoadBalancer(_ as CreateLoadBalancerRequest) >> new CreateLoadBalancerResult(dNSName: 'dnsName1')
  }

  void "should auto-create application load balancer and application security groups"() {
    given:
    description.securityGroups = []
    description.vpcId = "vpcId"

    when:
    operation.operate([])

    then:
    1 * securityGroupLookup.getSecurityGroupByName('bar', 'kato-elb', 'vpcId') >> Optional.empty()
    1 * securityGroupLookup.getSecurityGroupByName('bar', 'kato', 'vpcId') >> Optional.empty()
    1 * securityGroupLookup.createSecurityGroup( { it.name == 'kato-elb'}) >> elbSecurityGroupUpdater
    1 * securityGroupLookup.createSecurityGroup( { it.name == 'kato'}) >> appSecurityGroupUpdater
    1 * elbSecurityGroupUpdater.getSecurityGroup() >> elbSecurityGroup
    1 * appSecurityGroupUpdater.getSecurityGroup() >> applicationSecurityGroup
    1 * appSecurityGroupUpdater.addIngress(_) >> {
      def permissions = it[0] as List<IpPermission>
      assert permissions.size() == 2
      assert permissions*.fromPort == [8501, 7001] && permissions*.toPort == [8501, 7001]
      assert elbSecurityGroup.groupId in permissions[0].userIdGroupPairs*.groupId
      assert elbSecurityGroup.groupId in permissions[1].userIdGroupPairs*.groupId
    }

    1 * loadBalancing.createLoadBalancer(_ as CreateLoadBalancerRequest) >> new CreateLoadBalancerResult(dNSName: 'dnsName1')
  }

  @Unroll
  void "should enable AWS Shield protection if external ELB"() {
    given:
    description.credentials = TestCredential.named('bar', [shieldEnabled: shieldEnabled])
    description.shieldProtectionEnabled = descriptionOverride
    description.vpcId = "vpcId"

    when:
    operation.operate([])

    then:
    1 * securityGroupLookup.getSecurityGroupByName('bar', 'kato-elb', 'vpcId') >> Optional.of(elbSecurityGroupUpdater)
    1 * securityGroupLookup.getSecurityGroupByName('bar', 'kato', 'vpcId') >> Optional.of(appSecurityGroupUpdater)
    1 * elbSecurityGroupUpdater.getSecurityGroup() >> elbSecurityGroup
    1 * appSecurityGroupUpdater.getSecurityGroup() >> applicationSecurityGroup

    1 * loadBalancing.createLoadBalancer(_ as CreateLoadBalancerRequest) >> new CreateLoadBalancerResult(dNSName: 'dnsName1')
    (shouldProtect ? 1 : 0) * awsShield.createProtection(new CreateProtectionRequest(
      name: 'kato-main-frontend',
      resourceArn: 'arn:aws:elasticloadbalancing:123456789012bar:us-east-1:loadbalancer/kato-main-frontend'
    ))

    where:
    shieldEnabled | descriptionOverride || shouldProtect
    false         | false               || false
    false         | true                || false
    true          | false               || false
    true          | true                || true
  }
}
