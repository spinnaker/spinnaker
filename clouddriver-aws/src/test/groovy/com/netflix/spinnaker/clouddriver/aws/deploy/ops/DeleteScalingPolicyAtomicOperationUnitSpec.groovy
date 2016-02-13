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

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.DeletePolicyRequest
import com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest
import com.amazonaws.services.autoscaling.model.PutScalingPolicyResult
import com.amazonaws.services.autoscaling.model.StepAdjustment
import com.netflix.spinnaker.clouddriver.aws.deploy.description.AdjustmentType
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteScalingPolicyDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.MetricAggregationType
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertScalingPolicyDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.services.IdGenerator
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Specification
import spock.lang.Subject

class DeleteScalingPolicyAtomicOperationUnitSpec extends Specification {
  private static final String ACCOUNT = "test"

  def credz = Stub(NetflixAmazonCredentials) {
    getName() >> ACCOUNT
  }

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  final description = new DeleteScalingPolicyDescription(
    asgName: "kato-main-v000",
    name: "scalingPolicy1",
    region: "us-west-1",
    credentials: credz
  )

  final autoScaling = Mock(AmazonAutoScaling)
  final amazonClientProvider = Stub(AmazonClientProvider) {
    getAutoScaling(credz, "us-west-1", true) >> autoScaling
  }

  @Subject final op = new DeleteScalingPolicyAtomicOperation(description)

  def setup() {
    op.amazonClientProvider = amazonClientProvider
  }

  void "delete scaling policy"() {

    when:
    op.operate([])

    then:
    1 * autoScaling.deletePolicy(new DeletePolicyRequest(
      policyName: "scalingPolicy1",
      autoScalingGroupName: "kato-main-v000"
    ))
  }

}
