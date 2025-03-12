/*
 * Copyright 2021 Expedia, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.cloudformation.AmazonCloudFormation
import com.amazonaws.services.cloudformation.model.DeleteStackRequest
import com.amazonaws.services.cloudformation.model.DeleteStackResult
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteCloudFormationDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Specification

class DeleteCloudFormationAtomicOperationSpec extends Specification {
  void setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should build a DeleteStackRequest and submit through aws client"() {
    given:
    def amazonClientProvider = Mock(AmazonClientProvider)
    def amazonCloudFormation = Mock(AmazonCloudFormation)
    def deleteStackResult = Mock(DeleteStackResult)
    def op = new DeleteCloudFormationAtomicOperation(
      new DeleteCloudFormationDescription(
        [
          stackName: "stackTest",
          region: "eu-west-1",
          credentials: TestCredential.named("test")
        ]
      )
    )
    op.amazonClientProvider = amazonClientProvider

    when:
    op.operate([])

    then:
    1 * amazonClientProvider.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    1 * amazonCloudFormation.deleteStack(_) >> { DeleteStackRequest request ->
      assert request.getStackName() == "stackTest"
      deleteStackResult
    }
  }

  void "should propagate exceptions when deleting the stack"() {
    given:
    def amazonClientProvider = Mock(AmazonClientProvider)
    def amazonCloudFormation = Mock(AmazonCloudFormation)
    def op = new DeleteCloudFormationAtomicOperation(
      new DeleteCloudFormationDescription(
        [
          stackName: "stackTest",
          region: "eu-west-1",
          credentials: TestCredential.named("test")
        ]
      )
    )
    op.amazonClientProvider = amazonClientProvider
    def exception = new AmazonServiceException("error")

    when:
    try {
      op.operate([])
    }
    catch (Exception e) {
      e instanceof AmazonServiceException
    }

    then:
    1 * amazonClientProvider.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    1 * amazonCloudFormation.deleteStack(_) >> {
      throw exception
    }
  }


}
