package com.netflix.spinnaker.orca.echo.spring

import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.listeners.Persister
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.ExecutionStatus.*

class EchoNotifyingStageListenerSpec extends Specification {

  def echoService = Mock(EchoService)
  def persister = Stub(Persister)
  def repository = Mock(ExecutionRepository)
  def contextParameterProcessor =  new ContextParameterProcessor()

  @Subject
  def echoListener = new EchoNotifyingStageListener(echoService, repository, contextParameterProcessor)

  @Shared
  def pipelineStage = new Stage(Execution.newPipeline("orca"), "test", "test", [:])

  @Shared
  def orchestrationStage = new Stage(Execution.newOrchestration("orca"), "test")

  def "triggers an event when a task step starts"() {
    given:
    def task = new Task(status: NOT_STARTED)

    when:
    echoListener.beforeTask(persister, pipelineStage, task)

    then:
    1 * echoService.recordEvent({ event -> event.details.type == "orca:task:starting" })
  }

  def "triggers an event when a stage starts"() {
    given:
    def task = new Task(stageStart: true)

    when:
    echoListener.beforeStage(persister, pipelineStage)

    then:
    1 * echoService.recordEvent({ event -> event.details.type == "orca:stage:starting" })
  }

  def "triggers an event when a task starts"() {
    given:
    def task = new Task(stageStart: false)

    and:
    def events = []
    echoService.recordEvent(_) >> { events << it[0]; null }

    when:
    echoListener.beforeTask(persister, pipelineStage, task)

    then:
    events.size() == 1
    events.details.type == ["orca:task:starting"]
  }

  @Unroll
  def "triggers an event when a task completes"() {
    given:
    def task = new Task(name: taskName, stageEnd: isEnd)

    when:
    echoListener.afterTask(persister, stage, task, executionStatus, wasSuccessful)

    then:
    invocations * echoService.recordEvent(_)

    where:
    invocations | stage              | executionStatus | wasSuccessful | isEnd
    0           | orchestrationStage | RUNNING         | true          | false
    1           | orchestrationStage | STOPPED         | true          | false
    1           | orchestrationStage | SUCCEEDED       | true          | false
    1           | pipelineStage      | SUCCEEDED       | true          | false
    1           | pipelineStage      | SUCCEEDED       | true          | true
    1           | pipelineStage      | TERMINAL        | false         | false
    1           | orchestrationStage | SUCCEEDED       | true          | true

    taskName = "xxx"
  }

  @Unroll
  def "triggers an end stage event"() {
    when:
    echoListener.afterStage(persister, stage)

    then:
    1 * echoService.recordEvent(_)

    where:
    stage              | _
    pipelineStage      | _
    orchestrationStage | _
  }

  @Unroll
  def "sends the correct data to echo when the task completes"() {
    given:
    def task = new Task(name: taskName)

    and:
    def message
    echoService.recordEvent(_) >> {
      message = it[0]
      return null
    }

    when:
    echoListener.afterTask(persister, stage, task, executionStatus, wasSuccessful)

    then:
    message.details.source == "orca"
    message.details.application == pipelineStage.execution.application
    message.details.type == "orca:task:$echoMessage"
    message.details.type instanceof String
    message.content.standalone == standalone
    message.content.taskName == "${stage.type}.$taskName"
    message.content.taskName instanceof String

    where:
    stage              | executionStatus | wasSuccessful | echoMessage | standalone
    orchestrationStage | STOPPED         | true          | "complete"  | true
    pipelineStage      | SUCCEEDED       | true          | "complete"  | false
    pipelineStage      | TERMINAL        | false         | "failed"    | false

    taskName = "xxx"
  }
}
