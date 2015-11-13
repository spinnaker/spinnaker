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
import com.amazonaws.services.autoscaling.model.DeleteAutoScalingGroupRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.kato.aws.TestCredential
import com.netflix.spinnaker.kato.aws.deploy.description.ShrinkClusterDescription
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

class ShrinkClusterAtomicOperationUnitSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "operation looks up unused asgs and deletes them"() {
    setup:
    def inactiveAsg = "asgard-test-v000"
    def mockAsg = Mock(AutoScalingGroup)
    mockAsg.getAutoScalingGroupName() >> inactiveAsg
    mockAsg.getInstances() >> []
    def mockAutoScaling = Mock(AmazonAutoScaling)
    def mockResult = Mock(DescribeAutoScalingGroupsResult)
    mockResult.getAutoScalingGroups() >> [mockAsg]
    mockAutoScaling.describeAutoScalingGroups() >> mockResult
    def mockAmazonClientProvider = Mock(AmazonClientProvider)
    mockAmazonClientProvider.getAutoScaling(_, _, true) >> mockAutoScaling
    def description = new ShrinkClusterDescription()
    description.application = "asgard"
    description.clusterName = "asgard-test"
    description.regions = ['us-west-1']
    description.credentials = TestCredential.named('baz')
    def rt = Mock(RestTemplate)
    def operation = new ShrinkClusterAtomicOperation(description, rt)
    operation.amazonClientProvider = mockAmazonClientProvider

    when:
    operation.operate([])

    then:
    1 * mockAutoScaling.deleteAutoScalingGroup(_) >> { DeleteAutoScalingGroupRequest request ->
      assert request.autoScalingGroupName == inactiveAsg
      assert request.forceDelete
    }
  }
}
