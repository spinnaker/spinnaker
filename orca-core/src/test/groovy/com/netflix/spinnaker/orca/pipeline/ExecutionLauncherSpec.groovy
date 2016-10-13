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

abstract class ExecutionLauncherSpec<T extends Execution, L extends ExecutionLauncher<T>> extends Specification {

  abstract L create()

  @Shared def objectMapper = new ObjectMapper()
  def runner = Mock(ExecutionRunner)
  def executionRepository = Mock(ExecutionRepository)

}

class PipelineLauncherSpec extends ExecutionLauncherSpec<Pipeline, PipelineLauncher> {

  def startTracker = Stub(PipelineStartTracker)

  @Override
  PipelineLauncher create() {
    return new PipelineLauncher(objectMapper, "currentInstanceId", executionRepository, runner, startTracker)
  }

  def "can autowire pipeline launcher with optional dependencies"() {
    given:
    def context = new AnnotationConfigApplicationContext()
    context.with {
      beanFactory.with {
        register(TestConfiguration)
        registerSingleton("objectMapper", objectMapper)
        registerSingleton("executionRepository", executionRepository)
        registerSingleton("executionRunner", runner)
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
        registerSingleton("executionRunner", runner)
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
    0 * runner.start(_)

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
    1 * runner.start(_)

    where:
    config = [id: "whatever", stages: []]
    json = objectMapper.writeValueAsString(config)
  }
}
