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

import com.netflix.spinnaker.config.SpringBatchConfiguration
import com.netflix.spinnaker.orca.batch.ExecutionListenerProvider
import com.netflix.spinnaker.orca.batch.StageBuilderProvider
import com.netflix.spinnaker.orca.batch.listeners.SpringBatchExecutionListenerProvider
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.batch.stages.LinearStageDefinitionBuilder
import com.netflix.spinnaker.orca.batch.stages.SpringBatchStageBuilderProvider
import com.netflix.spinnaker.orca.config.JesqueConfiguration
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.PipelineJobBuilder
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.SimpleStage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.test.TestConfiguration
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.support.AbstractApplicationContext
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD

/**
 * Most of what the listener does can be tested at a unit level, this is just to
 * ensure that we're making the right assumptions about the statuses we'll get
 * from Batch at runtime, etc.
 */
@ContextConfiguration(classes = [
  BatchTestConfiguration,
  SpringBatchConfiguration,
  EmbeddedRedisConfiguration,
  JesqueConfiguration,
  OrcaConfiguration,
  TestConfiguration
])
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
class EchoEventSpec extends Specification {

  public static final taskSuccess = new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
  public static final taskTerminal = new DefaultTaskResult(ExecutionStatus.TERMINAL)
  public static final taskMustRepeat = new DefaultTaskResult(ExecutionStatus.RUNNING)

  def echoService = Mock(EchoService)

  @Autowired
  AbstractApplicationContext applicationContext
  @Autowired
  PipelineStarter pipelineStarter
  @Autowired
  PipelineJobBuilder pipelineJobBuilder
  @Autowired
  ExecutionListenerProvider executionListenerProvider
  @Autowired
  ExecutionRepository executionRepository
  @Autowired
  StageBuilderProvider stageBuilderProvider

  def task1 = new Test1Task(delegate: Mock(Task))
  def task2 = new Test2Task(delegate: Mock(Task))

  @Shared
    json

  def setupSpec() {
    def config = [
      application: "app",
      name       : "my-pipeline",
      stages     : [[type: "stage1"], [type: "stage2"]]
    ]
    json = new OrcaObjectMapper().writeValueAsString(config)
  }

  def setup() {
    def stageBuilders = []
    applicationContext.beanFactory.with {
      [task1, task2].eachWithIndex { task, i ->
        def name = "stage${i + 1}"
        def stage = new LinearStageDefinitionBuilder(new SimpleStage(name, task), stageBuilderProvider)
        autowireBean stage
        stage.setApplicationContext(applicationContext)
        stageBuilders << stage
      }

      // distinct task classes per mock are necessary now that task implementations are looked up by class name
      registerSingleton("task1", task1)
      registerSingleton("task2", task2)

      // needs to pick up the listeners
      autowireBean pipelineJobBuilder
    }

    ((SpringBatchExecutionListenerProvider) executionListenerProvider).executionListeners.add(0, new EchoNotifyingExecutionListener(echoService))
    ((SpringBatchExecutionListenerProvider) executionListenerProvider).stageListeners.add(0, new EchoNotifyingStageListener(echoService))
    ((SpringBatchStageBuilderProvider) stageBuilderProvider).stageBuilders.addAll(stageBuilders)

    // needs to pick up the tasks
    pipelineJobBuilder.initialize()
  }

  def "events are raised in the correct order"() {
    given:
    def events = collectEvents()

    and:
    task1.delegate.execute(_) >> taskSuccess
    task2.delegate.execute(_) >> taskSuccess

    when:
    pipelineStarter.start(json)

    then:
    events.details.type == ["orca:pipeline:starting"] +
      ( ["orca:task:starting", "orca:task:complete"] + ['orca:stage:starting'] + ["orca:task:starting", "orca:task:complete"] * 2 + ['orca:stage:complete']) * 2 +
      ["orca:pipeline:complete"]
  }

  def "when tasks repeat they don't send duplicate start events"() {
    given:
    def events = collectEvents()

    and:
    task1.delegate.execute(_) >>> [taskMustRepeat, taskMustRepeat, taskMustRepeat, taskSuccess]
    task2.delegate.execute(_) >> taskSuccess

    when:
    pipelineStarter.start(json)

    then:
    events.details.type == ["orca:pipeline:starting"] +
      (["orca:task:starting", "orca:task:complete", 'orca:stage:starting'] + ["orca:task:starting", "orca:task:complete"] * 2 + ['orca:stage:complete']) * 2 +
      ["orca:pipeline:complete"]
  }

  @Unroll
  def "when tasks fail they still send end events"() {
    given:
    def events = collectEvents()

    and:
    task1.delegate.execute(_) >> failure

    when:
    pipelineStarter.start(json)

    then:
    0 * task2.delegate.execute(_)

    and:
    events.details.type == ["orca:pipeline:starting",
                            "orca:task:starting",
                            "orca:task:complete",
                            "orca:stage:starting",
                            "orca:task:starting",
                            "orca:task:failed",
                            "orca:stage:failed",
                            "orca:pipeline:failed"]

    where:
    failure << [taskTerminal]
  }

  /**
   * Traps the events sent to echo.
   * @return a list that will collect the event data sent to echo.
   */
  private List<Map> collectEvents() {
    def events = []
    echoService.recordEvent(_) >> {
      events << it[0]
      null // don't need to actually return anything from the echo call
    }
    return events
  }

  static class Test1Task implements Task {
    @Delegate
    Task delegate
  }

  static class Test2Task implements Task {
    @Delegate
    Task delegate
  }
}
