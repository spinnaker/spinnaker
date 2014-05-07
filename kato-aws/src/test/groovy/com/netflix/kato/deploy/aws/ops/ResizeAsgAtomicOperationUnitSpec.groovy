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

package com.netflix.kato.deploy.aws.ops

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest
import com.netflix.kato.data.task.Task
import com.netflix.kato.data.task.TaskRepository
import com.netflix.kato.deploy.aws.StaticAmazonClients
import com.netflix.kato.deploy.aws.description.ResizeAsgDescription
import com.netflix.kato.security.aws.AmazonCredentials
import spock.lang.Specification

class ResizeAsgAtomicOperationUnitSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "operation invokes update to autoscaling group"() {
    setup:
    def mockAutoScaling = Mock(AmazonAutoScaling)
    StaticAmazonClients.metaClass.'static'.getAutoScaling = { AmazonCredentials credentials, String region ->
      mockAutoScaling
    }
    def description = new ResizeAsgDescription(asgName: "myasg-stack-v000", regions: ["us-west-1"])
    description.credentials = new AmazonCredentials(Mock(AWSCredentials), "baz")
    description.capacity.min = 1
    description.capacity.max = 2
    description.capacity.desired = 5
    def operation = new ResizeAsgAtomicOperation(description)

    when:
    operation.operate([])

    then:
    1 * mockAutoScaling.updateAutoScalingGroup(_) >> { UpdateAutoScalingGroupRequest request ->
      assert request.autoScalingGroupName == "myasg-stack-v000"
      assert request.minSize == 1
      assert request.maxSize == 2
      assert request.desiredCapacity == 5
    }
  }
}
