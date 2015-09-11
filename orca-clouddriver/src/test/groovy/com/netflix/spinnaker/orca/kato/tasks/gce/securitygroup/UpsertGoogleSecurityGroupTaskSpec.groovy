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

package com.netflix.spinnaker.orca.kato.tasks.gce.securitygroup

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Subject

class UpsertGoogleSecurityGroupTaskSpec extends Specification {

  @Subject task = new UpsertGoogleSecurityGroupTask()
  def stage = new PipelineStage(type: "whatever")
  def taskId = new TaskId(UUID.randomUUID().toString())

  def upsertGoogleSecurityGroupConfig = [
    cloudProvider   : "gce",
    name            : "test-security-group",
    description     : "Some description...",
    region          : "global",
    credentials     : "fzlem",
    firewallRuleName: "mysecuritygroup",
    network         : "default",
    sourceRanges    : [
      "192.168.0.0/16"
    ],
    allowed         : [
      [
        ipProtocol: "tcp",
        portRanges: [
          80
        ]
      ]
    ]
  ]

  def setup() {
    stage.context.putAll(upsertGoogleSecurityGroupConfig)
  }

  def "creates an upsert google security group task based on job parameters"() {
    given:
      def operations
      task.kato = Mock(KatoService) {
        1 * requestOperations(stage.context.cloudProvider, _) >> {
          operations = it[1]
          rx.Observable.from(taskId)
        }
      }

    when:
      task.execute(stage.asImmutable())

    then:
      operations.size() == 1
      with(operations[0].upsertSecurityGroup) {
        it instanceof Map
        name == this.upsertGoogleSecurityGroupConfig.name
        description == this.upsertGoogleSecurityGroupConfig.description
        region == this.upsertGoogleSecurityGroupConfig.region
        credentials == this.upsertGoogleSecurityGroupConfig.credentials
        firewallRuleName == this.upsertGoogleSecurityGroupConfig.firewallRuleName
        network == this.upsertGoogleSecurityGroupConfig.network
        sourceRanges == this.upsertGoogleSecurityGroupConfig.sourceRanges
        allowed == this.upsertGoogleSecurityGroupConfig.allowed
      }
  }

  def "returns a success status with the kato task id"() {
    given:
      task.kato = Stub(KatoService) {
        requestOperations(*_) >> rx.Observable.from(taskId)
      }

    when:
      def result = task.execute(stage.asImmutable())

    then:
      result.status == ExecutionStatus.SUCCEEDED
      result.outputs."kato.last.task.id" == taskId
      result.outputs."targets" == [
        [
          credentials: upsertGoogleSecurityGroupConfig.credentials,
          region     : upsertGoogleSecurityGroupConfig.region,
          name       : upsertGoogleSecurityGroupConfig.name
        ]
      ]
  }
}
