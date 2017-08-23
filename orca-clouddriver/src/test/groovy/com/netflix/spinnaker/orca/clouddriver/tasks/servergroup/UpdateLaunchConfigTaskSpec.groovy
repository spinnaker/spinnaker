/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject

class UpdateLaunchConfigTaskSpec extends Specification {

  KatoService katoService = Mock(KatoService)

  @Subject
  UpdateLaunchConfigTask task = new UpdateLaunchConfigTask(kato: katoService, defaultBakeAccount: "default")

  void "should populate deploy.server.groups to enable force cache refresh"() {
    setup:
      def taskConfig = [
          credentials: "test",
          region     : region,
          asgName    : asgName
      ]
      def stage = new Stage<>(new Pipeline(), "whatever", taskConfig)

    when:
      def result = task.execute(stage)

    then:
      1 * katoService.requestOperations("aws", _) >> rx.Observable.just(new TaskId("blerg"))
    result.context."deploy.server.groups" == [(region): [asgName]]

    where:
      region = "us-east-1"
      asgName = "myasg-v001"
  }

  void "should add amiName from upstream task if not specified"() {
    setup:
      def taskConfig = [
          credentials      : "test",
          region           : region,
          asgName          : asgName,
          deploymentDetails: [
              [ami: deploymentDetailsAmi, region: region, cloudProvider: "aws"]
          ],
          amiName          : contextAmi
      ]
      def stage = new Stage<>(new Pipeline(), "whatever", taskConfig)

    when:
      def result = task.execute(stage)

    then:
      1 * katoService.requestOperations("aws", _) >> { cloudProvider, ops ->
        def opConfig = ops.last().updateLaunchConfig

        assert opConfig.amiName == expectedAmi
        rx.Observable.just(new TaskId("blerg"))
      }

    where:
      deploymentDetailsAmi | contextAmi | expectedAmi
      "ami-dd"             | "ami-cc"   | "ami-cc"
      "ami-dd"             | null       | "ami-dd"
      region = "us-east-1"
      asgName = "myasg-v001"
  }

  def "prefers the ami from an upstream stage to one from deployment details"() {
    given:
      def taskConfig = [
          credentials      : "test",
          region           : region,
          asgName          : asgName,
          deploymentDetails: [
              ["ami": "not-my-ami", "region": region, cloudProvider: "aws"],
              ["ami": "also-not-my-ami", "region": region, cloudProvider: "aws"]
          ]
      ]
      def stage = new Stage<>(new Pipeline(), "whatever", taskConfig)
      stage.context.amiName = null
      stage.context.deploymentDetails = [
          ["ami": "not-my-ami", "region": region, cloudProvider: "aws"],
          ["ami": "also-not-my-ami", "region": region, cloudProvider: "aws"]
      ]


    and:
      def operations = []
      interaction {
        def taskId = new TaskId(UUID.randomUUID().toString())
        katoService.requestOperations(*_) >> { cloudProvider, ops ->
          operations.addAll(ops.flatten())
          Observable.from(taskId)
        }
      }

    and:
      def bakeStage1 = new Stage<>(stage.execution, "bake")
      bakeStage1.id = UUID.randomUUID()
      bakeStage1.refId = "1a"
      stage.execution.stages << bakeStage1

      def bakeSynthetic1 =
        new Stage<>(stage.execution, "bake in $region", [ami: amiName, region: region, cloudProvider: "aws"])
      bakeSynthetic1.id = UUID.randomUUID()
      bakeSynthetic1.parentStageId = bakeStage1.id
      stage.execution.stages << bakeSynthetic1

      def bakeStage2 = new Stage<>(stage.execution, "bake")
      bakeStage2.id = UUID.randomUUID()
      bakeStage2.refId = "2a"
      stage.execution.stages << bakeStage2

      def bakeSynthetic2 = new Stage<>(stage.execution, "bake in $region",
                                             [ami: "parallel-branch-ami", region: region, cloudProvider: "aws"])
      bakeSynthetic2.id = UUID.randomUUID()
      bakeSynthetic2.parentStageId = bakeStage2.id
      stage.execution.stages << bakeSynthetic2

      def intermediateStage = new Stage<>(stage.execution, "whatever")
      intermediateStage.id = UUID.randomUUID()
      intermediateStage.refId = "1b"
      stage.execution.stages << intermediateStage

    and:
      intermediateStage.requisiteStageRefIds = [bakeStage1.refId]
      stage.requisiteStageRefIds = [intermediateStage.refId]

    when:
    task.execute(stage)

    then:
      operations.find {
        it.containsKey("updateLaunchConfig")
      }.updateLaunchConfig.amiName == amiName

    where:
      amiName = "ami-name-from-bake"
      region = "us-east-1"
      asgName = "myasg-v001"
  }

  void "should inject allowLaunch if deploy account does not match bake account"() {
    setup:
      def taskConfig = [
          credentials: credentials,
          region     : region,
          asgName    : asgName,
          amiName    : "ami-abcdef"
      ]
      def stage = new Stage<>(new Pipeline(), "whatever", taskConfig)

    when:
      task.execute(stage)

    then:
      1 * katoService.requestOperations("aws", _) >> { cloudProvider, ops ->
        assert ops.size() == expectedOpsSize
        if (expectedOpsSize == 2) {
          def allowLaunch = ops.first().allowLaunchDescription
          assert allowLaunch.account == credentials
          assert allowLaunch.credentials == "default"
          assert allowLaunch.region == region
          assert allowLaunch.amiName == "ami-abcdef"
        }
        rx.Observable.just(new TaskId("blerg"))
      }

    where:
      credentials | expectedOpsSize
      "default"   | 1
      "test"      | 2
      region = "us-east-1"
      asgName = "myasg-v001"
  }

  def "should pass context unchanged with non-AWS cloud provider"() {
    setup:
      def taskConfig = [
          credentials  : "default",
          region       : "north-pole",
          foo          : "bar",
          cloudProvider: "abc"
      ]
      def stage = new Stage<>(new Pipeline(), "whatever", taskConfig)

    when:
      task.execute(stage)

    then:
      1 * katoService.requestOperations("abc", _) >> { cloudProvider, ops ->
        assert ops.size() == 1
        ops.find {
          it.containsKey("updateLaunchConfig")
        }.updateLaunchConfig == taskConfig
        rx.Observable.just(new TaskId("blerg"))
      }
  }
}
