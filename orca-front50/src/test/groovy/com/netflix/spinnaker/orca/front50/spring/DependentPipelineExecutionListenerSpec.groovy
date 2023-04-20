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

import com.netflix.spinnaker.fiat.shared.FiatStatus
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.TaskExecution
import com.netflix.spinnaker.orca.front50.DependentPipelineStarter
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.config.Front50ConfigurationProperties
import com.netflix.spinnaker.orca.front50.pipeline.PipelineStage
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipelinetemplate.V2Util
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class DependentPipelineExecutionListenerSpec extends Specification {

  def front50Service = Mock(Front50Service)
  def dependentPipelineStarter = Mock(DependentPipelineStarter)
  def pipelineConfig = buildPipelineConfig(null)
  def v2MptPipelineConfig = buildTemplatedPipelineConfig()
  def pipelineConfigWithRunAsUser = buildPipelineConfig("my_run_as_user")
  def contextParameterProcessor = new ContextParameterProcessor()
  def front50ConfigurationProperties = new Front50ConfigurationProperties()
  def templatePreprocessor = [process: {}] // Groovy thunk mock since the actual class is Kotlin and makes compliation fail.

  def pipeline = pipeline {
    application = "orca"
    stage {
      type = PipelineStage.PIPELINE_CONFIG_TYPE
      name = "pipeline"
    }
  }

  def fiatStatus = Mock(FiatStatus) {
    _ * isEnabled() >> { return true }
  }

  @Subject
  DependentPipelineExecutionListener listener = new DependentPipelineExecutionListener(
    front50Service, dependentPipelineStarter, fiatStatus, Optional.of([templatePreprocessor]), contextParameterProcessor, front50ConfigurationProperties
  )

  def "should trigger downstream pipeline when status and pipelines match"() {
    given:
    pipeline.stages.each {
      it.status = status
      it.tasks = [Mock(TaskExecution)]
    }

    pipeline.pipelineConfigId = "97c435a0-0faf-11e5-a62b-696d38c37faa"
    front50Service.getAllPipelines() >> [
      pipelineConfig, pipelineConfigWithRunAsUser
    ]

    when:
    listener.afterExecution(null, pipeline, null, true)

    then:
    1 * dependentPipelineStarter.trigger(_, _, _, _, _, null)
    1 * dependentPipelineStarter.trigger(_, _, _, _, _, { PipelineExecution.AuthenticationDetails user -> user.user == "my_run_as_user" })

    where:
    status << [ExecutionStatus.SUCCEEDED, ExecutionStatus.TERMINAL]
  }

  def "should trigger downstream v2 templated pipeline when status and pipelines match"() {
    given:
    pipeline.stages.each {
      it.status = status
      it.tasks = [Mock(TaskExecution)]
    }

    pipeline.pipelineConfigId = "97c435a0-0faf-11e5-a62b-696d38c37faa"
    front50Service.getAllPipelines() >> [
      pipelineConfig, pipelineConfigWithRunAsUser, v2MptPipelineConfig
    ]
    GroovyMock(V2Util, global: true)
    V2Util.planPipeline(_, _, v2MptPipelineConfig) >> v2MptPipelineConfig

    when:
    listener.afterExecution(null, pipeline, null, true)

    then:
    2 * dependentPipelineStarter.trigger(_, _, _, _, _, null)
    1 * dependentPipelineStarter.trigger(_, _, _, _, _, { PipelineExecution.AuthenticationDetails user -> user.user == "my_run_as_user" })

    where:
    status << [ExecutionStatus.SUCCEEDED, ExecutionStatus.TERMINAL]
  }

  def "should trigger downstream v2 templated pipeline even when templates are invalid"() {
    given:
    pipeline.stages.each {
      it.status = ExecutionStatus.SUCCEEDED
      it.tasks = [Mock(TaskExecution)]
    }

    pipeline.pipelineConfigId = "97c435a0-0faf-11e5-a62b-696d38c37faa"
    front50Service.getAllPipelines() >> [
      v2MptPipelineConfig, v2MptPipelineConfig
    ]
    GroovyMock(V2Util, global: true)
    V2Util.planPipeline(_, _, v2MptPipelineConfig) >>
      {throw new Exception("planning failed")} >>
      v2MptPipelineConfig
    V2Util.isV2Pipeline(_) >> true

    when:
    listener.afterExecution(null, pipeline, null, true)

    then:
    1 * dependentPipelineStarter.trigger(_, _, _, _, _, _)
  }

  def "should not trigger downstream pipeline when conditions don't match"() {
    given:
    pipeline.stages.each {
      it.status = status
      it.tasks = [Mock(TaskExecution)]
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
      it.tasks = [Mock(TaskExecution)]
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
      it.tasks = [Mock(TaskExecution)]
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
      it.tasks = [Mock(TaskExecution)]
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

  @Unroll
  def "uses front50's getTriggeredPipelines endpoint when configured to do so (#status)"() {
    given:
    def origValue = front50ConfigurationProperties.useTriggeredByEndpoint
    front50ConfigurationProperties.setUseTriggeredByEndpoint(true)

    // Set the execution status of the entire pipeline, since that's passed to front50
    pipeline.status = status

    pipeline.pipelineConfigId = "97c435a0-0faf-11e5-a62b-696d38c37faa"
    front50Service.getTriggeredPipelines(pipeline.pipelineConfigId, DependentPipelineExecutionListener.convertStatus(pipeline)) >> [
      pipelineConfig, pipelineConfigWithRunAsUser
    ]

    when:
    listener.afterExecution(null, pipeline, null, true)

    then:
    0 * front50Service.getAllPipelines()
    1 * dependentPipelineStarter.trigger(_, _, _, _, _, null)
    1 * dependentPipelineStarter.trigger(_, _, _, _, _, { PipelineExecution.AuthenticationDetails user -> user.user == "my_run_as_user" })

    cleanup:
    front50ConfigurationProperties.setUseTriggeredByEndpoint(origValue)

    where:
    status << [ExecutionStatus.SUCCEEDED, ExecutionStatus.TERMINAL]
  }

  private static Map buildTemplatedPipelineConfig() {
    return [
      schema: "v2",
      type: "templatedPipeline",
      triggers: [
        [
          "enabled"    : true,
          "type"       : "pipeline",
          "application": "rush",
          "status"     : [
            "successful", "failed"
          ],
          "pipeline"   : "97c435a0-0faf-11e5-a62b-696d38c37faa",
          "runAsUser"  : null
        ]
      ]
    ]
  }

  private static Map buildPipelineConfig(String runAsUser) {
    return [
      triggers: [
        [
          "enabled"    : true,
          "type"       : "pipeline",
          "application": "rush",
          "status"     : [
            "successful", "failed"
          ],
          "pipeline"   : "97c435a0-0faf-11e5-a62b-696d38c37faa",
          "runAsUser"  : runAsUser
        ]
      ]
    ]
  }
}
