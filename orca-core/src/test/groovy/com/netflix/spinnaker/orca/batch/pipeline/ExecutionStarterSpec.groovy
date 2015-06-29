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
import com.netflix.spinnaker.orca.pipeline.ExecutionJobBuilder
import com.netflix.spinnaker.orca.pipeline.ExecutionStarter
import com.netflix.spinnaker.orca.pipeline.PipelineStartTracker
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import org.springframework.batch.core.JobExecutionListener
import org.springframework.batch.core.launch.JobLauncher
import spock.lang.Specification
import spock.lang.Unroll

class ExecutionStarterSpec extends Specification {

  @Unroll
  def "should not start a previously completed execution"() {
    given:
    def executionStarter = getExecutionStarter(sourceExecutionStatus)

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
    def executionStarter = getExecutionStarter(status)
    JobExecutionListener listener = Mock(JobExecutionListener)
    executionStarter.pipelineListeners = [listener]

    when:
    executionStarter.start("{}")

    then:
    launchInvocations * listener.afterJob(_)

    where:
    status                      || launchInvocations
    ExecutionStatus.TERMINAL    || 1
    ExecutionStatus.NOT_STARTED || 0
  }

  @Unroll
  def "should queue concurrent executions and start ones that should not be queued"() {

    given:
    def executionStarter = getExecutionStarter(ExecutionStatus.NOT_STARTED)
    executionStarter.startTracker = Mock(PipelineStartTracker)
    executionStarter.startTracker.hasStartedExecutions(_) >> isStarted

    when:
    executionStarter.start(pipeline)

    then:
    launched * executionStarter.launcher.run(_, _)
    queued * executionStarter.startTracker.addToQueue(pipelineConfigId, _)

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
    def executionStarter = getExecutionStarter(ExecutionStatus.NOT_STARTED)
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
    def executionStarter = getExecutionStarter(ExecutionStatus.NOT_STARTED)
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

  private def getExecutionStarter(ExecutionStatus executionStatus) {
    def executionJobBuilder = Mock(ExecutionJobBuilder)
    def executionStarter = new ExecutionStarter<Pipeline>("bake") {
      @Override
      protected ExecutionJobBuilder getExecutionJobBuilder() { executionJobBuilder }

      @Override
      protected void persistExecution(Pipeline subject) {}

      @Override
      protected Pipeline create(Map config) {
        def pipeline = new Pipeline()
        pipeline.stages << new PipelineStage(pipeline, "bake")
        pipeline.stages.each {
          it.tasks << new DefaultTask()
          it.status = executionStatus
        }
        pipeline.id = "ID"
        if (config.id) {
          pipeline.pipelineConfigId = config.id
        }
        if (config.limitConcurrent) {
          pipeline.limitConcurrent = config.limitConcurrent
        }
        return pipeline
      }
    }
    executionStarter.mapper = new ObjectMapper()
    executionStarter.launcher = Mock(JobLauncher)
    executionStarter
  }

}
