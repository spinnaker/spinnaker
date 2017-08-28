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

import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import rx.Observable
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class UpsertSecurityGroupTaskSpec extends Specification {

  @Shared
  SecurityGroupUpserter aUpserter = Stub(SecurityGroupUpserter) {
    getCloudProvider() >> "aCloud"
    getOperationContext(_) >> new SecurityGroupUpserter.OperationContext([["aOp": "foo"]], ["aOp-extra": "bar"])
  }

  @Shared
  SecurityGroupUpserter bUpserter = Stub(SecurityGroupUpserter) {
    getCloudProvider() >> "bCloud"
    getOperationContext(_) >> new SecurityGroupUpserter.OperationContext([["bOp": "bar"]], ["bOp-extra": "baz"])
  }

  @Shared
  def taskId = new TaskId(UUID.randomUUID().toString())

  @Shared
  def baseOutput = [
      "notification.type": "upsertsecuritygroup",
      "kato.last.task.id": taskId,
  ]

  @Unroll
  def "should invoke the correct upserter"() {
    given:
      KatoService katoService = Mock(KatoService)
      def task = new UpsertSecurityGroupTask(kato: katoService, securityGroupUpserters: [aUpserter, bUpserter])
    def stage = new Stage<>(new Pipeline("orca"), "whatever", [credentials: "abc", cloudProvider: cloudProvider])

    when:
      def result = task.execute(stage)

    then:
      1 * katoService.requestOperations(cloudProvider, ops) >> { Observable.from(taskId) }
      result
    result.context == outputs

    where:
      cloudProvider | ops              || outputs
      "aCloud"      | [["aOp": "foo"]] || baseOutput + ["aOp-extra": "bar"]
      "bCloud"      | [["bOp": "bar"]] || baseOutput + ["bOp-extra": "baz"]
  }
}
