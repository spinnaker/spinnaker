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
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest
import com.amazonaws.services.autoscaling.model.SuspendProcessesRequest
import com.amazonaws.services.ec2.AmazonEC2
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.amazoncomponents.security.AmazonCredentials
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.aws.description.CreateAsgDescription
import com.netflix.spinnaker.kato.model.aws.AutoScalingGroupOptions
import com.netflix.spinnaker.kato.model.aws.AutoScalingProcessType
import spock.lang.Specification

class CreateAsgAtomicOperationUnitSpec extends Specification {

  def mockAmazonEC2 = Mock(AmazonEC2)
  def mockAmazonAutoScaling = Mock(AmazonAutoScaling)
  def mockAmazonClientProvider = Mock(AmazonClientProvider)

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    with(mockAmazonClientProvider) {
      getAmazonEC2(_, _) >> mockAmazonEC2
      getAutoScaling(_, _) >> mockAmazonAutoScaling
    }
  }

  void "operation invokes create auto scaling group"() {
    setup:
    def description = new CreateAsgDescription(asgName: "wolverine", regions: ["us-west-1"],
        asgOptions: new AutoScalingGroupOptions(autoScalingGroupName: "wolverine", suspendedProcesses: [AutoScalingProcessType.Launch])
    )
    description.credentials = new AmazonCredentials(Mock(AWSCredentials), "baz")
    def operation = new CreateAsgAtomicOperation(description)
    operation.amazonClientProvider = mockAmazonClientProvider

    when:
    operation.operate([])

    then:
    1 * mockAmazonEC2.describeVpcs() >> []
    1 * mockAmazonEC2.describeSubnets() >> []
    0 * mockAmazonEC2._

    1 * mockAmazonAutoScaling.createAutoScalingGroup(new CreateAutoScalingGroupRequest(
      autoScalingGroupName: "wolverine",
    ))
    1 * mockAmazonAutoScaling.suspendProcesses(new SuspendProcessesRequest(
      autoScalingGroupName: "wolverine",
      scalingProcesses: ["Launch"]
    ))
    0 * mockAmazonAutoScaling._
  }
}
