/*
 * Copyright 2014 Netflix, Inc.
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

import com.netflix.spinnaker.kork.eureka.EurekaComponents
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.config.JesqueConfiguration
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.PipelineJobBuilder
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.support.AbstractApplicationContext
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD

@ContextConfiguration(classes = [BatchTestConfiguration, EurekaComponents, JesqueConfiguration, EmbeddedRedisConfiguration, OrcaConfiguration])
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
class PipelineStatusSpec extends Specification {

  @Autowired AbstractApplicationContext applicationContext
  @Autowired StepBuilderFactory steps
  @Autowired ExecutionRepository executionRepository
  @Autowired PipelineJobBuilder pipelineJobBuilder
  @Autowired PipelineStarter pipelineStarter

  static mapper = new OrcaObjectMapper()

  def fooTask = Stub(Task) {
    execute(*_) >> DefaultTaskResult.SUCCEEDED
  }

  def setup() {
    applicationContext.beanFactory.with {
      ["foo", "bar", "baz"].each { name ->
        def stage = new TestStage(name, steps, executionRepository, fooTask)
        stage.applicationContext = applicationContext
        registerSingleton "${name}Stage", stage
      }
    }
    pipelineJobBuilder.initialize()
  }

  def "can get a list of stages from the pipeline"() {
    expect:
    with(pipelineStarter.start(configJson)) {
      stages.size() == 3
      stages.type == stageTypes
    }

    where:
    stageTypes = ["foo", "bar", "baz"]
    config = [
      application: "app",
      name       : "test-pipeline",
      stages     : stageTypes.collect {
        [type: it]
      }
    ]
    configJson = mapper.writeValueAsString(config)
  }

  def "can get the status of each stage"() {
    given:
    def pipeline = pipelineStarter.start(configJson)

    expect:
    with(executionRepository.retrievePipeline(pipeline.id)) {
      stages*.status == [ExecutionStatus.SUCCEEDED] * 3
    }

    where:
    stageTypes = ["foo", "bar", "baz"]
    config = [
      application: "app",
      name       : "test-pipeline",
      stages     : stageTypes.collect {
        [type: it]
      }
    ]
    configJson = mapper.writeValueAsString(config)
  }

}
