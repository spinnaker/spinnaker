/*
 * Copyright (c) 2019 Schibsted Media Group.
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

import com.amazonaws.services.cloudformation.AmazonCloudFormation
import com.amazonaws.services.cloudformation.model.AlreadyExistsException
import com.amazonaws.services.cloudformation.model.CreateStackRequest
import com.amazonaws.services.cloudformation.model.CreateStackResult
import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.cloudformation.model.UpdateStackRequest
import com.amazonaws.services.cloudformation.model.UpdateStackResult
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeployCloudFormationDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Specification

class DeployCloudFormationAtomicOperationSpec extends Specification {
  void setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should build a CreateStackRequest and submit through aws client"() {
    given:
    def amazonClientProvider = Mock(AmazonClientProvider)
    def amazonCloudFormation = Mock(AmazonCloudFormation)
    def createStackResult = Mock(CreateStackResult)
    def stackId = "stackId"
    def op = new DeployCloudFormationAtomicOperation(
      new DeployCloudFormationDescription(
        [
          stackName: "stackTest",
          region: "eu-west-1",
          templateBody: [ key: "value" ],
          parameters: [ key: "value"],
          credentials: TestCredential.named("test")
        ]
      )
    )
    op.amazonClientProvider = amazonClientProvider
    op.objectMapper = new ObjectMapper()

    when:
    op.operate([])

    then:
    1 * amazonClientProvider.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    1 * amazonCloudFormation.createStack(_) >> { CreateStackRequest request ->
      assert request.getStackName() == "stackTest"
      assert request.getTemplateBody() == '{"key":"value"}'
      assert request.getParameters() == [ new Parameter().withParameterKey("key").withParameterValue("value") ]
      createStackResult
    }
    1 * createStackResult.getStackId() >> stackId
  }

  void "should build an UpdateStackRequest if stack exists and submit through aws client"() {
    given:
    def amazonClientProvider = Mock(AmazonClientProvider)
    def amazonCloudFormation = Mock(AmazonCloudFormation)
    def updateStackRequest = Mock(UpdateStackResult)
    def stackId = "stackId"
    def op = new DeployCloudFormationAtomicOperation(
      new DeployCloudFormationDescription(
        [
          stackName: "stackTest",
          region: "eu-west-1",
          templateBody: [ key: "value" ],
          parameters: [ key: "value"],
          credentials: TestCredential.named("test")
        ]
      )
    )
    op.amazonClientProvider = amazonClientProvider
    op.objectMapper = new ObjectMapper()

    when:
    op.operate([])

    then:
    1 * amazonClientProvider.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    1 * amazonCloudFormation.createStack(_) >> { throw new AlreadyExistsException() }
    1 * amazonCloudFormation.updateStack(_) >> { UpdateStackRequest request ->
      assert request.getStackName() == "stackTest"
      assert request.getTemplateBody() == '{"key":"value"}'
      assert request.getParameters() == [ new Parameter().withParameterKey("key").withParameterValue("value") ]
      updateStackRequest
    }
    1 * updateStackRequest.getStackId() >> stackId
  }
}
