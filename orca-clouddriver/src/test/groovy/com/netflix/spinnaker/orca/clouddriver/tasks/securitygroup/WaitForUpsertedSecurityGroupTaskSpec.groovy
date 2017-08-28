/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.securitygroup

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.MortService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification

class WaitForUpsertedSecurityGroupTaskSpec extends Specification {

  def "should result in successful task"() {
    given:
      def securityGroupName
      SecurityGroupUpserter aUpserter = Mock(SecurityGroupUpserter) {
        getCloudProvider() >> "aCloud"
        1 * isSecurityGroupUpserted(*_) >> {
          securityGroupName = it[0].name
          true
        }
      }
      def task = new WaitForUpsertedSecurityGroupTask(securityGroupUpserters: [aUpserter])
    def stage = new Stage<>(new Pipeline("orca"), "whatever", [
          credentials  : "abc",
          cloudProvider: "aCloud",
          targets      : [new MortService.SecurityGroup(name: "abc")]
      ])

    when:
      def result = task.execute(stage)

    then:
      result
      result.status == ExecutionStatus.SUCCEEDED
      securityGroupName == "abc"

  }

  def "should result in running if not upserted yet"() {
    given:
    SecurityGroupUpserter aUpserter = Stub(SecurityGroupUpserter) {
      getCloudProvider() >> "aCloud"
      isSecurityGroupUpserted(*_) >>> [false, true]
    }
    def task = new WaitForUpsertedSecurityGroupTask(securityGroupUpserters: [aUpserter])
    def ctx = [
      credentials  : "abc",
      cloudProvider: "aCloud",
      targets      : [new MortService.SecurityGroup(name: "abc")]
    ]
    def stage = new Stage<>(new Pipeline("orca"), "whatever", ctx)

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING

    when:
    result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
  }

  def "should throw an exception if the cloud provider is not recognized"() {
    given:
    def aUpserter = Stub(SecurityGroupUpserter) {
      getCloudProvider() >> "aCloud"
    }
    def task = new WaitForUpsertedSecurityGroupTask(securityGroupUpserters: [aUpserter])
    def ctx = [
      credentials  : "abc",
      cloudProvider: "bCloud",
      targets      : [new MortService.SecurityGroup(name: "abc")]
    ]
    def stage = new Stage<>(new Pipeline("orca"), "whatever", ctx)

    when:
      task.execute(stage)

    then:
      thrown(IllegalStateException)
  }
}
