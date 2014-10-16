/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
import com.netflix.spinnaker.orca.TaskContext
import com.netflix.spinnaker.orca.pipeline.NoSuchStageException
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
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.test.hamcrest.ContainsAllOf.containsAllOf
import static org.springframework.batch.repeat.RepeatStatus.FINISHED
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD
import static spock.util.matcher.HamcrestSupport.expect

@ContextConfiguration(classes = [BatchTestConfiguration])
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
class PipelineConfigurationSpec extends Specification {

  @Autowired AbstractApplicationContext applicationContext
  @Autowired JobBuilderFactory jobs
  @Autowired StepBuilderFactory steps
  @Autowired JobLauncher jobLauncher
  @Autowired JobRepository jobRepository

  @Subject jobStarter = new PipelineStarter()

  def fooTask = Mock(Task)
  def barTask = Mock(Task)
  def bazTask = Mock(Task)

  @Shared mapper = new ObjectMapper()

  def setup() {
    applicationContext.beanFactory.with {
      registerSingleton "mapper", mapper
      registerSingleton "fooStage", new TestStage("foo", steps, fooTask)
      registerSingleton "barStage", new TestStage("bar", steps, barTask)
      registerSingleton "bazStage", new TestStage("baz", steps, bazTask)

      autowireBean jobStarter
    }
    jobStarter.initialize()
  }

  def "an unknown stage type results in an exception"() {
    when:
    jobStarter.start configJson

    then:
    thrown NoSuchStageException

    where:
    config = [[type: "qux"]]
    configJson = mapper.writeValueAsString(config)
  }

  def "a single step is constructed from mayo's json config"() {
    when:
    jobStarter.start configJson

    then:
    1 * fooTask.execute(*_) >> DefaultTaskResult.SUCCEEDED

    where:
    config = [[type: "foo"]]
    configJson = mapper.writeValueAsString(config)
  }

  def "multiple steps are constructed from mayo's json config"() {
    when:
    jobStarter.start configJson

    then:
    1 * fooTask.execute(*_) >> DefaultTaskResult.SUCCEEDED

    then:
    1 * barTask.execute(*_) >> DefaultTaskResult.SUCCEEDED

    then:
    1 * bazTask.execute(*_) >> DefaultTaskResult.SUCCEEDED

    where:
    config = [
        [type: "foo"],
        [type: "bar"],
        [type: "baz"]
    ]
    configJson = mapper.writeValueAsString(config)
  }

  def "config is serialized to job execution context"() {
    given:
    Map inputs
    1 * fooTask.execute(*_) >> { TaskContext taskContext ->
      inputs = taskContext.inputs
      FINISHED
    }

    when:
    jobStarter.start configJson

    then:
    expect inputs, containsAllOf(expectedInputs)

    where:
    config = [[type: "foo", region: "us-west-1", os: "ubuntu"]]
    configJson = mapper.writeValueAsString(config)
    expectedInputs = ["foo.region": config[0].region, "foo.os": config[0].os, "foo.type": "foo"]
  }
}
