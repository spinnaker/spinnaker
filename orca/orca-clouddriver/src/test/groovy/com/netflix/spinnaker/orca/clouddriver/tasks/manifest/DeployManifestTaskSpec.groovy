/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.security.AuthenticatedRequest
import spock.lang.Specification
import spock.lang.Subject

class DeployManifestTaskSpec extends Specification {
  String TASK_ID = "12345"

  KatoService katoService = Mock(KatoService)

  @Subject
  DeployManifestTask task = new DeployManifestTask(katoService)

  def "enables traffic when the trafficManagement field is absent"() {
    given:
    def stage = createStage([:])

    when:
    task.execute(stage)

    then:
    1 * katoService.requestOperations("kubernetes", {
      Map it -> it.deployManifest.enableTraffic == true && !it.deployManifest.services
    }) >> new TaskId(TASK_ID)
    0 * katoService._
  }

  def "enables traffic when trafficManagement is disabled"() {
    given:
    def stage = createStage([
        trafficManagement: [
            enabled: false
        ]
    ])

    when:
    task.execute(stage)

    then:
    1 * katoService.requestOperations("kubernetes", {
      Map it -> it.deployManifest.enableTraffic == true && !it.deployManifest.services
    }) >> new TaskId(TASK_ID)
    0 * katoService._
  }

  def "enables traffic when trafficManagement is enabled and explicitly enables traffic"() {
    given:
    def stage = createStage([
      trafficManagement: [
        enabled: true,
        options: [
            enableTraffic: true,
            services: ["service my-service"]
        ]
      ]
    ])

    when:
    task.execute(stage)

    then:
    1 * katoService.requestOperations("kubernetes", {
      Map it -> it.deployManifest.enableTraffic == true && it.deployManifest.services == ["service my-service"]
    }) >> new TaskId(TASK_ID)
    0 * katoService._
  }

  def "does not enable traffic when trafficManagement is enabled and enableTraffic is disabled"() {
    given:
    def stage = createStage([
      trafficManagement: [
        enabled: true,
        options: [
          enableTraffic: false,
          services: ["service my-service"]
        ]
      ]
    ])

    when:
    task.execute(stage)

    then:
    1 * katoService.requestOperations("kubernetes", {
      Map it -> it.deployManifest.enableTraffic == false && it.deployManifest.services == ["service my-service"]
    }) >> new TaskId(TASK_ID)
    0 * katoService._
  }


  def "adds provenance annotations from authenticated request"() {
    given:
    def stage = createStage([:])
    AuthenticatedRequest.setUser("mdc-user")

    when:
    def ops = DeployManifestTask.getOperation(stage)

    then:
    ops.deployManifest."provenance.deployedBy" == "mdc-user"
    ops.deployManifest."provenance.executionId" == stage.getExecution().getId()

    cleanup:
    AuthenticatedRequest.clear()
  }

  def "falls back to execution authentication when request context is empty"() {
    given:
    def execution = new PipelineExecutionImpl(ExecutionType.PIPELINE, "test")
    execution.setAuthentication(new PipelineExecution.AuthenticationDetails("execution-user", new String[0]))
    def stage = new StageExecutionImpl(execution, "deployManifest", [
      account: "my-k8s-account",
      cloudProvider: "kubernetes",
      source: "text",
      manifests: []
    ])

    when:
    def ops = DeployManifestTask.getOperation(stage)

    then:
    ops.deployManifest."provenance.deployedBy" == "execution-user"
    ops.deployManifest."provenance.executionId" == execution.getId()
  }

  def createStage(Map extraParams) {
    return new StageExecutionImpl(Stub(PipelineExecutionImpl), "deployManifest", [
      account: "my-k8s-account",
      cloudProvider: "kubernetes",
      source: "text",
      manifests: []
    ] + extraParams)
  }
}
