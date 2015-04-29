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
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import org.springframework.batch.core.launch.JobLauncher
import spock.lang.Specification
import spock.lang.Unroll

class ExecutionStarterSpec extends Specification {
  @Unroll
  def "should not start a previously completed execution"() {
    given:
    def executionJobBuilder = Mock(ExecutionJobBuilder)
    def executionStatus = sourceExecutionStatus
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

        return pipeline
      }
    }
    executionStarter.mapper = new ObjectMapper()
    executionStarter.launcher = Mock(JobLauncher)

    when:
    def exceptionThrown = false
    try {
      executionStarter.start("{}")
    } catch (e) {
      assert e.message == "Unable to start execution that has previously been completed (Pipeline:ID)"
      exceptionThrown = true
    }

    then:
    exceptionThrown == expectedExceptionThrown

    where:
    sourceExecutionStatus       || expectedExceptionThrown
    ExecutionStatus.TERMINAL    || true
    ExecutionStatus.NOT_STARTED || false
  }
}
