package com.netflix.spinnaker.orca.echo.spring

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.pipeline.model.*
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.ExecutionStatus.*

class EchoNotifyingStageListenerSpec extends Specification {

  def echoService = Mock(EchoService)

  @Subject
  def echoListener = new EchoNotifyingStageListener(echoService)

  @Shared
  def pipelineStage = new PipelineStage(new Pipeline(), "test", "test", [:])

  @Shared
  def orchestrationStage = new OrchestrationStage(new Orchestration(), "test")

  def "triggers an event when a task step starts"() {
    given:
    def task = new DefaultTask(status: ExecutionStatus.NOT_STARTED)

    when:
    echoListener.beforeTask(null, pipelineStage, task)

    then:
    1 * echoService.recordEvent({ event -> event.details.type == "orca:task:starting" })
  }

  def "triggers an event when a stage starts"() {
    given:
    def task = new DefaultTask(stageStart: true)

    when:
    echoListener.beforeStage(null, pipelineStage)

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
    echoListener.beforeTask(null, pipelineStage, task)

    then:
    events.size() == 1
    events.details.type == ["orca:task:starting"]
  }

  @Unroll
  def "triggers an event when a task completes"() {
    given:
    def task = new DefaultTask(name: taskName, stageEnd: isEnd)

    when:
    echoListener.afterTask(null, stage, task, executionStatus, wasSuccessful)

    then:
    invocations * echoService.recordEvent(_)

    where:
    invocations | stage              | taskName   | executionStatus | wasSuccessful | isEnd
    0           | orchestrationStage | "stageEnd" | RUNNING         | true          | false
    1           | orchestrationStage | "stageEnd" | STOPPED         | true          | false
    1           | pipelineStage      | "xxx"      | SUCCEEDED       | true          | false
    2           | pipelineStage      | "stageEnd" | SUCCEEDED       | true          | false
    2           | pipelineStage      | "stageEnd" | SUCCEEDED       | false         | false
    1           | pipelineStage      | "xxx"      | SUCCEEDED       | true          | true // is end but v2 version so not triggered by afterTask
    1           | orchestrationStage | "xxx"      | SUCCEEDED       | true          | true
  }

  @Unroll
  def "triggers an end stage event"() {
    when:
    echoListener.afterStage(null, stage)

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
    echoListener.afterTask(null, stage, task, executionStatus, wasSuccessful)

    then:
    message.details.source == "orca"
    message.details.application == pipelineStage.execution.application
    message.details.type == "orca:${type}:$echoMessage"
    message.details.type instanceof String
    message.content.standalone == standalone
    message.content.taskName == "${stage.type}.$taskName"
    message.content.taskName instanceof String

    where:
    stage              | taskName   | executionStatus | wasSuccessful || echoMessage || type    || standalone
    orchestrationStage | "xxx"      | STOPPED         | true          || "complete"  || "task"  || true
    pipelineStage      | "xxx"      | SUCCEEDED       | true          || "complete"  || "task"  || false
    pipelineStage      | "stageEnd" | SUCCEEDED       | true          || "complete"  || "stage" || false
    pipelineStage      | "stageEnd" | SUCCEEDED       | false         || "failed"    || "stage" || false
  }
}
