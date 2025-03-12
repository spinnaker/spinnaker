/*
 * Copyright 2021 Expedia, Inc.
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

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification
import spock.lang.Subject

class DeleteCloudFormationTaskSpec extends Specification {

  def katoService = Mock(KatoService)

  @Subject
  def deleteCloudFormationTask = new DeleteCloudFormationTask(katoService: katoService)

  def "should delete stack"() {
    given:
    def taskId = new TaskId(id: 'id')
    def pipeline = PipelineExecutionImpl.newPipeline('orca')
    def context = [
      credentials: 'creds',
      cloudProvider: 'aws',
      stackName: 'stackName',
      regions: ['eu-west-1']
    ]
    def stage = new StageExecutionImpl(pipeline, 'test', 'test', context)

    when:
    def result = deleteCloudFormationTask.execute(stage)

    then:
    1 * katoService.requestOperations("aws", {
      def task = it.get(0).get("deleteCloudFormation")
      task != null
      task.get("stackName").equals(context.get("stackName"))
      task.get("region").equals(context.get("regions")[0])
      task.get("credentials").equals(context.get("credentials"))
    }) >> taskId
    result.getStatus() == ExecutionStatus.SUCCEEDED

  }

}
