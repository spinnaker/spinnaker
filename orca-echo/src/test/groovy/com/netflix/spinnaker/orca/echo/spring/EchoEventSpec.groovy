package com.netflix.spinnaker.orca.echo.spring

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.SimpleStage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.support.AbstractApplicationContext
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD

/**
 * Most of what the listener does can be tested at a unit level, this is just to
 * ensure that we're making the right assumptions about the statuses we'll get
 * from Batch at runtime, etc.
 */
@ContextConfiguration(classes = [BatchTestConfiguration, OrcaConfiguration])
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
class EchoEventSpec extends Specification {

  def echoService = Mock(EchoService)

  @Autowired AbstractApplicationContext applicationContext
  @Autowired PipelineStarter jobStarter
  @Autowired ExecutionRepository executionRepository

  def task1 = Stub(Task)
  def task2 = Stub(Task)

  @Shared mapper = new OrcaObjectMapper()

  def setup() {
    applicationContext.beanFactory.with {
      registerSingleton "echoListener", new EchoNotifyingStageExecutionListener(executionRepository, echoService)

      [task1, task2].eachWithIndex { task, i ->
        def name = "stage${i + 1}"
        def stage = new SimpleStage(name, task)
        autowireBean stage
        registerSingleton name, stage
      }
    }
    jobStarter.initialize()
  }

  @Ignore
  def "events are raised in the correct order"() {
    given:
    task1.execute(_) >> new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
    task2.execute(_) >> new DefaultTaskResult(ExecutionStatus.SUCCEEDED)

    when:
    jobStarter.start(json)

    then:
    4 * echoService.recordEvent(_)

    where:
    config = [
        application: "app",
        stages     : [[type: "stage1"], [type: "stage2"]]
    ]
    json = mapper.writeValueAsString(config)
  }

}
