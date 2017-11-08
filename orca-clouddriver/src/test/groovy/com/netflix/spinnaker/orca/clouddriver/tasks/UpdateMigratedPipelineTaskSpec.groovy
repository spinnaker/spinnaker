/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.tasks.pipeline.UpdateMigratedPipelineTask
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.kato.pipeline.ParallelDeployClusterExtractor
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject

class UpdateMigratedPipelineTaskSpec extends Specification {

  @Subject
  UpdateMigratedPipelineTask task = new UpdateMigratedPipelineTask()

  Front50Service front50Service
  KatoService katoService
  Map pipeline

  void setup() {
    front50Service = Mock()
    katoService = Mock()
    task.front50Service = front50Service
    task.extractors = [new ParallelDeployClusterExtractor()]
    pipeline = [
      id    : 'abc',
      name  : 'to migrate',
      stages: [
        [type: 'deploy', clusters: [[id: 1], [id: 2]]],
        [type: 'not-a-deploy', clusters: [[id: 3], [id: 4]]],
        [type: 'deploy', clusters: [[id: 5], [id: 6]]]
      ]
    ]
  }

  void 'applies new clusters to pipeline, removes id, updates name, saves, adds new ID to output'() {
    when:
    def context = [
      application      : 'theapp',
      "source.pipeline": pipeline,
      "kato.tasks"     : [
        [
          resultObjects: [
            [cluster: [id: 10]], [cluster: [id: 11]], [cluster: [id: 12]], [cluster: [id: 13]],
          ]
        ]
      ]
    ]
    def result = task.execute(new Stage(Execution.newPipeline("orca"), "updatePipeline", "migrate", context))

    then:
    1 * front50Service.savePipeline({
      it.id == null
      it.stages[0].clusters.id == [10, 11]
      it.stages[1].clusters.id == [3, 4]
      it.stages[2].clusters.id == [12, 13]
    })
    1 * front50Service.getPipelines("theapp") >> [pipeline.clone(), [id: 'def', name: 'to migrate - migrated']]
    result.status == ExecutionStatus.SUCCEEDED
  }

}
