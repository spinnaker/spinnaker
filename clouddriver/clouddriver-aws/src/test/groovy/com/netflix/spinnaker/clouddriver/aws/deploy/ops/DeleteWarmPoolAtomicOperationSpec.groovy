/*
 * Copyright 2026 McIntosh.farm
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
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteWarmPoolDescription
import com.netflix.spinnaker.clouddriver.aws.services.AsgService
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Specification
import spock.lang.Subject

class DeleteWarmPoolAtomicOperationSpec extends Specification {

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

  void 'should delete warm pool for each asg'() {
    def description = new DeleteWarmPoolDescription(
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
      forceDelete: true
    )
    @Subject operation = new DeleteWarmPoolAtomicOperation(description)
    operation.regionScopedProviderFactory = mockRegionScopedProviderFactory

    when:
    operation.operate([])

    then: 1 * mockAsgService.getAutoScalingGroup('asg1') >> new AutoScalingGroup()
    then: 1 * mockAsgService.deleteWarmPool("asg1", true)
    then: 1 * mockAsgService.getAutoScalingGroup('asg1') >> new AutoScalingGroup()
    then: 1 * mockAsgService.deleteWarmPool("asg1", true)
    0 * mockAsgService._
  }

  void 'should not delete warm pool in region if ASG name is invalid'() {
    def description = new DeleteWarmPoolDescription(
      asgs: [
        [
          serverGroupName: "asg1",
          region         : "us-west-1"
        ],
      ],
      forceDelete: false
    )
    @Subject operation = new DeleteWarmPoolAtomicOperation(description)
    operation.regionScopedProviderFactory = mockRegionScopedProviderFactory

    when:
    operation.operate([])

    then: 1 * mockAsgService.getAutoScalingGroup('asg1')
    0 * mockAsgService._
  }
}
