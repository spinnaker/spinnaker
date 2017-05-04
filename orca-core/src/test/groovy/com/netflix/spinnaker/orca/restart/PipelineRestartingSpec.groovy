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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.SpringBatchConfiguration
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.config.JesqueConfiguration
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.config.OrcaPersistenceConfiguration
import com.netflix.spinnaker.orca.pipeline.ExecutionRunner
import com.netflix.spinnaker.orca.pipeline.PipelineLauncher
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task as TaskModel
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.test.JobCompletionListener
import com.netflix.spinnaker.orca.test.TestConfiguration
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import groovy.transform.CompileStatic
import org.spockframework.spring.xml.SpockMockFactoryBean
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import static java.lang.System.currentTimeMillis

@ContextConfiguration(classes = [
  EmbeddedRedisConfiguration, JesqueConfiguration, BatchTestConfiguration,
  SpringBatchConfiguration, OrcaConfiguration, OrcaPersistenceConfiguration,
  JobCompletionListener, TestConfiguration, Config
])
class PipelineRestartingSpec extends Specification {

  @Autowired ExecutionRunner executionRunner
  @Autowired PipelineLauncher pipelineLauncher
  @Autowired ObjectMapper mapper
  @Autowired ExecutionRepository repository
  @Autowired JobCompletionListener jobCompletionListener
  @Autowired TestStage stageDefinitionBuilder

  @Autowired Task1 task1
  @Autowired Task2 task2

  def "a previously run pipeline can be restarted and completed tasks are skipped"() {
    given:
    pipeline.stages[0].tasks << new TaskModel(
      id: 1, name: "task1", status: SUCCEEDED,
      startTime: currentTimeMillis(),
      endTime: currentTimeMillis(), implementingClass: Task1.name)
    pipeline.stages[0].tasks << new TaskModel(
      id: 2, name: "task2", status: RUNNING,
      startTime: currentTimeMillis(), implementingClass: Task2.name)
    repository.store(pipeline)

    when:
    stageDefinitionBuilder.prepareStageForRestart(repository, pipeline.stages.first(), [stageDefinitionBuilder])
    executionRunner.restart(pipeline)
    jobCompletionListener.await()

    then:
    repository.retrievePipeline(pipeline.id).status.toString() == SUCCEEDED.name()

    and:
    0 * task1.execute(_)
    1 * task2.execute(_) >> new TaskResult(SUCCEEDED)

    where:
    pipeline = Pipeline
      .builder()
      .withApplication("app")
      .withName("my-pipeline")
      .withStage("test")
      .build()
  }

  @CompileStatic
  static class TestStage implements StageDefinitionBuilder {
    @Override
    <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
      builder
        .withTask("task1", Task1)
        .withTask("task2", Task2)
    }
  }

  static interface Task1 extends Task {}

  static interface Task2 extends Task {}

  @CompileStatic
  static class Config {
    @Bean
    StageDefinitionBuilder testStage() {
      new TestStage()
    }

    @Bean
    FactoryBean<Task1> task1() {
      new SpockMockFactoryBean<>(Task1)
    }

    @Bean
    FactoryBean<Task2> task2() { new SpockMockFactoryBean<>(Task2) }
  }
}
