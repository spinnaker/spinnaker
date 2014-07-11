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


package com.netflix.spinnaker.kato.deploy.aws.ops

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.*
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.amazoncomponents.security.AmazonCredentials
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.aws.description.DisableAsgDescription
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

class DisableAsgAtomicOperationUnitSpec extends Specification {
  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "operation invokes update to autoscaling group and deregisters instances from the load balancers"() {
    setup:
    def mockAutoScaling = Mock(AmazonAutoScaling)
    def mockLoadBalancing = Mock(AmazonElasticLoadBalancing)
    def mockAmazonClientProvider = Mock(AmazonClientProvider)
    mockAmazonClientProvider.getAmazonElasticLoadBalancing(_, _) >> mockLoadBalancing
    mockAmazonClientProvider.getAutoScaling(_, _) >> mockAutoScaling
    def description = new DisableAsgDescription(asgName: "myasg-stack-v000", regions: ["us-west-1"])
    description.credentials = new AmazonCredentials(Mock(AWSCredentials), "baz")
    def operation = new DisableAsgAtomicOperation(description, Mock(RestTemplate))
    operation.discoveryHostFormat = "http://%s.discovery%s.netflix.net"
    operation.amazonClientProvider = mockAmazonClientProvider

    when:
    operation.operate([])

    then:
    1 * mockLoadBalancing.deregisterInstancesFromLoadBalancer(_) >> { DeregisterInstancesFromLoadBalancerRequest request ->
      assert request.loadBalancerName == "myasg-stack-frontend"
      assert request.instances.first().instanceId == "i-123456"
    }
    1 * mockAutoScaling.describeAutoScalingGroups(_) >> { DescribeAutoScalingGroupsRequest request ->
      assert request.autoScalingGroupNames == ["myasg-stack-v000"]
      def mock = Mock(AutoScalingGroup)
      mock.getAutoScalingGroupName() >> "myasg-stack-v000"
      def instance = Mock(Instance)
      instance.getInstanceId() >> "i-123456"
      mock.getInstances() >> [instance]
      mock.getLoadBalancerNames() >> ["myasg-stack-frontend"]
      new DescribeAutoScalingGroupsResult().withAutoScalingGroups(mock)
    }
    1 * mockAutoScaling.suspendProcesses(_) >> { SuspendProcessesRequest request ->
      assert request.autoScalingGroupName == "myasg-stack-v000"
      assert request.scalingProcesses == ["AddToLoadBalancer", "Launch", "Terminate"]
    }
  }
}
