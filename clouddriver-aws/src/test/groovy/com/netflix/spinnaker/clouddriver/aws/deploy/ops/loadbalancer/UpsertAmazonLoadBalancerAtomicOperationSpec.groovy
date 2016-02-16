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
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.ApplySecurityGroupsToLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.ConfigureHealthCheckRequest
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerListenersRequest
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerResult
import com.amazonaws.services.elasticloadbalancing.model.CrossZoneLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerListenersRequest
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult
import com.amazonaws.services.elasticloadbalancing.model.HealthCheck
import com.amazonaws.services.elasticloadbalancing.model.Listener
import com.amazonaws.services.elasticloadbalancing.model.ListenerDescription
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerAttributes
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.amazonaws.services.elasticloadbalancing.model.ModifyLoadBalancerAttributesRequest
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.aws.model.SubnetAnalyzer
import com.netflix.spinnaker.clouddriver.aws.model.SubnetTarget
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.aws.services.SecurityGroupService
import spock.lang.Specification
import spock.lang.Subject

class UpsertAmazonLoadBalancerAtomicOperationSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  UpsertAmazonLoadBalancerDescription description = new UpsertAmazonLoadBalancerDescription(
          name: "kato-main-frontend",
          availabilityZones: ["us-east-1": ["us-east-1a"]],
          listeners: [
                  new UpsertAmazonLoadBalancerDescription.Listener(
                          externalProtocol: UpsertAmazonLoadBalancerDescription.Listener.ListenerType.HTTP,
                          externalPort: 80,
                          internalPort: 8501
                  )
          ],
          securityGroups: ["foo"],
          credentials: TestCredential.named('bar'),
          healthCheck: "HTTP:7001/health"
  )
  AmazonElasticLoadBalancing loadBalancing = Mock(AmazonElasticLoadBalancing)
  def mockAmazonClientProvider = Stub(AmazonClientProvider) {
    getAmazonElasticLoadBalancing(_, _, true) >> loadBalancing
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
  @Subject operation = new UpsertAmazonLoadBalancerAtomicOperation(description)

  def setup() {
    operation.amazonClientProvider = mockAmazonClientProvider
    operation.regionScopedProviderFactory = regionScopedProviderFactory
  }

  void "should create load balancer"() {
    def existingLoadBalancers = []

    when:
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest(loadBalancerNames: ["kato-main-frontend"])) >>
            new DescribeLoadBalancersResult(loadBalancerDescriptions: existingLoadBalancers)
    1 * loadBalancing.createLoadBalancer(new CreateLoadBalancerRequest(
            loadBalancerName: "kato-main-frontend",
            listeners: [
                    new Listener(protocol: "HTTP", loadBalancerPort: 80, instanceProtocol: "HTTP", instancePort: 8501)
            ],
            availabilityZones: ["us-east-1a"],
            subnets: [],
            securityGroups: ["sg-1234"],
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
                    additionalAttributes: []
            )
    ))
    0 * _
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
      new UpsertAmazonLoadBalancerDescription.Listener(
        externalProtocol: UpsertAmazonLoadBalancerDescription.Listener.ListenerType.HTTP,
        externalPort: 8080,
        internalPort: 8080
      ))

    when:
    operation.operate([])

    then:
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
    1 * loadBalancing.modifyLoadBalancerAttributes(new ModifyLoadBalancerAttributesRequest(
            loadBalancerName: "kato-main-frontend",
            loadBalancerAttributes: new LoadBalancerAttributes(
                    crossZoneLoadBalancing: new CrossZoneLoadBalancing(enabled: true),
                    additionalAttributes: []
            )
    ))
    0 * _
  }

  void "should attempt to apply all listener modifications regardless of individual failures"() {
    def existingLoadBalancers = [
      new LoadBalancerDescription(loadBalancerName: "kato-main-frontend")
        .withListenerDescriptions(
        new ListenerDescription().withListener(new Listener(protocol: "HTTP", loadBalancerPort: 80, instanceProtocol: "HTTP", instancePort: 8501))
      )
    ]

    given:
    description.listeners.clear()
    description.listeners.add(
      new UpsertAmazonLoadBalancerDescription.Listener(
        externalProtocol: UpsertAmazonLoadBalancerDescription.Listener.ListenerType.TCP,
        externalPort: 22,
        internalPort: 22
      ))
    description.listeners.add(
      new UpsertAmazonLoadBalancerDescription.Listener(
        externalProtocol: UpsertAmazonLoadBalancerDescription.Listener.ListenerType.HTTP,
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
    0 * _
  }

  void "should respect crossZone balancing directive"() {
    def loadBalancer = new LoadBalancerDescription(loadBalancerName: "kato-main-frontend")
    "when requesting crossZone to be disabled, we'll turn it off"
    description.crossZoneBalancing = false

    when:
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest(loadBalancerNames: ["kato-main-frontend"])) >>
            new DescribeLoadBalancersResult(loadBalancerDescriptions: [loadBalancer])
    1 * loadBalancing.modifyLoadBalancerAttributes(_) >> {  ModifyLoadBalancerAttributesRequest request ->
      assert !request.loadBalancerAttributes.crossZoneLoadBalancing.enabled
    }
  }

  void "should handle VPC ELB creation backward compatibility"() {
    description.subnetType = "internal"
    description.isInternal = null;
    when:
    operation.operate([])

    then:
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
    1 * mockSubnetAnalyzer.getSubnetIdsForZones(["us-east-1a"], "internal", SubnetTarget.ELB) >> ["subnet1"]
  }

  void "should handle VPC ELB creation"() {
      description.subnetType = "internal"
      description.isInternal = true;
      when:
      operation.operate([])

      then:
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
      1 * mockSubnetAnalyzer.getSubnetIdsForZones(["us-east-1a"], "internal", SubnetTarget.ELB) >> ["subnet1"]
  }

  void "should use clusterName if name not provided"() {
    setup:
    description.clusterName = "kato-test"
    description.name = null

    when:
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest(loadBalancerNames: ["kato-test-frontend"])) >>
            new DescribeLoadBalancersResult(loadBalancerDescriptions: [])
    1 * loadBalancing.createLoadBalancer() { createLoadBalancerRequest ->
      createLoadBalancerRequest.loadBalancerName == "kato-test-frontend"
    } >> new CreateLoadBalancerResult(dNSName: "dnsName1")
  }

  void "should reset existing listeners on a load balancer that already exists"() {
    setup:
    def listener = new ListenerDescription().withListener(new Listener("HTTP", 111, 80))
    def loadBalancer = new LoadBalancerDescription(listenerDescriptions: [listener])

    when:
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(_) >> new DescribeLoadBalancersResult(loadBalancerDescriptions: [loadBalancer])
    1 * loadBalancing.deleteLoadBalancerListeners(new DeleteLoadBalancerListenersRequest(loadBalancerPorts: [111]))
    1 * loadBalancing.createLoadBalancerListeners(new CreateLoadBalancerListenersRequest(
            listeners: [ new Listener(loadBalancerPort: 80, instancePort: 8501, protocol: "HTTP", instanceProtocol: "HTTP") ]
    ))
  }
}
