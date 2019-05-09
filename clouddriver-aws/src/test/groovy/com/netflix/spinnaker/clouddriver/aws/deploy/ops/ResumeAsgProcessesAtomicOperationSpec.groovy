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
package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.ResumeAsgProcessesAtomicOperation
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ResumeAsgProcessesDescription
import com.netflix.spinnaker.clouddriver.aws.model.AutoScalingProcessType
import com.netflix.spinnaker.clouddriver.aws.services.AsgService
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.aws.model.AutoScalingProcessType.Launch
import static com.netflix.spinnaker.clouddriver.aws.model.AutoScalingProcessType.Terminate

class ResumeAsgProcessesAtomicOperationSpec extends Specification {

  def mockAsgService = Mock(AsgService)
  def mockRegionScopedProvider = Mock(RegionScopedProviderFactory.RegionScopedProvider) {
    getAsgService() >> mockAsgService
  }
  def mockRegionScopedProviderFactory = Mock(RegionScopedProviderFactory) {
    forRegion(_, _) >> mockRegionScopedProvider
  }
  def task = new DefaultTask("1")

  def setup() {
    TaskRepository.threadLocalTask.set(task)
  }

  void 'should resume ASG processes'() {
    def description = new ResumeAsgProcessesDescription(
      asgs: [
        [
          serverGroupName: "asg1",
          region         : "us-west-1"
        ],
        [
          serverGroupName: "asg1",
          region         : "us-east-1"
        ],
      ],
      processes: ["Launch", "Terminate"]
    )
    @Subject operation = new ResumeAsgProcessesAtomicOperation(description)
    operation.regionScopedProviderFactory = mockRegionScopedProviderFactory

    when:
    operation.operate([])

    then: 1 * mockAsgService.getAutoScalingGroup('asg1') >> new AutoScalingGroup()
    then: 1 * mockAsgService.resumeProcesses("asg1", [Launch, Terminate])
    then: 1 * mockAsgService.getAutoScalingGroup('asg1') >> new AutoScalingGroup()
    then: 1 * mockAsgService.resumeProcesses("asg1", [Launch, Terminate])

    and:
    task.history*.status == [
      "Creating task 1",
      "Initializing Resume ASG Processes operation for [[serverGroupName:asg1, region:us-west-1], [serverGroupName:asg1, region:us-east-1]]...",
      "Resuming ASG processes (Launch, Terminate) for asg1 in us-west-1...",
      "Resuming ASG processes (Launch, Terminate) for asg1 in us-east-1...",
      "Finished Resume ASG Processes operation for [[serverGroupName:asg1, region:us-west-1], [serverGroupName:asg1, region:us-east-1]]."
    ]
    0 * mockAsgService._
  }

  void 'should not resume ASG processes in region if ASG name is invalid'() {
    def description = new ResumeAsgProcessesDescription(
      asgs: [
        [
          serverGroupName: "asg1",
          region         : "us-west-1"
        ],
        [
          serverGroupName: "asg1",
          region         : "us-east-1"
        ],
      ],
      processes: ["Launch", "Terminate"]
    )
    @Subject operation = new ResumeAsgProcessesAtomicOperation(description)
    operation.regionScopedProviderFactory = mockRegionScopedProviderFactory

    when:
    operation.operate([])

    then: 1 * mockAsgService.getAutoScalingGroup('asg1')
    then: 1 * mockAsgService.getAutoScalingGroup('asg1') >> new AutoScalingGroup()
    then: 1 * mockAsgService.resumeProcesses("asg1", [Launch, Terminate])

    and:
    task.history*.status == [
      "Creating task 1",
      "Initializing Resume ASG Processes operation for [[serverGroupName:asg1, region:us-west-1], [serverGroupName:asg1, region:us-east-1]]...",
      "No ASG named 'asg1' found in us-west-1.",
      "Resuming ASG processes (Launch, Terminate) for asg1 in us-east-1...",
      "Finished Resume ASG Processes operation for [[serverGroupName:asg1, region:us-west-1], [serverGroupName:asg1, region:us-east-1]]."
    ]
    0 * mockAsgService._
  }

  void 'should not resume ASG processes in region if there is an error'() {
    def description = new ResumeAsgProcessesDescription(
      asgs: [
        [
          serverGroupName: "asg1",
          region         : "us-west-1"
        ],
        [
          serverGroupName: "asg1",
          region         : "us-east-1"
        ],
      ],
      processes: ["Launch", "Terminate"]
    )
    @Subject operation = new ResumeAsgProcessesAtomicOperation(description)
    operation.regionScopedProviderFactory = mockRegionScopedProviderFactory

    when:
    operation.operate([])

    then: 1 * mockAsgService.getAutoScalingGroup('asg1') >> new AutoScalingGroup()
    then: 1 * mockAsgService.resumeProcesses("asg1", [Launch, Terminate]) >> {
      throw new Exception('Uh oh!')
    }
    then: 1 * mockAsgService.getAutoScalingGroup('asg1') >> new AutoScalingGroup()
    then: 1 * mockAsgService.resumeProcesses("asg1", [Launch, Terminate])

    and:
    task.history*.status == [
      "Creating task 1",
      "Initializing Resume ASG Processes operation for [[serverGroupName:asg1, region:us-west-1], [serverGroupName:asg1, region:us-east-1]]...",
      "Resuming ASG processes (Launch, Terminate) for asg1 in us-west-1...",
      "Could not resume processes for ASG 'asg1' in region us-west-1! Reason: Uh oh!",
      "Resuming ASG processes (Launch, Terminate) for asg1 in us-east-1...",
      "Finished Resume ASG Processes operation for [[serverGroupName:asg1, region:us-west-1], [serverGroupName:asg1, region:us-east-1]]."
    ]
    0 * mockAsgService._
  }

}
