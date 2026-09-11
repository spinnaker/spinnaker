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

import software.amazon.awssdk.awscore.exception.AwsErrorDetails
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.services.elasticloadbalancing.ElasticLoadBalancingClient
import software.amazon.awssdk.services.elasticloadbalancing.model.*
import software.amazon.awssdk.services.shield.ShieldClient
import software.amazon.awssdk.services.shield.model.CreateProtectionRequest
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerClassicDescription
import com.netflix.spinnaker.clouddriver.aws.model.SubnetAnalyzer
import com.netflix.spinnaker.clouddriver.aws.model.SubnetTarget
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.aws.services.SecurityGroupService
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException
import com.netflix.spinnaker.config.AwsConfiguration
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

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
  ElasticLoadBalancingClient loadBalancing = Mock(ElasticLoadBalancingClient)
  ShieldClient awsShield = Mock(ShieldClient)
  def mockAmazonClientProvider = Stub(AmazonClientProvider) {
    getAmazonElasticLoadBalancingClassicV2(_, _) >> loadBalancing
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

  @Subject operation = new UpsertAmazonLoadBalancerAtomicOperation(description)

  def setup() {
    operation.deployDefaults = new AwsConfiguration.DeployDefaults(addAppGroupToServerGroup: true, createLoadBalancerIngressPermissions: true)
    operation.amazonClientProvider = mockAmazonClientProvider
    operation.regionScopedProviderFactory = regionScopedProviderFactory
    operation.ingressLoadBalancerBuilder = ingressLoadBalancerBuilder
  }

  void "should create load balancer"() {
    given:
    def existingLoadBalancers = []
    description.vpcId = "vpcId"

    when:
    description.subnetType = 'internal'
    operation.operate([])

    then:
    1 * ingressLoadBalancerBuilder.ingressApplicationLoadBalancerGroup(
      'kato',
      'us-east-1',
      'bar',
      description.credentials,
      "vpcId",
      { it.toList().sort() == [7001, 8501] },
      _) >> new IngressLoadBalancerBuilder.IngressLoadBalancerGroupResult("sg-1234", "kato-elb")

    and:
    1 * mockSubnetAnalyzer.getSubnetIdsForZones(['us-east-1a'], 'internal', SubnetTarget.ELB, 1) >> ["subnet-1"]
    1 * loadBalancing.describeLoadBalancers(DescribeLoadBalancersRequest.builder().loadBalancerNames(["kato-main-frontend"]).build()) >>
            DescribeLoadBalancersResponse.builder().loadBalancerDescriptions(existingLoadBalancers).build()
    1 * loadBalancing.createLoadBalancer(CreateLoadBalancerRequest.builder()
            .loadBalancerName("kato-main-frontend")
            .listeners(
                    Listener.builder().protocol("HTTP").loadBalancerPort(80).instanceProtocol("HTTP").instancePort(8501).build()
            )
            .subnets(["subnet-1"])
            .securityGroups(["sg-1234"])
            .scheme("internal")
            .build()) >> CreateLoadBalancerResponse.builder().dnsName("dnsName1").build()
    1 * loadBalancing.configureHealthCheck(ConfigureHealthCheckRequest.builder()
            .loadBalancerName("kato-main-frontend")
            .healthCheck(HealthCheck.builder()
                    .target("HTTP:7001/health")
                    .interval(10)
                    .timeout(5)
                    .unhealthyThreshold(2)
                    .healthyThreshold(10)
                    .build())
            .build())
    1 * loadBalancing.modifyLoadBalancerAttributes(ModifyLoadBalancerAttributesRequest.builder()
            .loadBalancerName("kato-main-frontend")
            .loadBalancerAttributes(LoadBalancerAttributes.builder()
                    .crossZoneLoadBalancing(CrossZoneLoadBalancing.builder().enabled(true).build())
                    .connectionDraining(ConnectionDraining.builder().enabled(false).build())
                    .connectionSettings(ConnectionSettings.builder().idleTimeout(60).build())
                    .build())
            .build())
    0 * _
  }

  void "should fail updating a load balancer with no security groups in VPC"() {
    given:
    def existingLoadBalancers = [
      LoadBalancerDescription.builder().loadBalancerName("kato-main-frontend").vpcId("test-vpc").listenerDescriptions(
        ListenerDescription.builder().listener(Listener.builder().protocol("HTTP").loadBalancerPort(80).instanceProtocol("HTTP").instancePort(8501).build()).build()
      ).build()
    ]

    and:
    loadBalancing.describeLoadBalancers(
      DescribeLoadBalancersRequest.builder().loadBalancerNames(["kato-main-frontend"]).build()
    ) >> DescribeLoadBalancersResponse.builder().loadBalancerDescriptions(existingLoadBalancers).build()

    and: 'auto-creating groups fails'
    description.securityGroups = []
    description.vpcId = "vpcId"

    when:
    operation.operate([])

    then:
    thrown(IllegalArgumentException)

    when: "in EC2 classic"
    existingLoadBalancers = [
      LoadBalancerDescription.builder().loadBalancerName("kato-main-frontend").listenerDescriptions(
        ListenerDescription.builder().listener(Listener.builder().protocol("HTTP").loadBalancerPort(80).instanceProtocol("HTTP").instancePort(8501).build()).build()
      ).build()
    ]

    and:
    loadBalancing.describeLoadBalancers(
      DescribeLoadBalancersRequest.builder().loadBalancerNames(["kato-main-frontend"]).build()
    ) >> DescribeLoadBalancersResponse.builder().loadBalancerDescriptions(existingLoadBalancers).build()

    then:
    notThrown(IllegalArgumentException)
  }

  void "should update existing load balancer"() {
    def existingLoadBalancers = [
      LoadBalancerDescription.builder().loadBalancerName("kato-main-frontend")
        .listenerDescriptions(
        ListenerDescription.builder().listener(Listener.builder().protocol("HTTP").loadBalancerPort(80).instanceProtocol("HTTP").instancePort(8501).build()).build()
      ).build()
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

    then:
    1 * loadBalancing.describeLoadBalancers(DescribeLoadBalancersRequest.builder().loadBalancerNames(["kato-main-frontend"]).build()) >>
            DescribeLoadBalancersResponse.builder().loadBalancerDescriptions(existingLoadBalancers).build()
    1 * loadBalancing.createLoadBalancerListeners(CreateLoadBalancerListenersRequest.builder()
            .loadBalancerName("kato-main-frontend")
            .listeners(Listener.builder().protocol("HTTP").loadBalancerPort(8080).instanceProtocol("HTTP").instancePort(8080).build())
            .build())
    1 * loadBalancing.applySecurityGroupsToLoadBalancer(ApplySecurityGroupsToLoadBalancerRequest.builder()
            .loadBalancerName("kato-main-frontend")
            .securityGroups(["sg-1234"])
            .build())
    1 * loadBalancing.configureHealthCheck(ConfigureHealthCheckRequest.builder()
            .loadBalancerName("kato-main-frontend")
            .healthCheck(HealthCheck.builder()
                    .target("HTTP:7001/health")
                    .interval(10)
                    .timeout(5)
                    .unhealthyThreshold(2)
                    .healthyThreshold(10)
                    .build())
            .build())
    1 * loadBalancing.describeLoadBalancerAttributes(DescribeLoadBalancerAttributesRequest.builder().loadBalancerName("kato-main-frontend").build()) >>
            DescribeLoadBalancerAttributesResponse.builder().loadBalancerAttributes(
              LoadBalancerAttributes.builder()
                .crossZoneLoadBalancing(CrossZoneLoadBalancing.builder().enabled(false).build())
                .connectionDraining(ConnectionDraining.builder().enabled(false).build())
                .build()).build()
    1 * loadBalancing.modifyLoadBalancerAttributes(ModifyLoadBalancerAttributesRequest.builder()
            .loadBalancerName("kato-main-frontend")
            .loadBalancerAttributes(LoadBalancerAttributes.builder()
                    .crossZoneLoadBalancing(CrossZoneLoadBalancing.builder().enabled(true).build())
                    .connectionSettings(ConnectionSettings.builder().idleTimeout(60).build())
                    .build())
            .build())
    0 * _
  }

  @Unroll
  void "should use existing loadbalancer attributes to #desc if not explicitly provided in description"() {
    def existingLoadBalancers = [
      LoadBalancerDescription.builder().loadBalancerName("kato-main-frontend")
        .listenerDescriptions(
        ListenerDescription.builder().listener(Listener.builder().protocol("HTTP").loadBalancerPort(80).instanceProtocol("HTTP").instancePort(8501).build()).build()
      ).build()
    ]

    given:
    description.crossZoneBalancing = descriptionCrossZone
    description.connectionDraining = descriptionDraining
    description.deregistrationDelay = descriptionTimeout

    when:
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(DescribeLoadBalancersRequest.builder().loadBalancerNames(["kato-main-frontend"]).build()) >>
      DescribeLoadBalancersResponse.builder().loadBalancerDescriptions(existingLoadBalancers).build()
    1 * loadBalancing.applySecurityGroupsToLoadBalancer(ApplySecurityGroupsToLoadBalancerRequest.builder()
      .loadBalancerName("kato-main-frontend")
      .securityGroups(["sg-1234"])
      .build())
    1 * loadBalancing.configureHealthCheck(ConfigureHealthCheckRequest.builder()
      .loadBalancerName("kato-main-frontend")
      .healthCheck(HealthCheck.builder()
        .target("HTTP:7001/health")
        .interval(10)
        .timeout(5)
        .unhealthyThreshold(2)
        .healthyThreshold(10)
        .build())
      .build())
    1 * loadBalancing.describeLoadBalancerAttributes(DescribeLoadBalancerAttributesRequest.builder().loadBalancerName("kato-main-frontend").build()) >>
      DescribeLoadBalancerAttributesResponse.builder().loadBalancerAttributes(
        LoadBalancerAttributes.builder()
          .crossZoneLoadBalancing(CrossZoneLoadBalancing.builder().enabled(existingCrossZone).build())
          .connectionDraining(ConnectionDraining.builder().enabled(existingDraining).timeout(existingTimeout).build())
          .connectionSettings(ConnectionSettings.builder().idleTimeout(existingIdleTimeout).build())
          .build()).build()
    expectedInv * loadBalancing.modifyLoadBalancerAttributes(ModifyLoadBalancerAttributesRequest.builder()
      .loadBalancerName("kato-main-frontend")
      .loadBalancerAttributes(expectedAttributes as LoadBalancerAttributes)
      .build())
    0 * _


    where:
    desc                  | expectedInv | existingCrossZone | descriptionCrossZone | existingDraining | existingTimeout | descriptionDraining | descriptionTimeout | existingIdleTimeout | descriptionIdleTimeout
    "make no changes"     | 0           | true              | null                 | true             | 300             | null                | null               | 60                  | 60
    "enable cross zone"   | 1           | false             | true                 | true             | 123             | null                | null               | 60                  | 60
    "enable draining"     | 1           | true              | null                 | false            | 300             | true                | null               | 60                  | 60
    "modify timeout"      | 1           | true              | null                 | false            | 300             | null                | 150                | 60                  | 60
    "modify idle timeout" | 0           | true              | null                 | true             | 300             | null                | null               | 60                  | 120

    expectedAttributes = expectedAttributes(existingCrossZone, descriptionCrossZone, existingDraining, existingTimeout, descriptionDraining, descriptionTimeout, existingIdleTimeout, descriptionIdleTimeout)
  }

  private LoadBalancerAttributes expectedAttributes(existingCrossZone, descriptionCrossZone, existingDraining, existingTimeout, descriptionDraining, descriptionTimeout, existingIdleTimeout, descriptionIdleTimeout) {
    CrossZoneLoadBalancing czlb = null
    if (existingCrossZone != descriptionCrossZone && descriptionCrossZone != null) {
      czlb = CrossZoneLoadBalancing.builder().enabled(descriptionCrossZone).build()
    }
    ConnectionSettings cs = null
    if (existingIdleTimeout != descriptionIdleTimeout) {
      cs = ConnectionSettings.builder().idleTimeout(descriptionIdleTimeout).build()
    }
    ConnectionDraining cd = null
    if ((descriptionDraining != null || descriptionTimeout != null) && (existingDraining != descriptionDraining || existingTimeout != descriptionTimeout)) {
      cd = ConnectionDraining.builder().enabled([descriptionDraining, existingDraining].findResult(Closure.IDENTITY)).timeout([descriptionTimeout, existingTimeout].findResult(Closure.IDENTITY)).build()
    }
    if (cd == null && czlb == null) {
      return null
    }
    def lbaBuilder = LoadBalancerAttributes.builder()
    if (cd != null) {
      lbaBuilder.connectionDraining(cd)
    }
    if (czlb != null) {
      lbaBuilder.crossZoneLoadBalancing(czlb)
    }
    if (cs != null) {
      lbaBuilder.connectionSettings(cs)
    }
    return lbaBuilder.build()
  }

  void "should restore listener policies when updating an existing load balancer"() {
    given:
    def httpListener = Listener.builder().protocol("HTTP").loadBalancerPort(80).instanceProtocol("HTTP").instancePort(8502).build()
    def httpsListener = Listener.builder().protocol("HTTPS").loadBalancerPort(443).instanceProtocol("HTTP").instancePort(7001).sslCertificateId("foo").build()
    def policies = ["cookiePolicy"]

    def existingLB = LoadBalancerDescription.builder()
      .loadBalancerName("kato-main-frontend")
      .listenerDescriptions([
        ListenerDescription.builder().listener(httpListener).build(),
        ListenerDescription.builder().listener(httpsListener).policyNames(policies).build()
      ])
      .build()

    and:
    description.subnetType = "internal"
    description.setIsInternal(true)
    description.vpcId = "vpcId"

    // request listeners
    description.listeners.clear()
    description.listeners.addAll(
      [
        new UpsertAmazonLoadBalancerClassicDescription.Listener(
          externalProtocol: UpsertAmazonLoadBalancerClassicDescription.Listener.ListenerType.HTTP,
          externalPort: httpListener.loadBalancerPort(),
          internalPort: httpListener.instancePort()
        ),
        new UpsertAmazonLoadBalancerClassicDescription.Listener(
          externalProtocol: UpsertAmazonLoadBalancerClassicDescription.Listener.ListenerType.HTTPS,
          externalPort: httpsListener.loadBalancerPort(),
          internalPort: httpsListener.instancePort(),
          sslCertificateId: "bar" //updated cert on listener
        )
    ])

    when:
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(_) >> DescribeLoadBalancersResponse.builder().loadBalancerDescriptions([existingLB]).build()
    1 * loadBalancing.describeLoadBalancerAttributes(_) >> DescribeLoadBalancerAttributesResponse.builder().build()
    1 * loadBalancing.deleteLoadBalancerListeners({
      it.loadBalancerPorts() == [httpsListener.loadBalancerPort()]
    } as DeleteLoadBalancerListenersRequest)

    1 * loadBalancing.createLoadBalancerListeners(*_) >> { args ->
      def request = args[0] as CreateLoadBalancerListenersRequest
      assert request.loadBalancerName() == description.name
      assert request.listeners().size() == 1
      assert request.listeners()*.loadBalancerPort() == [ httpsListener.loadBalancerPort() ]
    }

    1 * loadBalancing.configureHealthCheck(ConfigureHealthCheckRequest.builder()
      .loadBalancerName("kato-main-frontend")
      .healthCheck(HealthCheck.builder()
        .target("HTTP:7001/health")
        .interval(10)
        .timeout(5)
        .unhealthyThreshold(2)
        .healthyThreshold(10)
        .build())
      .build())

    1 * loadBalancing.modifyLoadBalancerAttributes(ModifyLoadBalancerAttributesRequest.builder()
      .loadBalancerName("kato-main-frontend")
      .loadBalancerAttributes(LoadBalancerAttributes.builder()
        .crossZoneLoadBalancing(CrossZoneLoadBalancing.builder().enabled(true).build())
        .connectionDraining(ConnectionDraining.builder().enabled(false).build())
        .connectionSettings(ConnectionSettings.builder().idleTimeout(60).build())
        .build())
      .build())

    1 * loadBalancing.setLoadBalancerPoliciesOfListener(*_) >> { args ->
      def request = args[0] as SetLoadBalancerPoliciesOfListenerRequest
      assert request.loadBalancerName() == description.name
      assert request.policyNames() == policies
      assert request.loadBalancerPort() == httpsListener.loadBalancerPort()
    }
  }

  void "should attempt to apply all listener modifications regardless of individual failures"() {
    given:
    def existingLoadBalancers = [
      LoadBalancerDescription.builder().loadBalancerName("kato-main-frontend")
        .listenerDescriptions(
        ListenerDescription.builder().listener(Listener.builder().protocol("HTTP").loadBalancerPort(80).instanceProtocol("HTTP").instancePort(8501).build()).build()
      ).build()
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

    1 * loadBalancing.describeLoadBalancers(DescribeLoadBalancersRequest.builder().loadBalancerNames(["kato-main-frontend"]).build()) >>
      DescribeLoadBalancersResponse.builder().loadBalancerDescriptions(existingLoadBalancers).build()
    1 * loadBalancing.createLoadBalancerListeners(CreateLoadBalancerListenersRequest.builder()
      .loadBalancerName("kato-main-frontend")
      .listeners(Listener.builder().protocol("TCP").loadBalancerPort(22).instanceProtocol("TCP").instancePort(22).build())
      .build()) >> {
        throw AwsServiceException.builder()
          .message("AmazonServiceException")
          .awsErrorDetails(AwsErrorDetails.builder().errorMessage("AmazonServiceException").build())
          .build()
      }
    1 * loadBalancing.deleteLoadBalancerListeners(DeleteLoadBalancerListenersRequest.builder()
      .loadBalancerName("kato-main-frontend").loadBalancerPorts([80]).build()
    )
    1 * loadBalancing.createLoadBalancerListeners(CreateLoadBalancerListenersRequest.builder()
      .loadBalancerName("kato-main-frontend")
      .listeners(Listener.builder().protocol("HTTP").loadBalancerPort(80).instanceProtocol("HTTP").instancePort(8502).build())
      .build())
    1 * loadBalancing.applySecurityGroupsToLoadBalancer(ApplySecurityGroupsToLoadBalancerRequest.builder()
      .loadBalancerName("kato-main-frontend")
      .securityGroups(["sg-1234"])
      .build())
    1 * loadBalancing.createLoadBalancerListeners(CreateLoadBalancerListenersRequest.builder()
      .loadBalancerName('kato-main-frontend')
      .listeners(Listener.builder().protocol('HTTP').loadBalancerPort(80).instanceProtocol('HTTP').instancePort(8501).build())
      .build())
    0 * _
  }

  void "should respect crossZone balancing directive"() {
    given:
    def loadBalancer = LoadBalancerDescription.builder().loadBalancerName("kato-main-frontend").build()
    "when requesting crossZone to be disabled, we'll turn it off"
    description.crossZoneBalancing = false
    description.vpcId = "vpcId"

    when:
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(DescribeLoadBalancersRequest.builder().loadBalancerNames(["kato-main-frontend"]).build()) >>
            DescribeLoadBalancersResponse.builder().loadBalancerDescriptions([loadBalancer]).build()
    1 * loadBalancing.describeLoadBalancerAttributes(DescribeLoadBalancerAttributesRequest.builder().loadBalancerName("kato-main-frontend").build()) >>
            DescribeLoadBalancerAttributesResponse.builder().loadBalancerAttributes(LoadBalancerAttributes.builder().crossZoneLoadBalancing(CrossZoneLoadBalancing.builder().enabled(true).build()).build()).build()
    1 * loadBalancing.modifyLoadBalancerAttributes(_) >> {  ModifyLoadBalancerAttributesRequest request ->
      assert !request.loadBalancerAttributes().crossZoneLoadBalancing().enabled()
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
    1 * ingressLoadBalancerBuilder.ingressApplicationLoadBalancerGroup(_, _, _, _, _, _, _) >> new IngressLoadBalancerBuilder.IngressLoadBalancerGroupResult("sg-1234", "kato-elb")

    and:
    1 * loadBalancing.describeLoadBalancers(DescribeLoadBalancersRequest.builder().loadBalancerNames(["kato-main-frontend"]).build()) >> null
    1 * loadBalancing.createLoadBalancer(CreateLoadBalancerRequest.builder()
            .loadBalancerName("kato-main-frontend")
            .listeners(
                    Listener.builder().protocol("HTTP").loadBalancerPort(80).instanceProtocol("HTTP").instancePort(8501).build()
            )
            .subnets(["subnet1"])
            .securityGroups(["sg-1234"])
            .scheme("internal")
            .build()) >> CreateLoadBalancerResponse.builder().dnsName("dnsName1").build()
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
    1 * ingressLoadBalancerBuilder.ingressApplicationLoadBalancerGroup(_, _, _, _, _, _, _) >> new IngressLoadBalancerBuilder.IngressLoadBalancerGroupResult("sg-1234", "kato-elb")

    and:
    1 * loadBalancing.describeLoadBalancers(DescribeLoadBalancersRequest.builder().loadBalancerNames(["kato-main-frontend"]).build()) >> null
    1 * loadBalancing.createLoadBalancer(CreateLoadBalancerRequest.builder()
            .loadBalancerName("kato-main-frontend")
            .listeners(
                    Listener.builder().protocol("HTTP").loadBalancerPort(80).instanceProtocol("HTTP").instancePort(8501).build()
            )
            .subnets(["subnet1"])
            .securityGroups(["sg-1234"])
            .scheme("internal")
            .build()) >> CreateLoadBalancerResponse.builder().dnsName("dnsName1").build()
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
    1 * ingressLoadBalancerBuilder.ingressApplicationLoadBalancerGroup(_, _, _, _, _, _, _) >> new IngressLoadBalancerBuilder.IngressLoadBalancerGroupResult("sg-1234", "kato-elb")

    and:
    1 * loadBalancing.describeLoadBalancers(DescribeLoadBalancersRequest.builder().loadBalancerNames(["kato-test-frontend"]).build()) >>
            DescribeLoadBalancersResponse.builder().loadBalancerDescriptions([]).build()
    1 * loadBalancing.createLoadBalancer(_ as CreateLoadBalancerRequest) >> { CreateLoadBalancerRequest createLoadBalancerRequest ->
      assert createLoadBalancerRequest.loadBalancerName() == "kato-test-frontend"
      CreateLoadBalancerResponse.builder().dnsName("dnsName1").build()
    }
  }

  void "should reset existing listeners on a load balancer that already exists"() {
    given:
    def listener = ListenerDescription.builder().listener(Listener.builder().protocol("HTTP").loadBalancerPort(111).instancePort(80).build()).build()
    def loadBalancer = LoadBalancerDescription.builder().listenerDescriptions([listener]).build()
    description.vpcId = "vpcId"

    when:
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(_) >> DescribeLoadBalancersResponse.builder().loadBalancerDescriptions([loadBalancer]).build()
    1 * loadBalancing.describeLoadBalancerAttributes(_) >> DescribeLoadBalancerAttributesResponse.builder().build()
    1 * loadBalancing.deleteLoadBalancerListeners(DeleteLoadBalancerListenersRequest.builder().loadBalancerPorts([111]).build())
    1 * loadBalancing.createLoadBalancerListeners(CreateLoadBalancerListenersRequest.builder()
            .listeners(Listener.builder().loadBalancerPort(80).instancePort(8501).protocol("HTTP").instanceProtocol("HTTP").build())
            .build())
  }

  void "should ignore the old listener of pre-2012 ELBs"() {
    given:
    def oldListener = ListenerDescription.builder().listener(Listener.builder().loadBalancerPort(0).instancePort(0).build()).build()
    def listener = ListenerDescription.builder().listener(Listener.builder().protocol("HTTP").loadBalancerPort(111).instancePort(80).build()).build()
    def loadBalancer = LoadBalancerDescription.builder().listenerDescriptions([oldListener, listener]).build()
    description.vpcId = "vpcId"

    when:
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(_) >> DescribeLoadBalancersResponse.builder().loadBalancerDescriptions([loadBalancer]).build()
    1 * loadBalancing.describeLoadBalancerAttributes(_) >> DescribeLoadBalancerAttributesResponse.builder().build()
    1 * loadBalancing.deleteLoadBalancerListeners(DeleteLoadBalancerListenersRequest.builder().loadBalancerPorts([111]).build())
    0 * loadBalancing.deleteLoadBalancerListeners(_)
    1 * loadBalancing.createLoadBalancerListeners(CreateLoadBalancerListenersRequest.builder()
      .listeners(Listener.builder().loadBalancerPort(80).instancePort(8501).protocol("HTTP").instanceProtocol("HTTP").build())
      .build())
    0 * loadBalancing.createLoadBalancerListeners(_)
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
    1 * ingressLoadBalancerBuilder.ingressApplicationLoadBalancerGroup(_, _, _, _, _, _, _) >> new IngressLoadBalancerBuilder.IngressLoadBalancerGroupResult("sg-1234", "kato-elb")

    1 * loadBalancing.createLoadBalancer(_ as CreateLoadBalancerRequest) >> CreateLoadBalancerResponse.builder().dnsName('dnsName1').build()
    (shouldProtect ? 1 : 0) * awsShield.createProtection(CreateProtectionRequest.builder()
      .name('kato-main-frontend')
      .resourceArn('arn:aws:elasticloadbalancing:123456789012bar:us-east-1:loadbalancer/kato-main-frontend')
      .build())

    where:
    shieldEnabled | descriptionOverride || shouldProtect
    false         | false               || false
    false         | true                || false
    true          | false               || false
    true          | true                || true
  }
}
