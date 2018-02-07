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

package com.netflix.spinnaker.orca.front50.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.front50.pipeline.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class MonitorPipelineTaskSpec extends Specification {


  @Subject
  MonitorPipelineTask task = new MonitorPipelineTask()
  ExecutionRepository repo = Mock(ExecutionRepository)
  Stage stage = new Stage(type: "whatever")

  def setup() {
    task.executionRepository = repo
    stage.context.executionId = 'abc'
  }

  @Unroll
  def "returns the correct task result based on child pipeline execution"() {
    given:
    def pipeline = pipeline {
      application = "orca"
      stage {
        type = PipelineStage.PIPELINE_CONFIG_TYPE
        name = "pipeline"
      }
      status = providedStatus
    }

    repo.retrieve(*_) >> pipeline

    when:
    def result = task.execute(stage)

    then:
    result.status == expectedStatus

    where:
    providedStatus              || expectedStatus
    ExecutionStatus.SUCCEEDED   || ExecutionStatus.SUCCEEDED
    ExecutionStatus.RUNNING     || ExecutionStatus.RUNNING
    ExecutionStatus.NOT_STARTED || ExecutionStatus.RUNNING
    ExecutionStatus.SUSPENDED   || ExecutionStatus.RUNNING
    ExecutionStatus.CANCELED    || ExecutionStatus.TERMINAL
    ExecutionStatus.TERMINAL    || ExecutionStatus.TERMINAL
    ExecutionStatus.REDIRECT    || ExecutionStatus.RUNNING
  }

  def "propagates pipeline exceptions"() {
    def katoTasks = [
      [status: [failed: false], messages: ["should not appear"]],
      [status: [failed: true], history: ["task failed, no exception"]],
      [status: [failed: true], history: ["should not appear"], exception: [message: "task had exception"]],
    ]
    def pipeline = pipeline {
      application = "orca"
      name = "some child"
      stage {
        type = PipelineStage.PIPELINE_CONFIG_TYPE
        name = "other"
      }
      stage {
        type = PipelineStage.PIPELINE_CONFIG_TYPE
        name = "a pipeline"
        status = ExecutionStatus.TERMINAL
        context = [exception: [details: [errors: "Some error"]]]
      }
      stage {
        type = PipelineStage.PIPELINE_CONFIG_TYPE
        name = "pipeline"
        status = ExecutionStatus.TERMINAL
        context = [exception: [details: [errors: "Some other error"]]]
      }
      stage {
        type = PipelineStage.PIPELINE_CONFIG_TYPE
        name = "deploy"
        status = ExecutionStatus.TERMINAL
        context = ["kato.tasks": katoTasks]
      }
      status = ExecutionStatus.TERMINAL
    }

    repo.retrieve(*_) >> pipeline

    when:
    def result = task.execute(stage)
    def exception = result.context.exception

    then:
    exception == [
      details: [
        errors: [
          "Exception in child pipeline stage (some child: a pipeline): Some error",
          "Exception in child pipeline stage (some child: pipeline): Some other error",
          "Exception in child pipeline stage (some child: deploy): task failed, no exception",
          "Exception in child pipeline stage (some child: deploy): task had exception"
        ]
      ],
      // source should reference the _first_ halted stage in the child pipeline
      source: [
        executionId: pipeline.id,
        stageId: pipeline.stages[1].id,
        stageName: "a pipeline",
        stageIndex: 1
      ]
    ]
  }
}
