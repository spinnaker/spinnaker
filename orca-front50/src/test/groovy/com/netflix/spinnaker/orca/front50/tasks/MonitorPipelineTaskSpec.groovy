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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.front50.pipeline.MonitorPipelineStage
import com.netflix.spinnaker.orca.front50.pipeline.PipelineStage
import com.netflix.spinnaker.orca.pipeline.WaitStage
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class MonitorPipelineTaskSpec extends Specification {

  ExecutionRepository repo = Mock(ExecutionRepository)
  StageExecutionImpl stage = new StageExecutionImpl()

  @Subject
  MonitorPipelineTask task = new MonitorPipelineTask(repo, new ObjectMapper())

  def setup() {
    stage.context.executionId = 'abc'
    stage.type = 'whatever'
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
    ExecutionStatus.CANCELED    || ExecutionStatus.CANCELED
    ExecutionStatus.TERMINAL    || ExecutionStatus.TERMINAL
    ExecutionStatus.REDIRECT    || ExecutionStatus.RUNNING
  }

  def "propagates artifacts from child pipeline"() {
    given:
    def pipeline = pipeline {
      application = "orca"
      stage {
        type = PipelineStage.PIPELINE_CONFIG_TYPE
        name = "pipeline"
        outputs = [
          artifacts: [
            [type     : "docker/image",
             reference: "gcr.io/project/my-image@sha256:28f82eba",
             name     : "gcr.io/project/my-image",
             version  : "sha256:28f82eba"],
          ]
        ]
      }
      status = ExecutionStatus.SUCCEEDED
    }

    repo.retrieve(*_) >> pipeline

    when:
    def result = task.execute(stage)

    then:
    result.outputs.containsKey("artifacts")
    result.outputs["artifacts"].size == 1
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
      source : [
        executionId: pipeline.id,
        stageId    : pipeline.stages[1].id,
        stageName  : "a pipeline",
        stageIndex : 1
      ]
    ]
  }

  def "propagates child pipeline outputs"() {
    def pipeline = pipeline {
      application = "orca"
      name = "a pipeline"
      stage {
        type = PipelineStage.PIPELINE_CONFIG_TYPE
        name = "stage with outputs"
        outputs = [
          "myVar1": "myValue1",
          "myVar2": "myValue2"
        ]
      }
      status = ExecutionStatus.SUCCEEDED
    }

    repo.retrieve(*_) >> pipeline

    when:
    def result = task.execute(stage)
    def outputs = result.outputs

    then:
    outputs == [
      "myVar1": "myValue1",
      "myVar2": "myValue2"
    ]
  }

  def "propagates failed child pipeline outputs"() {
    def pipeline = pipeline {
      application = "orca"
      name = "a pipeline"
      stage {
        type = PipelineStage.PIPELINE_CONFIG_TYPE
        name = "failed stage with outputs"
        outputs = [
          "myVar1": "myValue1",
          "myVar2": "myValue2"
        ]
        status = ExecutionStatus.TERMINAL
      }
      status = ExecutionStatus.TERMINAL
    }

    repo.retrieve(*_) >> pipeline

    when:
    def result = task.execute(stage)
    def outputs = result.outputs

    then:
    outputs == [
      "myVar1": "myValue1",
      "myVar2": "myValue2"
    ]
  }

  def "do not propagate running child pipeline outputs"() {
    def pipeline = pipeline {
      application = "orca"
      name = "a pipeline"
      stage {
        type = PipelineStage.PIPELINE_CONFIG_TYPE
        name = "running stage with outputs"
        outputs = [
            "myVar1": "myValue1",
            "myVar2": "myValue2"
        ]
        status = ExecutionStatus.RUNNING
      }
      status = ExecutionStatus.RUNNING
    }

    repo.retrieve(*_) >> pipeline

    when:
    def result = task.execute(stage)
    def outputs = result.outputs

    then:
    outputs == [:]
  }

  @Unroll
  def "respect #behavior behavior when monitoring multiple pipelines"() {
    ObjectMapper objectMapper = new ObjectMapper()

    def child1 = pipeline {
      application = "orca"
      name = "child 1"
      stage {
        type = WaitStage.typeName
        name = "Wait that failed"
        status = ExecutionStatus.TERMINAL
      }
      status = ExecutionStatus.TERMINAL
    }

    def child2 = pipeline {
      application = "orca"
      name = "child 2"
      stage {
        type = WaitStage.typeName
        name = "Wait that is running"
        status = ExecutionStatus.RUNNING
      }
      status = ExecutionStatus.RUNNING
    }

    MonitorPipelineStage.StageParameters parameters = new MonitorPipelineStage.StageParameters()
    parameters.executionIds = [child1.id, child2.id]
    parameters.monitorBehavior = MonitorPipelineStage.MonitorBehavior.FailFast

    def parent = pipeline {
      application = "orca"
      name = "a pipeline"
      stage {
        type = MonitorPipelineStage.PIPELINE_CONFIG_TYPE
        name = "Monitor multiple executions"
      }
    }

    parent.stages[0].context.putAll(objectMapper.convertValue(parameters, Map))

    repo.retrieve(ExecutionType.PIPELINE, child1.id) >> child1
    repo.retrieve(ExecutionType.PIPELINE, child2.id) >> child2

    when:
    def result = task.execute(parent.stages[0])
    def typedResult =  objectMapper.convertValue(result.outputs, MonitorPipelineStage.StageResult.class)


    then:
    result.status == ExecutionStatus.TERMINAL
    typedResult.executionStatuses.size() == 2
    typedResult.executionStatuses[child1.id].status == child1.status
    typedResult.executionStatuses[child2.id].status == child2.status
    parent.stages[0].context.exception.details.errors[0] == "At least one monitored pipeline failed, look for errors in failed pipelines"

    where:
    behavior                                                    || expectedStatus
    MonitorPipelineStage.MonitorBehavior.FailFast               || ExecutionStatus.TERMINAL
    MonitorPipelineStage.MonitorBehavior.WaitForAllToComplete   || ExecutionStatus.RUNNING
  }

  @Unroll
  def "handles a missing execution id (stage type: #stageType)"() {
    given:
    stage.context.remove('executionId')
    stage.type = stageType

    when:
    task.execute(stage)

    then:
    noExceptionThrown()
    0 * repo.retrieve(PIPELINE, _)

    where:
    stageType << [ MonitorPipelineStage.PIPELINE_CONFIG_TYPE, PipelineStage.PIPELINE_CONFIG_TYPE ]
  }

  @Unroll
  def "handles a null execution id (stage type: #stageType)"() {
    given:
    stage.context.executionId = null
    stage.context.executionIds = [null]
    stage.type = stageType

    when:
    task.execute(stage)

    then:
    noExceptionThrown()
    0 * repo.retrieve(PIPELINE, _)

    where:
    stageType << [ MonitorPipelineStage.PIPELINE_CONFIG_TYPE, PipelineStage.PIPELINE_CONFIG_TYPE ]
  }

}
