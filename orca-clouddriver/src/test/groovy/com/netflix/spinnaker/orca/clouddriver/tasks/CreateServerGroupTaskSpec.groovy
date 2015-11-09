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

package com.netflix.spinnaker.orca.clouddriver.tasks

import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import rx.Observable
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class CreateServerGroupTaskSpec extends Specification {

  @Shared
  ServerGroupCreator aCreator = Stub(ServerGroupCreator) {
    getCloudProvider() >> "aCloud"
    isKatoResultExpected() >> false
    getOperations(_) >> [["aOp": "foo"]]
  }
  @Shared
  ServerGroupCreator bCreator = Stub(ServerGroupCreator) {
    getCloudProvider() >> "bCloud"
    isKatoResultExpected() >> false
    getOperations(_) >> [["bOp": "bar"]]
  }
  @Shared
  ServerGroupCreator cCreator = Stub(ServerGroupCreator) {
    getCloudProvider() >> "cCloud"
    isKatoResultExpected() >> true
    getOperations(_) >> [["cOp": "baz"]]
  }
  @Shared
  TaskId taskId = new TaskId(UUID.randomUUID().toString())

  @Shared
  def baseOutput = [
      "notification.type"  : "createdeploy",
      "kato.last.task.id"  : taskId,
      "deploy.account.name": "abc"
  ]

  @Unroll
  def "should have cloud provider-specific outputs"() {
    given:
      KatoService katoService = Mock(KatoService)
      def task = new CreateServerGroupTask(kato: katoService, serverGroupCreators: [aCreator, bCreator, cCreator])
      def stage = new PipelineStage(new Pipeline(), "whatever", [credentials: "abc", cloudProvider: cloudProvider])

    when:
      def result = task.execute(stage)

    then:
      1 * katoService.requestOperations(cloudProvider, _) >> { Observable.from(taskId) }
      result
      result.stageOutputs == outputs

    where:
      cloudProvider | ops              || outputs
      "aCloud"      | [["aOp": "foo"]] || baseOutput + ["kato.result.expected": false]
      "bCloud"      | [["bOp": "bar"]] || baseOutput + ["kato.result.expected": false]
      "cCloud"      | [["cOp": "baz"]] || baseOutput + ["kato.result.expected": true]
  }
}
