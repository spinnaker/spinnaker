/*
 * Copyright (c) 2019 Schibsted Media Group.
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
package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.cloudformation

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.client.Response
import retrofit.mime.TypedString
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class DeleteCloudFormationChangeSetTaskSpec extends Specification {

  def katoService = Mock(KatoService)
  def oortService = Mock(OortService)
  def objectMapper = new ObjectMapper()

  @Subject
  def deleteCloudFormationChangeSetTask = new DeleteCloudFormationChangeSetTask(katoService: katoService)

  def "should delete change set if requested by the context"() {
    given:
    def taskId = new TaskId(id: 'id')
    def pipeline = Execution.newPipeline('orca')
    def context = [
      credentials: 'creds',
      cloudProvider: 'aws',
      stackName: 'stackName',
      changeSetName: 'changeSetName',
      deleteChangeSet: true,
      regions: ['eu-west-1']
    ]
    def stage = new Stage(pipeline, 'test', 'test', context)

    when:
    def result = deleteCloudFormationChangeSetTask.execute(stage)

    then:
    1 * katoService.requestOperations("aws", {
      it.get(0).get("deleteCloudFormationChangeSet") != null
    }) >> Observable.just(taskId)
    result.getStatus() == ExecutionStatus.SUCCEEDED

  }

  def "should succeed deleting change set if not requested by the context"() {
    given:
    def pipeline = Execution.newPipeline('orca')
    def context = [
      credentials: 'creds',
      cloudProvider: 'aws',
      stackName: 'stackName',
      changeSetName: 'changeSetName',
      deleteChangeSet: false,
      regions: ['eu-west-1']
    ]
    def stage = new Stage(pipeline, 'test', 'test', context)

    when:
    def result = deleteCloudFormationChangeSetTask.execute(stage)

    then:
    result.getStatus() == ExecutionStatus.SUCCEEDED

  }

}
