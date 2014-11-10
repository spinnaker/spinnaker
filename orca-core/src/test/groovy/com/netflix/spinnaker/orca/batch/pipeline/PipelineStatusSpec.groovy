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
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.PipelineStore
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

@ContextConfiguration(classes = [BatchTestConfiguration])
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
class PipelineStatusSpec extends Specification {

  @Autowired AbstractApplicationContext applicationContext
  @Autowired JobBuilderFactory jobs
  @Autowired StepBuilderFactory steps
  @Autowired JobLauncher jobLauncher
  @Autowired JobRepository jobRepository
  @Autowired JobExplorer jobExplorer

  @Subject pipelineStarter = new PipelineStarter()

  @Shared mapper = new ObjectMapper()
  def fooTask = Stub(Task) {
    execute(*_) >> DefaultTaskResult.SUCCEEDED
  }

  def setup() {
    applicationContext.beanFactory.with {
      registerSingleton "mapper", mapper
      registerSingleton "pipelineStore", Stub(PipelineStore)
      ["foo", "bar", "baz"].each { name ->
        registerSingleton "${name}Stage", new TestStage(name, steps, fooTask)
      }

      autowireBean pipelineStarter
    }
    pipelineStarter.initialize()
  }

  def "can get a list of stages from the pipeline"() {
    expect:
    pipelineStarter.start(configJson).subscribe {
      with(it) {
        stages.size() == 3
        stages.type == stageTypes
      }
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
    expect:
    pipelineStarter.start(configJson).subscribe({
      with(it) {
        // Pipeline has a getStatus as well as stage – here we want the stage
        // status. Really should remove the duplication
        stages*.status == [PipelineStatus.SUCCEEDED] * 3
      }
    })

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
