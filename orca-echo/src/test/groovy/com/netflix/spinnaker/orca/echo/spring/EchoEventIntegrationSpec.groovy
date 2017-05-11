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

package com.netflix.spinnaker.orca.echo.spring

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.batch.SpringBatchExecutionRunner
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapterImpl
import com.netflix.spinnaker.orca.batch.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.batch.listeners.SpringBatchExecutionListenerProvider
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipeline.ExecutionRunner
import com.netflix.spinnaker.orca.pipeline.ExecutionRunnerSpec.TestTask
import com.netflix.spinnaker.orca.pipeline.RestrictExecutionDuringTimeWindow
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode.TaskDefinition
import com.netflix.spinnaker.orca.pipeline.TaskNode.TaskGraph
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.parallel.WaitForRequisiteCompletionStage
import com.netflix.spinnaker.orca.pipeline.parallel.WaitForRequisiteCompletionTask
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.tasks.NoOpTask
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import groovy.transform.CompileStatic
import org.spockframework.spring.xml.SpockMockFactoryBean
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.support.GenericApplicationContext
import org.springframework.retry.backoff.Sleeper
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import retrofit.client.Response
import spock.lang.Specification
import spock.lang.Unroll
import spock.mock.DetachedMockFactory
import static com.netflix.spinnaker.orca.ExecutionStatus.*
import static com.netflix.spinnaker.orca.pipeline.TaskNode.GraphType.FULL
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD

@ContextConfiguration(classes = [
  StageNavigator, WaitForRequisiteCompletionTask, Config,
  WaitForRequisiteCompletionStage, RestrictExecutionDuringTimeWindow
])
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
abstract class EchoEventIntegrationSpec<R extends ExecutionRunner> extends Specification {

  abstract R create(StageDefinitionBuilder... stageDefBuilders)

  @Autowired TestTask task
  @Autowired EchoService echoService
  @Autowired ExecutionRepository executionRepository

  @Unroll
  def "raises events correctly for a stage whose status is #taskStatus"() {
    given:
    executionRepository.retrievePipeline(execution.id) >> execution
    executionRepository.updateStatus(execution.id, _) >> { id, newStatus ->
      execution.status = newStatus
    }

    and:
    def stageDefBuilder = Stub(StageDefinitionBuilder) {
      getType() >> stageType
      buildTaskGraph(_) >> new TaskGraph(FULL, [new TaskDefinition("test", TestTask)])
    }
    def runner = create(stageDefBuilder)

    and:
    task.execute(_) >> new TaskResult(taskStatus)

    and:
    def events = []
    echoService.recordEvent(_) >> { Map it ->
      events << it.details.type
      new Response("echo", 200, "OK", [], null)
    }

    when:
    runner.start(execution)

    then:
    events[0] == "orca:pipeline:starting"
    events[1] == "orca:stage:starting"
    events[2] == "orca:task:starting"
    events[3] == "orca:task:$taskState"
    events[4] == "orca:stage:$stageState"
    events[5] == "orca:pipeline:$pipelineState"

    and:
    execution.status == pipelineStatus

    where:
    taskStatus      || taskState  | stageState | pipelineState | pipelineStatus
    SUCCEEDED       || "complete" | "complete" | "complete"    | SUCCEEDED
    FAILED_CONTINUE || "complete" | "failed"   | "complete"    | SUCCEEDED
    TERMINAL        || "failed"   | "failed"   | "failed"      | TERMINAL
    CANCELED        || "failed"   | "failed"   | "failed"      | CANCELED
    SKIPPED         || "complete" | "complete" | "complete"    | SUCCEEDED

    stageType = "foo"
    execution = Pipeline.builder().withId("1").withStage(stageType).build()
  }

  @Unroll
  def "raises events correctly for a stage that does not stop the pipeline if it fails"() {
    given:
    executionRepository.retrievePipeline(execution.id) >> execution
    executionRepository.updateStatus(execution.id, _) >> { id, newStatus ->
      execution.status = newStatus
    }

    and:
    def stageDefBuilder = Stub(StageDefinitionBuilder) {
      getType() >> stageType
      buildTaskGraph(_) >> new TaskGraph(FULL, [new TaskDefinition("test", TestTask)])
    }
    def runner = create(stageDefBuilder)

    and:
    task.execute(_) >> new TaskResult(TERMINAL)

    and:
    def events = []
    echoService.recordEvent(_) >> { Map it ->
      events << it.details.type
      new Response("echo", 200, "OK", [], null)
    }

    when:
    runner.start(execution)

    then:
    verifyAll {
      events[0] == "orca:pipeline:starting"
      events[1] == "orca:stage:starting"
      events[2] == "orca:task:starting"
      // TBH I think task should be "failed" as well but it doesn't matter for
      // the purpose of notifications and is harder to implement
      events[3] == "orca:task:complete"
      events[4] == "orca:stage:failed"
      events[5] == "orca:pipeline:complete"
    }

    and:
    execution.stages.first().status == STOPPED
    execution.status == SUCCEEDED

    where:
    stageType = "foo"
    execution = Pipeline
      .builder()
      .withId("1")
      .withStage(stageType, stageType, [failPipeline: false])
      .build()
  }

  @CompileStatic
  static class Config {
    DetachedMockFactory mockFactory = new DetachedMockFactory()

    @Bean
    ExecutionRepository executionRepository() {
      mockFactory.Mock(ExecutionRepository)
    }

    @Bean
    EchoService echoService() {
      mockFactory.Mock(EchoService)
    }

    @Bean
    Front50Service front50Service() {
      mockFactory.Mock(Front50Service)
    }

    @Bean
    EchoNotifyingStageListener echoNotifyingStageListener(EchoService echoService, ExecutionRepository repository) {
      new EchoNotifyingStageListener(echoService, repository)
    }

    @Bean
    EchoNotifyingExecutionListener echoNotifyingExecutionListener(EchoService echoService, Front50Service front50Service, ContextParameterProcessor contextParameterProcessor) {
      new EchoNotifyingExecutionListener(echoService, front50Service, new ObjectMapper(), contextParameterProcessor)
    }

    @Bean
    TestTask testTask() { mockFactory.Stub(TestTask) }

    @Bean
    ContextParameterProcessor contextParameterProcessor() {
      new ContextParameterProcessor()
    }
  }
}

@ContextConfiguration(
  classes = [
    BatchTestConfiguration, TaskTaskletAdapterImpl,
    SpringBatchExecutionListenerProvider, Config, NoOpTask
  ]
)
class SpringBatchEchoEventIntegrationSpec extends EchoEventIntegrationSpec<SpringBatchExecutionRunner> {

  @Autowired GenericApplicationContext applicationContext
  @Autowired ExecutionRepository executionRepository

  @Override
  SpringBatchExecutionRunner create(StageDefinitionBuilder... stageDefBuilders) {
    applicationContext.with {
      stageDefBuilders.each {
        beanFactory.registerSingleton(it.type, it)
      }
      autowireCapableBeanFactory.createBean(SpringBatchExecutionRunner)
    }
  }

  @CompileStatic
  static class Config {
    @Bean
    FactoryBean<ExceptionHandler> exceptionHandler() {
      new SpockMockFactoryBean(ExceptionHandler)
    }

    @Bean
    FactoryBean<Sleeper> sleeper() { new SpockMockFactoryBean(Sleeper) }
  }
}
