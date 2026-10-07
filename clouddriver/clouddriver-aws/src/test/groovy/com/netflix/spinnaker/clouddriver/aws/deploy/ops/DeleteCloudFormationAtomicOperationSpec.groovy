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

import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteCloudFormationDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.services.cloudformation.CloudFormationClient
import software.amazon.awssdk.services.cloudformation.model.DeleteStackRequest
import software.amazon.awssdk.services.cloudformation.model.DeleteStackResponse
import spock.lang.Specification

class DeleteCloudFormationAtomicOperationSpec extends Specification {
  void setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should build a DeleteStackRequest and submit through aws client"() {
    given:
    def amazonClientProvider = Mock(AmazonClientProvider)
    def cloudFormationClient = Mock(CloudFormationClient)
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
    1 * amazonClientProvider.getAmazonCloudFormationV2(_, _) >> cloudFormationClient
    1 * cloudFormationClient.deleteStack(_ as DeleteStackRequest) >> { DeleteStackRequest request ->
      assert request.stackName() == "stackTest"
      DeleteStackResponse.builder().build()
    }
  }

  void "should propagate exceptions when deleting the stack"() {
    given:
    def amazonClientProvider = Mock(AmazonClientProvider)
    def cloudFormationClient = Mock(CloudFormationClient)
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
    def exception = AwsServiceException.builder().message("error").build()

    when:
    op.operate([])

    then:
    1 * amazonClientProvider.getAmazonCloudFormationV2(_, _) >> cloudFormationClient
    1 * cloudFormationClient.deleteStack(_ as DeleteStackRequest) >> {
      throw exception
    }
    thrown(AwsServiceException)
  }
}
