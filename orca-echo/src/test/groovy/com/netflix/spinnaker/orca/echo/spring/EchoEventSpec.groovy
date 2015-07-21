package com.netflix.spinnaker.orca.echo.spring

import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD

import com.netflix.spinnaker.kork.eureka.EurekaComponents
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.config.JesqueConfiguration
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.PipelineJobBuilder
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.SimpleStage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
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
@ContextConfiguration(classes = [BatchTestConfiguration, EmbeddedRedisConfiguration, JesqueConfiguration, EurekaComponents, OrcaConfiguration])
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
class EchoEventSpec extends Specification {

  public static final taskSuccess = new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
  public static final taskFailed = new DefaultTaskResult(ExecutionStatus.FAILED)
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
  ExecutionRepository executionRepository

  def task1 = Mock(Task)
  def task2 = Mock(Task)

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
    applicationContext.beanFactory.with {
      registerSingleton "echoPipelineListener", new EchoNotifyingPipelineExecutionListener(executionRepository, echoService)
      registerSingleton "echoTaskListener", new EchoNotifyingStageExecutionListener(executionRepository, echoService)

      [task1, task2].eachWithIndex { task, i ->
        def name = "stage${i + 1}"
        def stage = new SimpleStage(name, task)
        autowireBean stage
        registerSingleton name, stage
        stage.applicationContext = applicationContext
      }
      // needs to pick up the listeners
      autowireBean pipelineJobBuilder
    }
    // needs to pick up the tasks
    pipelineJobBuilder.initialize()
  }

  def "events are raised in the correct order"() {
    given:
    def events = collectEvents()

    and:
    task1.execute(_) >> taskSuccess
    task2.execute(_) >> taskSuccess

    when:
    pipelineStarter.start(json)

    then:
    events.details.type == ["orca:pipeline:starting"] +
      (['orca:stage:starting'] + ["orca:task:starting", "orca:task:complete"] * 3 + ['orca:stage:complete']) * 2 +
      ["orca:pipeline:complete"]
  }

  def "when tasks repeat they don't send duplicate start events"() {
    given:
    def events = collectEvents()

    and:
    task1.execute(_) >>> [taskMustRepeat, taskMustRepeat, taskMustRepeat, taskSuccess]
    task2.execute(_) >> taskSuccess

    when:
    pipelineStarter.start(json)

    then:
    events.details.type == ["orca:pipeline:starting"] +
      (['orca:stage:starting'] + ["orca:task:starting", "orca:task:complete"] * 3 + ['orca:stage:complete']) * 2 +
      ["orca:pipeline:complete"]
  }

  @Unroll
  def "when tasks fail they still send end events"() {
    given:
    def events = collectEvents()

    and:
    task1.execute(_) >> failure

    when:
    pipelineStarter.start(json)

    then:
    0 * task2.execute(_)

    and:
    events.details.type == ["orca:pipeline:starting",
                            "orca:stage:starting",
                            "orca:task:starting",
                            "orca:task:complete",
                            "orca:task:starting",
                            "orca:task:failed",
                            "orca:stage:failed",
                            "orca:pipeline:failed"]

    where:
    failure << [taskFailed, taskTerminal]
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
}
