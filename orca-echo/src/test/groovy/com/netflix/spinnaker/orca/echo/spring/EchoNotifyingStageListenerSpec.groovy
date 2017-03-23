package com.netflix.spinnaker.orca.echo.spring

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.listeners.Persister
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.ExecutionStatus.*

class EchoNotifyingStageListenerSpec extends Specification {

  def echoService = Mock(EchoService)
  def persister = Stub(Persister)

  @Subject
  def echoListener = new EchoNotifyingStageListener(echoService)

  @Shared
  def pipelineStage = new Stage<>(new Pipeline(), "test", "test", [:])

  @Shared
  def orchestrationStage = new Stage<>(new Orchestration(), "test")

  def "triggers an event when a task step starts"() {
    given:
    def task = new DefaultTask(status: ExecutionStatus.NOT_STARTED)

    when:
    echoListener.beforeTask(persister, pipelineStage, task)

    then:
    1 * echoService.recordEvent({ event -> event.details.type == "orca:task:starting" })
  }

  def "triggers an event when a stage starts"() {
    given:
    def task = new DefaultTask(stageStart: true)

    when:
    echoListener.beforeStage(persister, pipelineStage)

    then:
    1 * echoService.recordEvent({ event -> event.details.type == "orca:stage:starting" })
  }

  def "triggers an event when a task starts"() {
    given:
    def task = new DefaultTask(stageStart: false)

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
    def task = new DefaultTask(name: taskName, stageEnd: isEnd)

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
    invocations * echoService.recordEvent(_)

    where:
    invocations | stage
    1           | pipelineStage
    0           | orchestrationStage
  }

  @Unroll
  def "sends the correct data to echo when the task completes"() {
    given:
    def task = new DefaultTask(name: taskName)

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
