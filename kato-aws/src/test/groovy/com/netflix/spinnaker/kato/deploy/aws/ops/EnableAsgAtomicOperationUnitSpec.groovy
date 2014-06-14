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
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.amazoncomponents.security.AmazonCredentials
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.aws.description.EnableAsgDescription
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

class EnableAsgAtomicOperationUnitSpec extends Specification {

  def mockAutoScaling = Mock(AmazonAutoScaling)
  def mockLoadBalancing = Mock(AmazonElasticLoadBalancing)
  def mockAmazonClientProvider = Mock(AmazonClientProvider)

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    with(mockAmazonClientProvider) {
      getAmazonElasticLoadBalancing(_, _) >> mockLoadBalancing
      getAutoScaling(_, _) >> mockAutoScaling
    }
  }

  void "operation invokes update to autoscaling group and registers instances with the load balancers"() {
    setup:
    def description = new EnableAsgDescription(asgName: "myasg-stack-v000", regions: ["us-west-1"])
    description.credentials = new AmazonCredentials(Mock(AWSCredentials), "baz")
    def operation = new EnableAsgAtomicOperation(description, Mock(RestTemplate))
    operation.discoveryHostFormat = "http://%s.discovery%s.netflix.net"
    operation.amazonClientProvider = mockAmazonClientProvider

    when:
    operation.operate([])

    then:
    1 * mockLoadBalancing.registerInstancesWithLoadBalancer(new RegisterInstancesWithLoadBalancerRequest(
      loadBalancerName: "myasg-stack-frontend",
      instances: [new com.amazonaws.services.elasticloadbalancing.model.Instance(instanceId: "i-123456")]
    ))
    0 * mockLoadBalancing._

    1 * mockAutoScaling.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest(
      autoScalingGroupNames: ["myasg-stack-v000"]
    )) >> {
      new DescribeAutoScalingGroupsResult(autoScalingGroups: [new AutoScalingGroup(
        autoScalingGroupName: "myasg-stack-v000",
        instances: [new Instance(instanceId: "i-123456")],
        loadBalancerNames: ["myasg-stack-frontend"]
      )])
    }
    1 * mockAutoScaling.resumeProcesses(new ResumeProcessesRequest(
      autoScalingGroupName: "myasg-stack-v000",
      scalingProcesses: ["Launch", "Terminate", "AddToLoadBalancer"]
    ))
    0 * mockAutoScaling._
  }
}
