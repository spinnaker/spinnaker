package com.netflix.spinnaker.orca.echo.spring

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.SimpleStage
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.support.AbstractApplicationContext
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.ExecutionStatus.*
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD

@ContextConfiguration(classes = [BatchTestConfiguration, OrcaConfiguration])
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)

class EchoStepExecutionListenerSpec extends Specification {

  def echoService = Mock(EchoService)
  def echoListener = new EchoStepExecutionListener(echoService)
  def task = Stub(Task)
  def stage = new SimpleStage("test", task)

  @Autowired AbstractApplicationContext applicationContext
  @Autowired PipelineStarter pipelineStarter
  @Shared mapper = new OrcaObjectMapper()

  def setup() {
    applicationContext.beanFactory.with {
      registerSingleton("echoListener", echoListener)

      autowireBean(stage)
      registerSingleton("testStage", stage)
    }
    pipelineStarter.initialize()
  }

  @Unroll
  def "triggers an event when task exits with #status"() {
    given:
    task.execute(_) >> new DefaultTaskResult(status)

    when:
    pipelineStarter.start(configJson)

    then:
    1 * echoService.recordEvent(_)

    where:
    status    | _
    SUCCEEDED | _
    FAILED    | _
    TERMINAL  | _

    config = [
        application: "app",
        stages     : [[type: "test"]]
    ]
    configJson = mapper.writeValueAsString(config)
  }

}
