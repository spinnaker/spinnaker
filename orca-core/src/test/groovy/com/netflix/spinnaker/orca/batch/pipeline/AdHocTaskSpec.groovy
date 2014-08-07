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
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.support.AbstractApplicationContext
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import spock.lang.*
import static com.netflix.spinnaker.orca.TaskResult.Status.SUCCEEDED
import static org.springframework.batch.repeat.RepeatStatus.FINISHED
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD

@Narrative("Orca should support the addition of ad-hoc tasks (i.e. those with no pre-defined stage) to a pipeline")
@Issue("https://github.com/spinnaker/orca/issues/42")
@ContextConfiguration(classes = [BatchTestConfiguration])
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
class AdHocTaskSpec extends Specification {

  @Autowired AbstractApplicationContext applicationContext
  @Autowired JobBuilderFactory jobs
  @Autowired StepBuilderFactory steps
  @Autowired JobLauncher jobLauncher
  @Autowired JobRepository jobRepository

  @Subject jobStarter = new PipelineStarter()

  def fooTasklet = Mock(Tasklet)
  def barTask = Mock(Task)

  @Shared mapper = new ObjectMapper()

  def setup() {
    applicationContext.beanFactory.with {
      registerSingleton "mapper", mapper
      registerSingleton "fooStageBuilder", new TestStageBuilder(fooTasklet, steps)
      registerSingleton "barTask", barTask

      autowireBean jobStarter
    }
  }

  def "an unknown stage is interpreted as an ad-hoc task"() {
    when:
    jobStarter.start configJson

    then:
    1 * fooTasklet.execute(*_) >> FINISHED

    then:
    1 * barTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)

    where:
    config = [[type: "foo"], [type: "bar"]]
    configJson = mapper.writeValueAsString(config)
  }
}
