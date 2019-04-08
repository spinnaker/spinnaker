/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.snapshot

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification

class DeleteSnapshotTaskSpec extends Specification {

  def "Should delete a snapshot"() {
    given:
    def context = [
      cloudProvider: "aws",
      credentials  : "test",
      region       : "us-east-1",
      snapshotIds  : ["snap-08e97a12bceb0b750"]
    ]

    def stage = new Stage(Execution.newPipeline("orca"), "deleteSnapshot", context)

    and:
    List<Map> operations = []
    def katoService = Mock(KatoService) {
      1 * requestOperations("aws", _) >> {
        operations = it[1]
        rx.Observable.from(new TaskId(UUID.randomUUID().toString()))
      }
    }
    def task = new DeleteSnapshotTask(katoService)

    when:
    def result = task.execute(stage)

    then:
    operations.size() == 1
    operations[0].deleteSnapshot.snapshotId == stage.context.snapshotIds[0]
    result.status == ExecutionStatus.SUCCEEDED
  }
}
