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
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.Instance
import com.netflix.amazoncomponents.security.AmazonCredentials
import com.netflix.spinnaker.kato.data.task.DefaultTask
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.aws.description.EnableAsgDescription
import com.netflix.spinnaker.kato.model.aws.AutoScalingProcessType
import com.netflix.spinnaker.kato.services.AsgService
import com.netflix.spinnaker.kato.services.ElbService
import com.netflix.spinnaker.kato.services.EurekaService
import com.netflix.spinnaker.kato.services.RegionScopedProviderFactory
import spock.lang.Specification

class EnableAsgAtomicOperationUnitSpec extends Specification {

  def mockAsgService = Mock(AsgService)
  def mockEurekaService = Mock(EurekaService)
  def mockElbService = Mock(ElbService)
  def mockRegionScopedProvider = Mock(RegionScopedProviderFactory.RegionScopedProvider) {
    getAsgService() >> mockAsgService
    getElbService() >> mockElbService
    getEurekaService(_, _) >> mockEurekaService
  }
  def mockRegionScopedProviderFactory = Mock(RegionScopedProviderFactory)
  def task = new DefaultTask("1")

  def setup() {
    TaskRepository.threadLocalTask.set(task)
  }

  void "operation invokes update to autoscaling group and registers instances from the load balancers"() {
    setup:
    def description = new EnableAsgDescription(asgName: "myasg-stack-v000", regions: ["us-west-1"])
    description.credentials = new AmazonCredentials(Mock(AWSCredentials), "baz")
    def operation = new EnableAsgAtomicOperation(description)
    operation.with {
      regionScopedProviderFactory = mockRegionScopedProviderFactory
    }

    when:
    operation.operate([])

    then:
    1 * mockRegionScopedProviderFactory.forRegion(_, "us-west-1") >> mockRegionScopedProvider
    with(mockAsgService) {
      1 * getAutoScalingGroup("myasg-stack-v000") >> new AutoScalingGroup(
        autoScalingGroupName: "myasg-stack-v000", instances: [new Instance(instanceId: "i-123456")], loadBalancerNames: ["myasg-stack-frontend"])
      1 * resumeProcesses("myasg-stack-v000", AutoScalingProcessType.getDisableProcesses())
      0 * _
    }
    1 * mockEurekaService.enableInstancesForAsg("myasg-stack-v000", ["i-123456"])
    1 * mockElbService.registerInstancesWithLoadBalancer(["myasg-stack-frontend"], ["i-123456"])

    and:
    task.getHistory()*.status == [
      "Initializing Enable ASG operation for 'myasg-stack-v000'...",
      "Enabling ASG 'myasg-stack-v000' in us-west-1...",
      "Registering instances with Load Balancers...",
      "Done enabling ASG myasg-stack-v000."
    ]
  }

  void "should log a nonexistant ASG"() {
    setup:
    def description = new EnableAsgDescription(asgName: "myasg-stack-v000", regions: ["us-west-1"])
    description.credentials = new AmazonCredentials(Mock(AWSCredentials), "baz")
    def operation = new EnableAsgAtomicOperation(description)
    operation.with {
      regionScopedProviderFactory = mockRegionScopedProviderFactory
    }

    when:
    operation.operate([])

    then:
    1 * mockRegionScopedProviderFactory.forRegion(_, "us-west-1") >> mockRegionScopedProvider
    with(mockAsgService) {
      1 * getAutoScalingGroup("myasg-stack-v000")
      0 * _
    }

    and:
    task.getHistory()*.status == [
      "Initializing Enable ASG operation for 'myasg-stack-v000'...",
      "No ASG named 'myasg-stack-v000' found in us-west-1",
      "Done enabling ASG myasg-stack-v000."
    ]
  }

  void "should enable ASGs across regions even after failure"() {
    setup:
    def description = new EnableAsgDescription(asgName: "myasg-stack-v000", regions: ["us-east-1", "us-west-1"])
    description.credentials = new AmazonCredentials(Mock(AWSCredentials), "baz")
    def operation = new EnableAsgAtomicOperation(description)
    operation.with {
      regionScopedProviderFactory = mockRegionScopedProviderFactory
    }
    def mockAsgServiceEast = Mock(AsgService)
    def mockElbServiceEast = Mock(ElbService)
    def mockEurekaServiceEast = Mock(EurekaService)
    def mockRegionScopedProviderEast = Mock(RegionScopedProviderFactory.RegionScopedProvider) {
      getAsgService() >> mockAsgServiceEast
      getElbService() >> mockElbServiceEast
      getEurekaService(_, _) >> mockEurekaServiceEast
    }
    def mockAsgServiceWest = Mock(AsgService)
    def mockElbServiceWest = Mock(ElbService)
    def mockEurekaServiceWest = Mock(EurekaService)
    def mockRegionScopedProviderWest = Mock(RegionScopedProviderFactory.RegionScopedProvider) {
      getAsgService() >> mockAsgServiceWest
      getElbService() >> mockElbServiceWest
      getEurekaService(_, _) >> mockEurekaServiceWest
    }

    when:
    operation.operate([])

    then:
    1 * mockRegionScopedProviderFactory.forRegion(_, "us-east-1") >> mockRegionScopedProviderEast
    with(mockAsgServiceEast) {
      1 * getAutoScalingGroup("myasg-stack-v000") >> new AutoScalingGroup()
      1 * resumeProcesses("myasg-stack-v000", AutoScalingProcessType.getDisableProcesses()) >> {
        throw new Exception("Uh oh!")
      }
      0 * _
    }
    0 * mockElbServiceEast._
    0 * mockEurekaServiceWest._

    then:
    1 * mockRegionScopedProviderFactory.forRegion(_, "us-west-1") >> mockRegionScopedProviderWest
    with(mockAsgServiceWest) {
      1 * getAutoScalingGroup("myasg-stack-v000") >> new AutoScalingGroup(autoScalingGroupName: "myasg-stack-v000",
        instances: [new Instance(instanceId: "i-123456")], loadBalancerNames: ["myasg-stack-frontend"])
      1 * resumeProcesses("myasg-stack-v000", AutoScalingProcessType.getDisableProcesses())
      0 * _
    }
    1 * mockEurekaServiceWest.enableInstancesForAsg("myasg-stack-v000", ["i-123456"])
    1 * mockElbServiceWest.registerInstancesWithLoadBalancer(["myasg-stack-frontend"], ["i-123456"])

    and:
    task.getHistory()*.status == [
      "Initializing Enable ASG operation for 'myasg-stack-v000'...",
      "Enabling ASG 'myasg-stack-v000' in us-east-1...",
      "Could not enable ASG 'myasg-stack-v000' in region us-east-1! Reason: Uh oh!",
      "Enabling ASG 'myasg-stack-v000' in us-west-1...",
      "Registering instances with Load Balancers...",
      "Done enabling ASG myasg-stack-v000."
    ]
  }

}
