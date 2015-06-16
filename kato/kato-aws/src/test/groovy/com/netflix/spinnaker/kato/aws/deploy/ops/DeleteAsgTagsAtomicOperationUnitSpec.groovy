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
import com.amazonaws.services.autoscaling.model.*
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.aws.TestCredential
import com.netflix.spinnaker.kato.aws.deploy.description.DeleteAsgTagsDescription
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import spock.lang.Specification

class DeleteAsgTagsAtomicOperationUnitSpec extends Specification {

  def mockAutoScaling = Mock(AmazonAutoScaling)
  def mockAmazonClientProvider = Mock(AmazonClientProvider)

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should delete tags on ASG by name"() {
    def description = new DeleteAsgTagsDescription(asgName: "myasg-stack-v000", tagKeys: ["key"], regions: ["us-west-1"])
    description.credentials = TestCredential.named('baz')
    def operation = new DeleteAsgTagsAtomicOperation(description)
    operation.amazonClientProvider = mockAmazonClientProvider

    when:
    operation.operate([])

    then:
    1 * mockAmazonClientProvider.getAutoScaling(_, _, true) >> mockAutoScaling
    1 * mockAutoScaling.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest(autoScalingGroupNames: ["myasg-stack-v000"])) >> new DescribeAutoScalingGroupsResult(
      autoScalingGroups: [new AutoScalingGroup(autoScalingGroupName: "myasg-stack-v000")])
    1 * mockAutoScaling.deleteTags(new DeleteTagsRequest(tags: [new Tag(resourceId: "myasg-stack-v000", resourceType: "auto-scaling-group", key: "key")]))
    0 * _
  }
}
