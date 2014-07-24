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


package com.netflix.spinnaker.kato.deploy.aws.ops.loadbalancer

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult
import com.amazonaws.services.ec2.model.SecurityGroup
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.ApplySecurityGroupsToLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.ConfigureHealthCheckRequest
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerListenersRequest
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerListenersRequest
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult
import com.amazonaws.services.elasticloadbalancing.model.Listener
import com.amazonaws.services.elasticloadbalancing.model.ListenerDescription
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.amazoncomponents.security.AmazonCredentials
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.aws.description.UpsertAmazonLoadBalancerDescription
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class UpsertAmazonLoadBalancerAtomicOperationSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def description = new UpsertAmazonLoadBalancerDescription(clusterName: "kato-main", availabilityZones: ["us-east-1": ["us-east-1a"]],
    listeners: [
      new UpsertAmazonLoadBalancerDescription.Listener(
        externalProtocol: UpsertAmazonLoadBalancerDescription.Listener.ListenerType.HTTP,
        externalPort: 80,
        internalPort: 8501
      )
    ],
    securityGroups: ["foo"],
    credentials: new AmazonCredentials(Mock(AWSCredentials), "bar"),
    healthCheck: "HTTP:7001/health"
  )

  @Subject operation = new UpsertAmazonLoadBalancerAtomicOperation(description)

  @Shared
  AmazonElasticLoadBalancing loadBalancing

  @Shared
  AmazonEC2 ec2

  def setup() {
    loadBalancing = Mock(AmazonElasticLoadBalancing)
    ec2 = Mock(AmazonEC2)
    ec2.describeSecurityGroups() >> new DescribeSecurityGroupsResult().withSecurityGroups(new SecurityGroup().withGroupName("foo").withGroupId("sg-1234"))
    def mockAmazonClientProvider = Mock(AmazonClientProvider)
    mockAmazonClientProvider.getAmazonElasticLoadBalancing(_, _) >> loadBalancing
    mockAmazonClientProvider.getAmazonEC2(_, _) >> ec2
    operation.amazonClientProvider = mockAmazonClientProvider
  }

  void "should create a new load balancer if one doesn't already exist"() {
    setup:
    def loadBalancer = Mock(LoadBalancerDescription)

    when:
    operation.operate([])

    then:
    2 * loadBalancing.describeLoadBalancers(_) >>> [null, new DescribeLoadBalancersResult().withLoadBalancerDescriptions(loadBalancer)]
    1 * loadBalancing.createLoadBalancer(_) >> { CreateLoadBalancerRequest request ->
      assert request.loadBalancerName == "${description.clusterName}-frontend"
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
