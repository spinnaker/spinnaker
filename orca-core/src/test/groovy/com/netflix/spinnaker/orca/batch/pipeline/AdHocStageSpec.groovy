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
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.config.OrcaTestConfiguration
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.StandaloneTask
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.repository.JobRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.support.AbstractApplicationContext
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Timeout

import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD
//@Narrative("Orca should support the addition of ad-hoc stages (i.e. those with no pre-defined stage that just consist of a single task) to a pipeline")
@Issue("https://github.com/spinnaker/orca/issues/42")
@ContextConfiguration(classes = [BatchTestConfiguration, OrcaTestConfiguration])
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
class AdHocStageSpec extends Specification {

  @Autowired AbstractApplicationContext applicationContext
  @Autowired JobBuilderFactory jobs
  @Autowired StepBuilderFactory steps
  @Autowired JobLauncher jobLauncher
  @Autowired JobRepository jobRepository

  @Autowired @Subject PipelineStarter jobStarter
  @Autowired ExecutionRepository executionRepository

  @Shared mapper = new OrcaObjectMapper()

  def "an unknown stage is interpreted as an ad-hoc task"() {
    given:
    def fooTask = Mock(Task)
    def barTask = Mock(StandaloneTask) {
      getType() >> "bar"
    }
    applicationContext.beanFactory.with {
      registerSingleton "fooStage", new TestStage("foo", steps, executionRepository, fooTask)
      registerSingleton "barTask", barTask
    }
    jobStarter.initialize()

    when:
    jobStarter.start configJson

    then:
    1 * fooTask.execute(_) >> DefaultTaskResult.SUCCEEDED

    then:
    1 * barTask.execute(_) >> DefaultTaskResult.SUCCEEDED

    where:
    config = [
      application: "app",
      stages     : [[type: "foo"], [type: "bar"]]
    ]
    configJson = mapper.writeValueAsString(config)
  }

  @Timeout(1)
  def "an ad-hoc stage can be retryable"() {
    given:
    def fooTask = Mock(RetryableStandaloneTask) {
      getType() >> "foo"
      getTimeout() >> Long.MAX_VALUE
    }
    applicationContext.beanFactory.with {
      registerSingleton "fooTask", fooTask
    }
    jobStarter.initialize()

    when:
    jobStarter.start configJson

    then:
    2 * fooTask.execute(_) >>> [new DefaultTaskResult(RUNNING), DefaultTaskResult.SUCCEEDED]

    where:
    config = [
      application: "app",
      stages     : [[type: "foo"]]
    ]
    configJson = mapper.writeValueAsString(config)
  }

  private
  static interface RetryableStandaloneTask extends RetryableTask, StandaloneTask {
  }
}
