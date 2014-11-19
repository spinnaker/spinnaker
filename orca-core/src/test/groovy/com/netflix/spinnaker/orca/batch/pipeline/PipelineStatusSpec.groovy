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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.PipelineStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.persistence.PipelineStore
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.repository.JobRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.support.AbstractApplicationContext
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD

@ContextConfiguration(classes = [BatchTestConfiguration, OrcaConfiguration])
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
class PipelineStatusSpec extends Specification {

  @Autowired AbstractApplicationContext applicationContext
  @Autowired JobBuilderFactory jobs
  @Autowired StepBuilderFactory steps
  @Autowired JobLauncher jobLauncher
  @Autowired JobRepository jobRepository
  @Autowired JobExplorer jobExplorer

  @Autowired @Subject PipelineStarter pipelineStarter
  @Autowired PipelineStore pipelineStore

  @Shared mapper = new ObjectMapper()

  def fooTask = Stub(Task) {
    execute(*_) >> DefaultTaskResult.SUCCEEDED
  }

  def setup() {
    applicationContext.beanFactory.with {
//      registerSingleton "mapper", mapper
//      registerSingleton "pipelineStore", new InMemoryPipelineStore()
      ["foo", "bar", "baz"].each { name ->
        registerSingleton "${name}Stage", new TestStage(name, steps, pipelineStore, fooTask)
      }

//      autowireBean pipelineStarter
    }
    pipelineStarter.initialize()
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
    with(pipelineStore.retrieve(pipeline.id)) {
      stages*.status == [PipelineStatus.SUCCEEDED] * 3
    }

    where:
    stageTypes = ["foo", "bar", "baz"]
    config = [
      application: "app",
      stages     : stageTypes.collect {
        [type: it]
      }
    ]
    configJson = mapper.writeValueAsString(config)
  }

}
