/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.echo.pipelinetriggers.postprocessors

import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.test.RetrofitStubs
import spock.lang.Specification
import spock.lang.Subject

class PipelinePostProcessorHandlerSpec extends Specification implements RetrofitStubs {
  // Generates a simple post-processor that overwrites the id of a pipeline
  def changeId = {
    String newId, PostProcessorPriority priority ->
      new PipelinePostProcessor() {
        @Override
        Pipeline processPipeline(Pipeline inputPipeline) {
          return inputPipeline.withId(newId)
        }
        @Override
        PostProcessorPriority priority() {
          return priority;
        }
      }
  }

  def "does not change the pipeline if no post-processors are defined"() {
    given:
    @Subject
    def pipelinePostProcessorHandler = new PipelinePostProcessorHandler([])
    def inputPipeline = createPipelineWith(enabledJenkinsTrigger)

    when:
    def outputPipeline = pipelinePostProcessorHandler.process(inputPipeline)

    then:
    outputPipeline.id == inputPipeline.id
  }

  def "changes the pipeline as defined by the post-processor"() {
    given:
    @Subject
    def pipelinePostProcessorHandler = new PipelinePostProcessorHandler([changeId("ABC", PostProcessorPriority.DEFAULT)])
    def inputPipeline = createPipelineWith(enabledJenkinsTrigger)

    when:
    def outputPipeline = pipelinePostProcessorHandler.process(inputPipeline)

    then:
    outputPipeline.id == "ABC"
  }

  def "runs post-processors in order"() {
    given:
    @Subject
    def pipelinePostProcessorHandler = new PipelinePostProcessorHandler([
      changeId("ABC", PostProcessorPriority.LOWEST),
      changeId("DEF", PostProcessorPriority.HIGHEST)
    ])
    def inputPipeline = createPipelineWith(enabledJenkinsTrigger)

    when:
    def outputPipeline = pipelinePostProcessorHandler.process(inputPipeline)

    then:
    outputPipeline.id == "ABC"
  }
}
