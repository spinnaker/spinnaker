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
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.clouddriver.tasks.pipeline.MigratePipelineClustersTask
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.kato.pipeline.ParallelDeployClusterExtractor
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject

class MigratePipelineClustersTaskSpec extends Specification {

  @Subject
  MigratePipelineClustersTask task = new MigratePipelineClustersTask()

  Front50Service front50Service
  KatoService katoService

  void setup() {
    front50Service = Mock()
    katoService = Mock()
    task.front50Service = front50Service
    task.katoService = katoService
    task.extractors = [new ParallelDeployClusterExtractor()]
  }

  void 'returns terminal status when pipeline not found'() {
    when:
    def result = task.execute(new Stage(null, "migratePipelineCluster", "migrate", [pipelineConfigId: 'abc', application: 'theapp']))

    then:
    1 * front50Service.getPipelines('theapp') >> [[id: 'def']]
    result.status == ExecutionStatus.TERMINAL
    result.context.exception == "Could not find pipeline with ID abc"
  }

  void 'extracts clusters, sends them to clouddriver, and puts pipeline into context for later retrieval'() {
    given:
    def pipeline = [
      id    : 'abc',
      stages: [
        [type: 'deploy', clusters: [[id: 1], [id: 2]]],
        [type: 'not-a-deploy', clusters: [[id: 3], [id: 4]]],
        [type: 'deploy', clusters: [[id: 5], [id: 6]]]
      ]
    ]
    def context = [
      pipelineConfigId: 'abc',
      application     : 'theapp',
      regionMapping   : ['us-east-1': 'us-west-1']
    ]
    def stage = new Stage(Execution.newPipeline("orca"), "mpc", "m", context)

    when:
    def result = task.execute(stage)

    then:
    1 * front50Service.getPipelines('theapp') >> [pipeline]
    1 * katoService.requestOperations('aws', {
      it[0].migrateClusterConfigurations.sources.cluster.id == [1,2,3,4]
      it[0].migrateClusterConfigurations.regionMapping == ['us-east-1': 'us-west-1']
    }) >> rx.Observable.from([new TaskId(id: "1")])
    result.context['source.pipeline'] == pipeline
  }

}
