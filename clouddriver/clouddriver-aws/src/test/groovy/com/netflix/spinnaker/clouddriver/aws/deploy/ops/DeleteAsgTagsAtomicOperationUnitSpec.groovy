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
import software.amazon.awssdk.services.autoscaling.AutoScalingClient
import software.amazon.awssdk.services.autoscaling.model.*
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteAsgTagsDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Specification

class DeleteAsgTagsAtomicOperationUnitSpec extends Specification {

  def mockAutoScaling = Mock(AutoScalingClient)
  def mockAmazonClientProvider = Mock(AmazonClientProvider)

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should delete tags on ASG by name"() {
    def description = new DeleteAsgTagsDescription(
      asgs   : [[
        serverGroupName: "myasg-stack-v000",
        region         : "us-west-1"
      ]],
      tagKeys: ["key"]
    )
    description.credentials = TestCredential.named('baz')
    def operation = new DeleteAsgTagsAtomicOperation(description)
    operation.amazonClientProvider = mockAmazonClientProvider

    when:
    operation.operate([])

    then:
    1 * mockAmazonClientProvider.getAutoScalingV2(_, _) >> mockAutoScaling
    1 * mockAutoScaling.describeAutoScalingGroups(DescribeAutoScalingGroupsRequest.builder().autoScalingGroupNames(["myasg-stack-v000"]).build()) >> DescribeAutoScalingGroupsResponse.builder()
      .autoScalingGroups([AutoScalingGroup.builder().autoScalingGroupName("myasg-stack-v000").build()]).build()
    1 * mockAutoScaling.deleteTags(DeleteTagsRequest.builder().tags([Tag.builder().resourceId("myasg-stack-v000").resourceType("auto-scaling-group").key("key").build()]).build())
    0 * _
  }
}
