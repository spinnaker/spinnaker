/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.orca.front50.tasks

import com.netflix.spinnaker.orca.extensionpoint.pipeline.ExecutionPreprocessor
import com.netflix.spinnaker.orca.front50.DependentPipelineStarter
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.security.User
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class StartPipelineTaskSpec extends Specification {

  Front50Service front50Service = Mock(Front50Service)
  DependentPipelineStarter dependentPipelineStarter = Stub(DependentPipelineStarter)
  ContextParameterProcessor contextParameterProcessor = new ContextParameterProcessor()
  List<ExecutionPreprocessor> executionPreprocessors

  @Subject
  StartPipelineTask task = new StartPipelineTask(Optional.of(front50Service), dependentPipelineStarter, contextParameterProcessor, Optional.ofNullable(executionPreprocessors))

  @Unroll
  def "should trigger the dependent pipeline with the correct context and parentPipelineStageId"() {
    given:
    def pipelineConfig = [id: "testStrategyId", application: "orca", name: "testStrategy"]
    1 * front50Service.getStrategies(_) >> [pipelineConfig]
    def stage = stage {
      type = "whatever"
      context = [
        pipelineId        : "testStrategyId",
        pipelineParameters: [
          strategy: true,
          zone    : "north-pole-1",
        ],
        deploymentDetails : [
          [
            ami      : "testAMI",
            imageName: "testImageName",
            imageId  : "testImageId",
            zone     : "north-pole-1",
          ]
        ],
        user              : "testUser"
      ]
    }
    stage.execution.authentication = authentication

    def gotContext
    def parentPipelineStageId
    User authenticatedUser

    when:
    def result = task.execute(stage)

    then:
    dependentPipelineStarter.trigger(*_) >> {
      gotContext = it[3] // 3rd arg is context.
      parentPipelineStageId = it[4]
      authenticatedUser = it[5]

      return pipeline {
        id = "testPipelineId"
        application = "orca"
      }
    }
    gotContext == [
      strategy         : true,
      zone             : "north-pole-1",
      amiName          : "testAMI",
      imageId          : "testImageId",
      deploymentDetails: [
        [
          ami      : "testAMI",
          imageName: "testImageName",
          imageId  : "testImageId",
          zone     : "north-pole-1",
        ]
      ]
    ]
    parentPipelineStageId == stage.id

    authenticatedUser?.email == expectedAuthenticatedEmail
    authenticatedUser?.allowedAccounts == expectedAuthenticatedAllowedAccounts

    where:
    authentication || expectedAuthenticatedEmail || expectedAuthenticatedAllowedAccounts
    null           || null                       || null
    new Execution.AuthenticationDetails(
      "authenticated_user",
      "account1"
    )              || "authenticated_user"       || ["account1"]

  }
}
