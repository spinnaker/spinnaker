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


package com.netflix.spinnaker.kato.aws.deploy.ops.loadbalancer
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult
import com.amazonaws.services.ec2.model.SecurityGroup
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.*
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.aws.TestCredential
import com.netflix.spinnaker.kato.aws.deploy.description.UpsertAmazonLoadBalancerDescription
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class UpsertAmazonLoadBalancerAtomicOperationSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  @Shared
  UpsertAmazonLoadBalancerDescription description

  @Subject operation

  @Shared
  AmazonElasticLoadBalancing loadBalancing

  @Shared
  AmazonEC2 ec2

  def setup() {
    loadBalancing = Mock(AmazonElasticLoadBalancing)
    description = new UpsertAmazonLoadBalancerDescription(name: "kato-main-frontend", availabilityZones: ["us-east-1": ["us-east-1a"]],
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
    operation = new UpsertAmazonLoadBalancerAtomicOperation(description)
    ec2 = Mock(AmazonEC2)
    ec2.describeSecurityGroups() >> new DescribeSecurityGroupsResult().withSecurityGroups(new SecurityGroup().withGroupName("foo").withGroupId("sg-1234"))
    def mockAmazonClientProvider = Mock(AmazonClientProvider)
    mockAmazonClientProvider.getAmazonElasticLoadBalancing(_, _) >> loadBalancing
    mockAmazonClientProvider.getAmazonEC2(_, _) >> ec2
    operation.amazonClientProvider = mockAmazonClientProvider
  }

  void "should respect crossZone balancing directive"() {
    setup:
    "by default, we'll enable cross-zone balancing"
    def loadBalancer = Stub(LoadBalancerDescription)
    loadBalancer.getLoadBalancerName() >> "foo"

    when:
    operation.operate([])

    then:
    2 * loadBalancing.describeLoadBalancers(_) >>> [null, new DescribeLoadBalancersResult().withLoadBalancerDescriptions(loadBalancer)]
    1 * loadBalancing.modifyLoadBalancerAttributes(_) >> { ModifyLoadBalancerAttributesRequest request ->
      assert request.loadBalancerAttributes.crossZoneLoadBalancing.enabled
    }

    when:
    "when requesting crossZone to be disabled, we'll turn it off"
    description.crossZoneBalancing = false
    operation.operate([])

    then:
    2 * loadBalancing.describeLoadBalancers(_) >>> [null, new DescribeLoadBalancersResult().withLoadBalancerDescriptions(loadBalancer)]
    1 * loadBalancing.modifyLoadBalancerAttributes(_) >> { ModifyLoadBalancerAttributesRequest request ->
      assert !request.loadBalancerAttributes.crossZoneLoadBalancing.enabled
    }
  }

  void "should use clusterName if name not provided"() {
    setup:
    description.clusterName = "kato-test"
    description.name = null
    def loadBalancer = Mock(LoadBalancerDescription)

    when:
    operation.operate([])

    then:
    2 * loadBalancing.describeLoadBalancers(_) >>> [null, new DescribeLoadBalancersResult().withLoadBalancerDescriptions(loadBalancer)]
    1 * loadBalancing.createLoadBalancer(_) >> { CreateLoadBalancerRequest request ->
      assert request.loadBalancerName == "${description.clusterName}-frontend".toString()
    }
  }

  void "should create a new load balancer if one doesn't already exist"() {
    setup:
    def loadBalancer = Mock(LoadBalancerDescription)

    when:
    operation.operate([])

    then:
    2 * loadBalancing.describeLoadBalancers(_) >>> [null, new DescribeLoadBalancersResult().withLoadBalancerDescriptions(loadBalancer)]
    1 * loadBalancing.createLoadBalancer(_) >> { CreateLoadBalancerRequest request ->
      assert request.loadBalancerName == description.name
    }
  }

  void "should reset existing listeners on a load balancer that already exists"() {
    setup:
    def loadBalancer = Mock(LoadBalancerDescription)
    def listener = new ListenerDescription().withListener(new Listener("HTTP", 111, 80))
    loadBalancer.getListenerDescriptions() >> [listener]

    when:
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(_) >> new DescribeLoadBalancersResult().withLoadBalancerDescriptions(loadBalancer)
    1 * loadBalancing.deleteLoadBalancerListeners(_) >> { DeleteLoadBalancerListenersRequest request ->
      assert request.loadBalancerPorts == [111]
    }
    1 * loadBalancing.createLoadBalancerListeners(_) >> { CreateLoadBalancerListenersRequest request ->
      assert request.listeners[0].loadBalancerPort == description.listeners[0].externalPort
      assert request.listeners[0].instancePort == description.listeners[0].internalPort
      assert request.listeners[0].protocol == description.listeners[0].externalProtocol.name()
    }
  }

  void "should reset security groups"() {
    setup:
    def loadBalancer = Mock(LoadBalancerDescription)

    when:
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(_) >> new DescribeLoadBalancersResult().withLoadBalancerDescriptions(loadBalancer)
    1 * loadBalancing.applySecurityGroupsToLoadBalancer(_) >> { ApplySecurityGroupsToLoadBalancerRequest request ->
      assert request.securityGroups == ["sg-1234"]
    }
  }

  void "should apply healthcheck configuration"() {
    setup:
    def loadBalancer = Mock(LoadBalancerDescription)

    when:
    operation.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(_) >> new DescribeLoadBalancersResult().withLoadBalancerDescriptions(loadBalancer)
    1 * loadBalancing.configureHealthCheck(_) >> { ConfigureHealthCheckRequest request ->
      assert request.healthCheck.target == description.healthCheck
    }
  }

}
