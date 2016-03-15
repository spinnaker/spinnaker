/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.batch.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.batch.ExecutionListenerProvider
import com.netflix.spinnaker.orca.pipeline.PipelineJobBuilder
import com.netflix.spinnaker.orca.pipeline.PipelineStartTracker
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import org.springframework.batch.core.JobExecutionListener
import org.springframework.batch.core.configuration.support.MapJobRegistry
import org.springframework.batch.core.job.SimpleJob
import org.springframework.batch.core.launch.JobLauncher
import spock.lang.Specification
import spock.lang.Unroll

class PipelineStarterSpec extends Specification {

  @Unroll
  def "should not start a previously completed execution"() {
    given:
    def executionStarter = getPipelineStarter(sourceExecutionStatus)

    when:
    executionStarter.start("{}")

    then:
    launchInvocations * executionStarter.launcher.run(_, _)

    where:
    sourceExecutionStatus       || launchInvocations
    ExecutionStatus.TERMINAL    || 0
    ExecutionStatus.NOT_STARTED || 1
  }

  @Unroll
  def "should send failure event when exception occurs in new stage"() {
    given:
    def executionStarter = getPipelineStarter(status)
    JobExecutionListener listener = Mock(JobExecutionListener)
    executionStarter.executionListenerProvider = Mock(ExecutionListenerProvider)

    when:
    executionStarter.start("{}")

    then:
    launchInvocations * executionStarter.executionListenerProvider.allJobExecutionListeners() >> { [listener] }
    launchInvocations * listener.afterJob(_)

    where:
    status                      || launchInvocations
    ExecutionStatus.TERMINAL    || 1
    ExecutionStatus.NOT_STARTED || 0
  }

  @Unroll
  def "should queue concurrent executions and start ones that should not be queued"() {

    given:
    def executionStarter = getPipelineStarter(ExecutionStatus.NOT_STARTED)
    executionStarter.startTracker = Mock(PipelineStartTracker)
    executionStarter.startTracker.queueIfNotStarted(_,_) >> isStarted

    when:
    executionStarter.start(pipeline)

    then:
    launched * executionStarter.launcher.run(_, _)

    where:
    limitConcurrent | pipelineConfigId | isStarted | launched | queued
    null            | null             | false     | 1        | 0
    false           | null             | false     | 1        | 0
    true            | '123'            | false     | 1        | 0
    true            | '123'            | true      | 0        | 1

    pipeline = """{
      "limitConcurrent" : ${limitConcurrent},
      "id" : "${pipelineConfigId}"
    }"""

  }

  def "should keep track of started pipelines"() {
    given:
    def executionStarter = getPipelineStarter(ExecutionStatus.NOT_STARTED)
    executionStarter.startTracker = Mock(PipelineStartTracker)
    executionStarter.startTracker.hasStartedExecutions(_) >> true

    when:
    5.times {
      executionStarter.start("""{
        "limitConcurrent" : false,
        "id":"666"
      }""")
    }

    then:
    5 * executionStarter.launcher.run(_, _)
    5 * executionStarter.startTracker.addToStarted("666", _)
  }

  def "should keep track of started pipelines without pipeline id"() {
    given:
    def executionStarter = getPipelineStarter(ExecutionStatus.NOT_STARTED)
    executionStarter.startTracker = Mock(PipelineStartTracker)
    executionStarter.startTracker.hasStartedExecutions(_) >> true

    when:
      executionStarter.start("""{
        "limitConcurrent" : false
      }""")

    then:
    1 * executionStarter.launcher.run(_, _)
    1 * executionStarter.startTracker.addToStarted(null, _)
  }

  private PipelineStarter getPipelineStarter(ExecutionStatus executionStatus) {
    def executionJobBuilder = Stub(PipelineJobBuilder) {
      jobNameFor(_) >> { Pipeline pipeline -> "Pipeline:${pipeline.application}:${pipeline.name}:${pipeline.id}" }
      build(_) >> { Pipeline pipeline ->
        new SimpleJob("Pipeline:${pipeline.application}:${pipeline.name}:${pipeline.id}")
      }
    }
    def pipelineStarter = new PipelineStarter() {
      @Override
      protected void persistExecution(Pipeline subject) {}

      @Override
      protected Pipeline create(Map config) {
        def pipeline = new Pipeline()
        pipeline.id = "ID"
        if (config.id) {
          pipeline.pipelineConfigId = config.id
        }
        if (config.limitConcurrent) {
          pipeline.limitConcurrent = config.limitConcurrent
        }
        pipeline.status = executionStatus
        return pipeline
      }
    }
    pipelineStarter.executionJobBuilder = executionJobBuilder
    pipelineStarter.jobRegistry = new MapJobRegistry()
    pipelineStarter.mapper = new ObjectMapper()
    pipelineStarter.launcher = Mock(JobLauncher)
    pipelineStarter
  }

}
