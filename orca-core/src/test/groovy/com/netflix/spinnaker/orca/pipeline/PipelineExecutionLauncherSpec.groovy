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

import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import com.netflix.spinnaker.orca.events.BeforeInitialExecutionPersist
import org.springframework.context.ApplicationEventPublisher

import javax.annotation.Nonnull
import java.time.Clock
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.test.TestConfiguration
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE

class PipelineExecutionLauncherSpec extends Specification {

  @Shared def objectMapper = new ObjectMapper()
  def executionRunner = Mock(ExecutionRunner)
  def executionRepository = Mock(ExecutionRepository)
  def pipelineValidator = Stub(PipelineValidator)
  def applicationEventPublisher = Mock(ApplicationEventPublisher)

  ExecutionLauncher create() {
    return new ExecutionLauncher(
      objectMapper,
      executionRepository,
      executionRunner,
      Clock.systemDefaultZone(),
      applicationEventPublisher,
      Optional.of(pipelineValidator),
      Optional.<Registry> empty()
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
          @Nonnull
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
          @Nonnull
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

  def "starts pipeline"() {
    given:
    @Subject def launcher = create()

    when:
    launcher.start(PIPELINE, config)

    then:
    1 * applicationEventPublisher.publishEvent(_ as BeforeInitialExecutionPersist)
    1 * executionRunner.start(_)

    where:
    config = [id: "whatever", stages: []]
  }
}
