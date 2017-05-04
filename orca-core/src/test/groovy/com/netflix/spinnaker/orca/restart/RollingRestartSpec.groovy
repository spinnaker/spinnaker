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
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.batch.SpringBatchExecutionRunner
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapterImpl
import com.netflix.spinnaker.orca.batch.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.batch.listeners.SpringBatchExecutionListenerProvider
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.*
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task as TaskModel
import com.netflix.spinnaker.orca.pipeline.parallel.WaitForRequisiteCompletionStage
import com.netflix.spinnaker.orca.pipeline.parallel.WaitForRequisiteCompletionTask
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.tasks.NoOpTask
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import com.netflix.spinnaker.orca.test.JobCompletionListener
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import groovy.transform.CompileStatic
import org.spockframework.spring.xml.SpockMockFactoryBean
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.retry.backoff.Sleeper
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification
import static com.netflix.spinnaker.orca.ExecutionStatus.*
import static java.lang.System.currentTimeMillis

@ContextConfiguration(classes = [
  StageNavigator, WaitForRequisiteCompletionTask, Config,
  WaitForRequisiteCompletionStage, RestrictExecutionDuringTimeWindow,
  BatchTestConfiguration, TaskTaskletAdapterImpl,
  SpringBatchExecutionListenerProvider, Config, NoOpTask,
  PipelineLauncher, SpringBatchExecutionRunner, JobCompletionListener
])
class RollingRestartSpec extends Specification {

  @Autowired PipelineLauncher pipelineLauncher
  @Autowired ExecutionRunner executionRunner
  @Autowired ObjectMapper mapper
  @Autowired ExecutionRepository repository
  @Autowired JobCompletionListener jobCompletionListener

  @Autowired StartTask startTask
  @Autowired EndTask endTask
  @Autowired FinalTask finalTask

  def "a previously run rolling push pipeline can be restarted and redirects work"() {
    given:
    pipeline.stages.first().with {
      tasks << new TaskModel(
        id: 1,
        name: "start",
        status: SUCCEEDED,
        startTime: currentTimeMillis(),
        endTime: currentTimeMillis(),
        loopStart: true,
        implementingClass: StartTask.name)
      tasks << new TaskModel(
        id: 2,
        name: "end",
        status: REDIRECT,
        startTime: currentTimeMillis(),
        endTime: currentTimeMillis(),
        loopEnd: true,
        implementingClass: EndTask.name)
      tasks << new TaskModel(
        id: 3,
        name: "final",
        status: NOT_STARTED,
        startTime: currentTimeMillis(),
        implementingClass: FinalTask.name)
    }
    repository.retrievePipeline(pipeline.id) >> pipeline

    when:
    executionRunner.restart(pipeline)
    jobCompletionListener.await()

    then:
    2 * endTask.execute(_) >> new TaskResult(REDIRECT) >> new TaskResult(SUCCEEDED)
    1 * startTask.execute(_) >> new TaskResult(SUCCEEDED)
    1 * finalTask.execute(_) >> new TaskResult(SUCCEEDED)

    where:
    pipeline = Pipeline
      .builder()
      .withId(1)
      .withApplication("app")
      .withName("my-pipeline")
      .withStage("redirectingTest")
      .build()

  }

  static interface StartTask extends Task {}

  static interface EndTask extends Task {}

  static interface FinalTask extends Task {}

  static class RedirectingTestStage implements StageDefinitionBuilder {
    def <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
      builder
        .withLoop { subGraph ->
        subGraph
          .withTask("start", StartTask)
          .withTask("end", EndTask)
      }
      .withTask("final", FinalTask)
    }
  }

  @CompileStatic
  static class Config {
    @Bean
    FactoryBean<ExecutionRepository> executionRepository() {
      new SpockMockFactoryBean(ExecutionRepository)
    }

    @Bean
    FactoryBean<ExceptionHandler> exceptionHandler() {
      new SpockMockFactoryBean(ExceptionHandler)
    }

    @Bean
    FactoryBean<Sleeper> sleeper() { new SpockMockFactoryBean(Sleeper) }

    @Bean String currentInstanceId() { "localhost" }

    @Bean
    FactoryBean<StartTask> startTask() {
      new SpockMockFactoryBean<>(StartTask)
    }

    @Bean
    FactoryBean<EndTask> endTask() { new SpockMockFactoryBean<>(EndTask) }

    @Bean
    FactoryBean<FinalTask> finalTask() { new SpockMockFactoryBean<>(FinalTask) }

    @Bean
    StageDefinitionBuilder redirectingTestStage() {
      new RedirectingTestStage()
    }

    @Bean
    ObjectMapper objectMapper() {
      OrcaObjectMapper.newInstance()
    }

    @Bean
    ContextParameterProcessor contextParameterProcessor() {
      new ContextParameterProcessor()
    }
  }
}
