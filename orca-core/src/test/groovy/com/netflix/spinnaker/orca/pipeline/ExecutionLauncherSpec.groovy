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

package com.netflix.spinnaker.orca.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.test.TestConfiguration
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.pipeline.model.Execution.DEFAULT_EXECUTION_ENGINE
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionEngine.v2
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionEngine.v3

abstract class ExecutionLauncherSpec<T extends Execution, L extends ExecutionLauncher<T>> extends Specification {

  abstract L create()

  @Shared def objectMapper = new ObjectMapper()
  def v2Runner = Mock(ExecutionRunner) {
    engine() >> v2
  }
  def v3Runner = Mock(ExecutionRunner) {
    engine() >> v3
  }
  def executionRepository = Mock(ExecutionRepository)

}

class PipelineLauncherSpec extends ExecutionLauncherSpec<Pipeline, PipelineLauncher> {

  def startTracker = Mock(PipelineStartTracker)
  def pipelineValidator = Stub(PipelineValidator)

  @Override
  PipelineLauncher create() {
    return new PipelineLauncher(objectMapper, "currentInstanceId", executionRepository, [v2Runner, v3Runner], Optional.of(startTracker), Optional.of(pipelineValidator))
  }

  def "can autowire pipeline launcher with optional dependencies"() {
    given:
    def context = new AnnotationConfigApplicationContext()
    context.with {
      beanFactory.with {
        register(TestConfiguration)
        registerSingleton("objectMapper", objectMapper)
        registerSingleton("executionRepository", executionRepository)
        registerSingleton("executionRunner", v2Runner)
        registerSingleton("whateverStageDefBuilder", new StageDefinitionBuilder() {
          @Override
          String getType() {
            return "whatever"
          }
        })
        registerSingleton("pipelineStartTracker", startTracker)
      }
      register(PipelineLauncher)
      refresh()
    }

    expect:
    context.getBean(PipelineLauncher)
  }

  def "can autowire pipeline launcher without optional dependencies"() {
    given:
    def context = new AnnotationConfigApplicationContext()
    context.with {
      beanFactory.with {
        register(TestConfiguration)
        registerSingleton("objectMapper", objectMapper)
        registerSingleton("executionRepository", executionRepository)
        registerSingleton("executionRunner", v2Runner)
        registerSingleton("whateverStageDefBuilder", new StageDefinitionBuilder() {
          @Override
          String getType() {
            return "whatever"
          }
        })
      }
      register(PipelineLauncher)
      refresh()
    }

    expect:
    context.getBean(PipelineLauncher)
  }

  def "does not start pipeline if it should be queued"() {
    given:
    startTracker.queueIfNotStarted(*_) >> true

    and:
    @Subject def launcher = create()

    when:
    launcher.start(json)

    then:
    1 * executionRepository.store(_)
    0 * v2Runner.start(_)

    where:
    config = [id: "whatever", stages: [], limitConcurrent: true]
    json = objectMapper.writeValueAsString(config)
  }

  def "starts pipeline if it should not be queued"() {
    given:
    startTracker.queueIfNotStarted(*_) >> false

    and:
    @Subject def launcher = create()

    when:
    launcher.start(json)

    then:
    1 * v2Runner.start(_)
    1 * startTracker.addToStarted(config.id, _)

    where:
    config = [id: "whatever", stages: []]
    json = objectMapper.writeValueAsString(config)
  }

  @Unroll
  def "sets executionEngine correctly"() {
    given:
    @Subject def launcher = create()

    when:
    launcher.start(json)

    then:
    1 * executionRepository.store({
      it.executionEngine == expected
    })

    where:
    supplied                | expected
    [executionEngine: "v3"] | v3
    [executionEngine: "v2"] | v2
    [executionEngine: null] | DEFAULT_EXECUTION_ENGINE
    [:]                     | DEFAULT_EXECUTION_ENGINE

    config = [id: "1", stages: []] + supplied
    json = objectMapper.writeValueAsString(config)
  }
}
