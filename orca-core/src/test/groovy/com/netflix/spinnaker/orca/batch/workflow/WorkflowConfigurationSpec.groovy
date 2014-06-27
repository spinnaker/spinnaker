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

package com.netflix.spinnaker.orca.batch.workflow

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.Maps
import com.netflix.spinnaker.orca.api.JobStarter
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.support.AbstractApplicationContext
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import static org.springframework.batch.repeat.RepeatStatus.FINISHED
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD

@ContextConfiguration(classes = [BatchTestConfiguration])
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
class WorkflowConfigurationSpec extends Specification {

  @Autowired AbstractApplicationContext applicationContext
  @Autowired JobBuilderFactory jobs
  @Autowired StepBuilderFactory steps
  @Autowired JobLauncher jobLauncher
  @Autowired JobRepository jobRepository

  @Subject jobStarter = new JobStarter()

  def fooTasklet = Mock(Tasklet)
  def barTasklet = Mock(Tasklet)
  def bazTasklet = Mock(Tasklet)

  @Shared mapper = new ObjectMapper()

  def setup() {
    applicationContext.beanFactory.with {
      registerSingleton "mapper", mapper
      registerSingleton "fooWorkflowBuilder", new TestWorkflowBuilder(fooTasklet, steps)
      registerSingleton "barWorkflowBuilder", new TestWorkflowBuilder(barTasklet, steps)
      registerSingleton "bazWorkflowBuilder", new TestWorkflowBuilder(bazTasklet, steps)

      autowireBean jobStarter
    }
  }

  def "a single workflow step is constructed from mayo's json config"() {
    when:
    jobStarter.start configJson

    then:
    1 * fooTasklet.execute(*_) >> FINISHED

    where:
    config = [[type: "foo"]]
    configJson = mapper.writeValueAsString(config)
  }

  def "multiple workflow steps are constructed from mayo's json config"() {
    when:
    jobStarter.start configJson

    then:
    1 * fooTasklet.execute(*_) >> FINISHED

    then:
    1 * barTasklet.execute(*_) >> FINISHED

    then:
    1 * bazTasklet.execute(*_) >> FINISHED

    where:
    config = [
        [type: "foo"],
        [type: "bar"],
        [type: "baz"]
    ]
    configJson = mapper.writeValueAsString(config)
  }

  def "config values are converted to job parameters"() {
    given:
    def jobParameters
    1 * fooTasklet.execute(*_) >> { _, ChunkContext chunkContext ->
      jobParameters = chunkContext.stepContext.jobParameters
      FINISHED
    }

    when:
    jobStarter.start configJson

    then:
    jobParameters == expectedParameters

    where:
    config = [[type: "foo", region: "us-west-1", os: "ubuntu"]]
    configJson = mapper.writeValueAsString(config)
    expectedParameters = Maps.filterKeys(config[0]) { it != "type" }
  }
}
