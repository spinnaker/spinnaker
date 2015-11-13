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

package com.netflix.spinnaker.kato.aws.deploy.ops
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.CreateOrUpdateTagsRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.kato.aws.TestCredential
import com.netflix.spinnaker.kato.aws.deploy.description.UpsertAsgTagsDescription
import com.netflix.spinnaker.kato.data.task.DefaultTask
import com.netflix.spinnaker.kato.data.task.TaskRepository
import spock.lang.Specification

class UpsertAsgTagsAtomicOperationUnitSpec extends Specification {
  def setupSpec() {
    TaskRepository.threadLocalTask.set(new DefaultTask("taskId"))
  }

  void "operation invokes update to autoscaling group"() {
    setup:
    def mockAutoScaling = Mock(AmazonAutoScaling)
    def mockAmazonClientProvider = Mock(AmazonClientProvider)
    mockAmazonClientProvider.getAutoScaling(_, _, _) >> mockAutoScaling
    def description = new UpsertAsgTagsDescription(asgName: "myasg-stack-v000", tags: ["key": "value"], regions: ["us-west-1"])
    description.credentials = TestCredential.named('baz')
    def operation = new UpsertAsgTagsAtomicOperation(description)
    operation.amazonClientProvider = mockAmazonClientProvider

    when:
    operation.operate([])

    then:
    1 * mockAutoScaling.describeAutoScalingGroups(_) >> { DescribeAutoScalingGroupsRequest request ->
      assert request.autoScalingGroupNames == ["myasg-stack-v000"]
      def mock = Mock(AutoScalingGroup)
      mock.getAutoScalingGroupName() >> "myasg-stack-v000"
      new DescribeAutoScalingGroupsResult().withAutoScalingGroups(mock)
    }
    1 * mockAutoScaling.createOrUpdateTags(_) >> { CreateOrUpdateTagsRequest request ->
      assert request.tags
      assert request.tags.first().key == "key"
      assert request.tags.first().value == "value"
    }
    !TaskRepository.threadLocalTask.get().status.isFailed()
  }

  void "should fail task if ASG does not exist"() {
    setup:
    def mockAutoScaling = Mock(AmazonAutoScaling)
    def mockAmazonClientProvider = Mock(AmazonClientProvider)
    mockAmazonClientProvider.getAutoScaling(_, _, _) >> mockAutoScaling
    def description = new UpsertAsgTagsDescription(asgName: "myasg-stack-v000", tags: ["key": "value"], regions: ["us-west-1"])
    description.credentials = TestCredential.named('baz')
    def operation = new UpsertAsgTagsAtomicOperation(description)
    operation.amazonClientProvider = mockAmazonClientProvider

    when:
    operation.operate([])

    then:
    1 * mockAutoScaling.describeAutoScalingGroups(_) >> { DescribeAutoScalingGroupsRequest request ->
      new DescribeAutoScalingGroupsResult()
    }
    0 * mockAutoScaling.createOrUpdateTags(_)
    TaskRepository.threadLocalTask.get().status.isFailed()
  }
}
