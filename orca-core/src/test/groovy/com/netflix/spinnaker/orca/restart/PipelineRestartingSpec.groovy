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

package com.netflix.spinnaker.orca.restart

import groovy.transform.CompileStatic
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.SpringBatchConfiguration
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.batch.StageBuilder
import com.netflix.spinnaker.orca.config.JesqueConfiguration
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.config.OrcaPersistenceConfiguration
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.test.JobCompletionListener
import com.netflix.spinnaker.orca.test.TestConfiguration
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import org.spockframework.spring.xml.SpockMockFactoryBean
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.FlowBuilder
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD

@ContextConfiguration(classes = [
  EmbeddedRedisConfiguration, JesqueConfiguration, BatchTestConfiguration,
  SpringBatchConfiguration, OrcaConfiguration, OrcaPersistenceConfiguration,
  JobCompletionListener, TestConfiguration, Config
])
class PipelineRestartingSpec extends Specification {

  @Autowired PipelineStarter pipelineStarter
  @Autowired ObjectMapper mapper
  @Autowired ExecutionRepository repository
  @Autowired JobCompletionListener jobCompletionListener

  @Autowired Task1 task1
  @Autowired Task2 task2

  def "a previously run pipeline can be restarted and completed tasks are skipped"() {
    given:
    def pipeline = pipelineStarter.create(mapper.readValue(pipelineConfigFor("test"), Map))
    pipeline.stages[0].tasks << new DefaultTask(
      id: 2, name: "task1", status: SUCCEEDED,
      startTime: System.currentTimeMillis(),
      endTime: System.currentTimeMillis(), implementingClass: Task1)
    pipeline.stages[0].tasks << new DefaultTask(
      id: 3, name: "task2", status: RUNNING,
      startTime: System.currentTimeMillis(), implementingClass: Task2)
    repository.store(pipeline)

    when:
    pipelineStarter.resume(pipeline)
    jobCompletionListener.await()

    then:
    repository.retrievePipeline(pipeline.id).status.toString() == SUCCEEDED.name()

    and:
    0 * task1.execute(_)
    1 * task2.execute(_) >> new DefaultTaskResult(SUCCEEDED)
  }

  private String pipelineConfigFor(String... stages) {
    def config = [
      application: "app",
      name       : "my-pipeline",
      stages     : stages.collect { [type: it] }
    ]
    mapper.writeValueAsString(config)
  }

  @CompileStatic
  static class TestStage extends LinearStage {

    private final Task1 task1
    private final Task2 task2

    TestStage(Task1 task1, Task2 task2) {
      super("test")
      this.task1 = task1
      this.task2 = task2
    }

    @Override
    public List<Step> buildSteps(Stage stage) {
      [buildStep(stage, "task1", task1) , buildStep(stage, "task2", task2)]
    }
  }

  static interface Task1 extends Task {}

  static interface Task2 extends Task {}

  @CompileStatic
  static class Config {
    @Bean
    StageBuilder testStage(Task1 task1, Task2 task2) {
      new TestStage(task1, task2)
    }

    @Bean
    FactoryBean<Task1> task1() {
      new SpockMockFactoryBean<>(Task1) }

    @Bean
    FactoryBean<Task2> task2() { new SpockMockFactoryBean<>(Task2) }
  }
}
