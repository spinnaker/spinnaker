/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.front50.spring

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.front50.DependentPipelineStarter
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.pipeline.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Task
import spock.lang.Specification
import spock.lang.Subject

class DependentPipelineExecutionListenerSpec extends Specification {

  def front50Service = Mock(Front50Service)
  def dependentPipelineStarter = Mock(DependentPipelineStarter)
  def pipelineConfig = [
    triggers: [
      [
        "enabled"    : true,
        "type"       : "pipeline",
        "application": "rush",
        "status"     : [
          "successful", "failed"
        ],
        "pipeline"   : "97c435a0-0faf-11e5-a62b-696d38c37faa"
      ]
    ]
  ]

  def pipeline = new Pipeline().builder().withStage(
    PipelineStage.PIPELINE_CONFIG_TYPE, "pipeline", [:]
  ).build()

  @Subject
  DependentPipelineExecutionListener listener = new DependentPipelineExecutionListener(
    front50Service, dependentPipelineStarter
  )

  def "should trigger downstream pipeline when status and pipelines match"() {
    given:
    pipeline.stages.each {
      it.status = status
      it.tasks = [Mock(Task)]
    }

    pipeline.pipelineConfigId = "97c435a0-0faf-11e5-a62b-696d38c37faa"
    front50Service.getAllPipelines() >> [
      pipelineConfig
    ]

    when:
    listener.afterExecution(null, pipeline, null, true)

    then:
    1 * dependentPipelineStarter.trigger(_, _, _, _, _)

    where:
    status << [ExecutionStatus.SUCCEEDED, ExecutionStatus.TERMINAL]
  }

  def "should not trigger downstream pipeline when conditions don't match"() {
    given:
    pipeline.stages.each {
      it.status = status
      it.tasks = [Mock(Task)]
    }


    pipeline.pipelineConfigId = id

    pipelineConfig.triggers.first().status = ['successful']

    front50Service.getAllPipelines() >> [
      pipelineConfig
    ]

    when:
    listener.afterExecution(null, pipeline, null, true)

    then:
    0 * dependentPipelineStarter._

    where:
    status                      | id
    ExecutionStatus.TERMINAL    | "97c435a0-0faf-11e5-a62b-696d38c37faa"
    ExecutionStatus.NOT_STARTED | "97c435a0-0faf-11e5-a62b-696d38c37faa"
    ExecutionStatus.SUCCEEDED   | "notId"
  }

  def "can trigger multiple pipelines"() {
    given:
    pipeline.stages.each {
      it.status = ExecutionStatus.SUCCEEDED
      it.tasks = [Mock(Task)]
    }

    pipeline.pipelineConfigId = "97c435a0-0faf-11e5-a62b-696d38c37faa"
    front50Service.getAllPipelines() >> [
      pipelineConfig, pipelineConfig, pipelineConfig
    ]

    when:
    listener.afterExecution(null, pipeline, null, true)

    then:
    3 * dependentPipelineStarter._
  }

  def "ignore disabled triggers"() {
    given:
    pipeline.stages.each {
      it.status = ExecutionStatus.SUCCEEDED
      it.tasks = [Mock(Task)]
    }

    pipelineConfig.triggers.first().enabled = false


    pipeline.pipelineConfigId = "97c435a0-0faf-11e5-a62b-696d38c37faa"
    front50Service.getAllPipelines() >> [
      pipelineConfig
    ]

    when:
    listener.afterExecution(null, pipeline, null, true)

    then:
    0 * dependentPipelineStarter._
  }

  def "ignores executions with null pipelineConfigIds"() {
    pipeline.stages.each {
      it.status = ExecutionStatus.SUCCEEDED
      it.tasks = [Mock(Task)]
    }

    pipelineConfig.triggers.first().pipeline = null
    pipeline.pipelineConfigId = null

    front50Service.getAllPipelines() >> [
      pipelineConfig
    ]

    when:
    listener.afterExecution(null, pipeline, null, true)

    then:
    0 * dependentPipelineStarter._
  }
}
