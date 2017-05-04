/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.restart

import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.orca.pipeline.ExecutionRunner
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.repository.JobRestartException
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class PipelineRestartHandlerSpec extends Specification {

  def executionRunner = Mock(ExecutionRunner)
  def executionRepository = Stub(ExecutionRepository) {
    retrievePipeline(input.id) >> pipeline
  }
  def registry = new DefaultRegistry()

  @Shared def input = [id: "1", application: "orca", name: "test"]
  @Shared def pipeline = new Pipeline(id: input.id, application: input.application, name: input.name)
  @Subject handler = handlerFor(input)

  def "handler resumes the pipeline"() {
    when:
    handler.run()

    then:
    1 * executionRunner.restart(pipeline)
  }

  def "counts successful restarts"() {
    when:
    handler.run()

    then:
    successCount() == old(successCount()) + 1
  }

  def "counts failed restarts"() {
    given:
    executionRunner.restart(pipeline) >> { throw new JobRestartException("o noes") }

    when:
    handler.run()

    then:
    thrown JobRestartException

    and:
    failureCount() == old(failureCount()) + 1
  }

  private PipelineRestartHandler handlerFor(Map input) {
    def handler = new PipelineRestartHandler(input)
    handler.executionRunner = executionRunner
    handler.executionRepository = executionRepository
    handler.registry = registry
    return handler
  }

  private long successCount() {
    registry.counter("pipeline.restarts").count()
  }

  private long failureCount() {
    registry.counter("pipeline.failed.restarts").count()
  }
}
