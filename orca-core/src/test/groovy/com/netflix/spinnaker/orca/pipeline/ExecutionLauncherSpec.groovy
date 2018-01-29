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

import java.time.Clock
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.test.TestConfiguration
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE

class ExecutionLauncherSpec extends Specification {

  @Shared def objectMapper = new ObjectMapper()
  def executionRunner = Mock(ExecutionRunner)
  def executionRepository = Mock(ExecutionRepository)
  def startTracker = Mock(PipelineStartTracker)
  def pipelineValidator = Stub(PipelineValidator)

  ExecutionLauncher create() {
    return new ExecutionLauncher(
      objectMapper,
      executionRepository,
      executionRunner,
      Clock.systemDefaultZone(),
      Optional.of(pipelineValidator),
      Optional.of(startTracker),
      Optional.<Registry>empty()
    )
  }

  def "can autowire execution launcher with optional dependencies"() {
    given:
    def context = new AnnotationConfigApplicationContext()
    context.with {
      beanFactory.with {
        register(TestConfiguration)
        registerSingleton("clock", Clock.systemDefaultZone())
        registerSingleton("objectMapper", objectMapper)
        registerSingleton("executionRepository", executionRepository)
        registerSingleton("executionRunner", executionRunner)
        registerSingleton("whateverStageDefBuilder", new StageDefinitionBuilder() {
          @Override
          String getType() {
            return "whatever"
          }
        })
        registerSingleton("pipelineStartTracker", startTracker)
      }
      register(ExecutionLauncher)
      refresh()
    }

    expect:
    context.getBean(ExecutionLauncher)
  }

  def "can autowire execution launcher without optional dependencies"() {
    given:
    def context = new AnnotationConfigApplicationContext()
    context.with {
      beanFactory.with {
        register(TestConfiguration)
        registerSingleton("clock", Clock.systemDefaultZone())
        registerSingleton("objectMapper", objectMapper)
        registerSingleton("executionRepository", executionRepository)
        registerSingleton("executionRunner", executionRunner)
        registerSingleton("whateverStageDefBuilder", new StageDefinitionBuilder() {
          @Override
          String getType() {
            return "whatever"
          }
        })
      }
      register(ExecutionLauncher)
      refresh()
    }

    expect:
    context.getBean(ExecutionLauncher)
  }

  def "does not start pipeline if it should be queued"() {
    given:
    startTracker.queueIfNotStarted(*_) >> true

    and:
    @Subject def launcher = create()

    when:
    launcher.start(PIPELINE, json)

    then:
    1 * executionRepository.store(_)
    0 * executionRunner.start(_)

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
    launcher.start(PIPELINE, json)

    then:
    1 * executionRunner.start(_)
    1 * startTracker.addToStarted(config.id, _)

    where:
    config = [id: "whatever", stages: []]
    json = objectMapper.writeValueAsString(config)
  }
}
