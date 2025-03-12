package com.netflix.spinnaker.orca.echo.spring

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.TaskExecutionImpl
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.*
import static com.netflix.spinnaker.orca.echo.spring.EchoNotifyingStageListener.INCLUDE_FULL_EXECUTION_PROPERTY
import static com.netflix.spinnaker.orca.echo.spring.EchoNotifyingStageListener.IGNORE_TASK_EVENTS_PROPERTY


class EchoNotifyingStageListenerSpec extends Specification {

  def echoService = Mock(EchoService)
  def dynamicConfigService = Mock(DynamicConfigService)
  def contextParameterProcessor =  new ContextParameterProcessor()

  @Subject
  def echoListener = new EchoNotifyingStageListener(echoService, contextParameterProcessor, dynamicConfigService)

  @Shared
  def pipelineStage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "test", "test", [:])

  @Shared
  def orchestrationStage = new StageExecutionImpl(PipelineExecutionImpl.newOrchestration("orca"), "test")

  @Unroll
  def "triggers an event when a task step starts"() {
    given:
    def task = new TaskExecutionImpl(status: NOT_STARTED)

    when:
    echoListener.beforeTask(pipelineStage, task)

    then:
    1 * dynamicConfigService.getConfig(Boolean, IGNORE_TASK_EVENTS_PROPERTY, false) >> ignoreTaskEvents
    if (!ignoreTaskEvents) 1 * echoService.recordEvent({ event -> event.details.type == "orca:task:starting" })
    else 0 * echoService.recordEvent(_)

    where:
    ignoreTaskEvents << [true, false]
  }

  def "triggers an event when a stage starts"() {
    when:
    echoListener.beforeStage(pipelineStage)

    then:
    1 * echoService.recordEvent({ event -> event.details.type == "orca:stage:starting" })
  }

  @Unroll
  def "triggers an event when a task starts"() {
    given:
    def task = new TaskExecutionImpl(stageStart: false)

    when:
    echoListener.beforeTask(pipelineStage, task)

    then:
    1 * dynamicConfigService.getConfig(Boolean, IGNORE_TASK_EVENTS_PROPERTY, false) >> ignoreTaskEvents
    if (!ignoreTaskEvents) {
      def events = []
      1 * echoService.recordEvent(_) >> { events << it[0]; null }
      events.size() == 1
      events.details.type == ["orca:task:starting"]
    } else 0 * echoService.recordEvent(_)

    where:
    ignoreTaskEvents << [true, false]
  }

  @Unroll
  def "triggers an event when a task completes"() {
    given:
    def task = new TaskExecutionImpl(name: taskName, stageEnd: isEnd)
    task.status = executionStatus

    when:
    echoListener.afterTask(stage, task)

    then:
    1 * dynamicConfigService.getConfig(Boolean, IGNORE_TASK_EVENTS_PROPERTY, false) >> ignoreTaskEvents
    invocations * echoService.recordEvent(_)

    where:
    invocations | stage              | executionStatus | isEnd | ignoreTaskEvents
    0           | orchestrationStage | RUNNING         | false | false
    1           | orchestrationStage | STOPPED         | false | false
    1           | orchestrationStage | SUCCEEDED       | false | false
    1           | pipelineStage      | SUCCEEDED       | false | false
    1           | pipelineStage      | SUCCEEDED       | true  | false
    1           | pipelineStage      | TERMINAL        | false | false
    1           | orchestrationStage | SUCCEEDED       | true  | false
    0           | orchestrationStage | RUNNING         | false | true
    0           | orchestrationStage | STOPPED         | false | true
    0           | orchestrationStage | SUCCEEDED       | false | true
    0           | pipelineStage      | SUCCEEDED       | false | true
    0           | pipelineStage      | SUCCEEDED       | true  | true
    0           | pipelineStage      | TERMINAL        | false | true
    0           | orchestrationStage | SUCCEEDED       | true  | true

    taskName = "xxx"
  }

  @Unroll
  def "triggers an end stage event"() {
    when:
    echoListener.afterStage(stage)

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
    def task = new TaskExecutionImpl(name: taskName)
    task.status = executionStatus

    and:
    def message
    echoService.recordEvent(_) >> {
      message = it[0]
      return null
    }

    when:
    echoListener.afterTask(stage, task)

    then:
    1 * dynamicConfigService.getConfig(Boolean, IGNORE_TASK_EVENTS_PROPERTY, false) >> false
    1 * dynamicConfigService.getConfig(Boolean, INCLUDE_FULL_EXECUTION_PROPERTY, _) >> fullExecutionToggle
    message.details.source == "orca"
    message.details.application == pipelineStage.execution.application
    message.details.type == "orca:task:$echoMessage"
    message.details.type instanceof String
    message.content.standalone == standalone
    message.content.taskName == "${stage.type}.$taskName"
    message.content.taskName instanceof String
    message.content.stageId == stage.id
    (message.content.execution != null) == includesFullExecution

    where:
    stage              | executionStatus | echoMessage | standalone | fullExecutionToggle | includesFullExecution
    orchestrationStage | STOPPED         | "complete"  | true       | true                | true
    pipelineStage      | SUCCEEDED       | "complete"  | false      | true                | true
    pipelineStage      | TERMINAL        | "failed"    | false      | true                | true
    orchestrationStage | STOPPED         | "complete"  | true       | false               | false
    pipelineStage      | SUCCEEDED       | "complete"  | false      | false               | false
    pipelineStage      | TERMINAL        | "failed"    | false      | false               | false
    taskName = "xxx"
  }
}
