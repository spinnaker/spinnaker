/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.tide.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.tide.TideService
import com.netflix.spinnaker.orca.tide.model.TideTask
import com.netflix.spinnaker.orca.tide.pipeline.DeepCopyServerGroupStage
import spock.lang.Specification
import spock.lang.Subject

class WaitForTideTaskSpec extends Specification {
  @Subject
  def task = new WaitForTideTask()

  def config = [
      source: [
          account: "test",
          region: "us-east-1",
          asgName: "app-v001",
      ],
      target: [:],
      "tide.task.id": "3"
  ]

  void 'should return status of COMPLETED when taskResult exists'() {

    given:
    task.tideService = Mock(TideService)

    and:
    def pipeline = new Pipeline()
    pipeline.stages = [new PipelineStage(pipeline, DeepCopyServerGroupStage.MAYO_CONFIG_TYPE)]

    when:
    def taskResult = task.execute(new PipelineStage(pipeline, "DeepCopyServerGroup", config))

    then:
    task.tideService.getTask(config."tide.task.id") >> [taskComplete: new TideTask.TaskComplete()]
    taskResult.status == ExecutionStatus.SUCCEEDED
  }

  void 'should return status of RUNNING when no taskResult returned'() {
    given:
    task.tideService = Mock(TideService)

    and:
    def pipeline = new Pipeline()
    pipeline.stages = [new PipelineStage(pipeline, DeepCopyServerGroupStage.MAYO_CONFIG_TYPE)]

    when:
    def taskResult = task.execute(new PipelineStage(pipeline, "DeepCopyServerGroup", config))

    then:
    task.tideService.getTask(config."tide.task.id") >> [:]
    taskResult.status == ExecutionStatus.RUNNING
  }
}
