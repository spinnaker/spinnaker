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
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.monitoring.DefaultPipelineMonitor
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.repository.JobRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.support.AbstractApplicationContext
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import spock.lang.Ignore
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

  @Subject pipelineStarter = new PipelineStarter()

  @Shared mapper = new ObjectMapper()
  def fooTask = Stub(Task) {
    execute(*_) >> DefaultTaskResult.SUCCEEDED
  }

  def setup() {
    applicationContext.beanFactory.with {
      registerSingleton "mapper", mapper
      def monitor = new DefaultPipelineMonitor()
      ["foo", "bar", "baz"].each { name ->
        registerSingleton "${name}Stage", new TestStage(name, steps, monitor, fooTask)
      }

      autowireBean pipelineStarter
    }
    pipelineStarter.initialize()
  }

  def "creates a pipeline id"() {
    expect:
    with(pipelineStarter.start(configJson)) {
      id ==~ /.+/
    }

    where:
    config = [[type: "foo"]]
    configJson = mapper.writeValueAsString(config)
  }

  def "can get a list of stages from the pipeline"() {
    expect:
    with(pipelineStarter.start(configJson)) {
      stages.size() == 3
      stages.name == stageNames
    }

    where:
    stageNames = ["foo", "bar", "baz"]
    config = stageNames.collect {
      [type: it]
    }
    configJson = mapper.writeValueAsString(config)
  }

  @Ignore
  def "can get the status of each stage"() {
    expect:
    with(pipelineStarter.start(configJson)) {
      // Pipeline has a getStatus as well as stage – here we want the stage
      // status. Really should remove the duplication
      stages*.status == [PipelineStatus.SUCCEEDED] * 3
    }

    where:
    stageNames = ["foo", "bar", "baz"]
    config = stageNames.collect {
      [type: it]
    }
    configJson = mapper.writeValueAsString(config)
  }

}
