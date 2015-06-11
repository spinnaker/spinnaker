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

package com.netflix.spinnaker.orca.mayo.spring

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.echo.spring.DependentPipelineExecutionListener
import com.netflix.spinnaker.orca.mayo.DependentPipelineStarter
import com.netflix.spinnaker.orca.mayo.MayoService
import com.netflix.spinnaker.orca.mayo.pipeline.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Task
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParameter
import org.springframework.batch.core.JobParameters
import spock.lang.Specification
import spock.lang.Subject

class DependentPipelineExecutionListenerSpec extends Specification {

  def mayoService = Mock(MayoService)
  def executionRepository = Mock(ExecutionRepository)
  def dependentPipelineStarter = Mock(DependentPipelineStarter)
  def jobExecution = new JobExecution(0L, new JobParameters([pipeline: new JobParameter('something')]))
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
    PipelineStage.MAYO_CONFIG_TYPE, "pipeline", [:]
  ).build()

  @Subject
  DependentPipelineExecutionListener listener = new DependentPipelineExecutionListener(executionRepository, mayoService, dependentPipelineStarter)

  def "should trigger downstream pipeline when status and pipelines match"() {
    given:
    pipeline.stages.each {
      it.status = status
      it.tasks = [Mock(Task)]
    }

    pipeline.pipelineConfigId = "97c435a0-0faf-11e5-a62b-696d38c37faa"
    executionRepository.retrievePipeline(_) >> pipeline
    mayoService.getAllPipelines() >> [
      pipelineConfig
    ]

    when:
    listener.afterJob(jobExecution)

    then:
    1 * dependentPipelineStarter.trigger(_, _, _, _)

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
    executionRepository.retrievePipeline(_) >> pipeline

    pipelineConfig.triggers.first().status = ['successful']

    mayoService.getAllPipelines() >> [
      pipelineConfig
    ]

    when:
    listener.afterJob(jobExecution)

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
    executionRepository.retrievePipeline(_) >> pipeline
    mayoService.getAllPipelines() >> [
      pipelineConfig, pipelineConfig, pipelineConfig
    ]

    when:
    listener.afterJob(jobExecution)

    then:
    3 * dependentPipelineStarter._
  }

  def "ignore disabled triggers"(){
    given:
    pipeline.stages.each {
      it.status = ExecutionStatus.SUCCEEDED
      it.tasks = [Mock(Task)]
    }

    pipelineConfig.triggers.first().enabled = false


    pipeline.pipelineConfigId = "97c435a0-0faf-11e5-a62b-696d38c37faa"
    executionRepository.retrievePipeline(_) >> pipeline
    mayoService.getAllPipelines() >> [
      pipelineConfig
    ]

    when:
    listener.afterJob(jobExecution)

    then:
    0 * dependentPipelineStarter._
  }

}
