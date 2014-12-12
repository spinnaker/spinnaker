package com.netflix.spinnaker.orca.echo.spring

import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.StepExecution
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class EchoNotifyingStepExecutionListenerSpec extends Specification {

  def echoService = Mock(EchoService)
  def executionRepository = Stub(ExecutionRepository)

  @Subject
  def echoListener = new EchoNotifyingStageExecutionListener(executionRepository, echoService)

  def pipeline = new Pipeline(application: "foo")
  def stage = new PipelineStage(pipeline, "test")

  def "triggers an event when a task step starts"() {
    given:
    def stepExecution = Stub(StepExecution) {
      getStatus() >> BatchStatus.STARTING
    }

    when:
    echoListener.beforeTask(stage, stepExecution)

    then:
    1 * echoService.recordEvent(_)
  }

  def "does not trigger an event when a re-entrant task starts"() {
    given:
    def stepExecution = Stub(StepExecution) {
      getStatus() >> BatchStatus.STARTED
    }

    when:
    echoListener.beforeTask(stage, stepExecution)

    then:
    0 * echoService._
  }

  @Unroll
  def "triggers an event when a task step exits with #status"() {
    given:
    def stepExecution = Stub(StepExecution) {
      getStatus() >> status
    }

    when:
    echoListener.afterTask(stage, stepExecution)

    then:
    1 * echoService.recordEvent(_)

    where:
    status                | _
    BatchStatus.COMPLETED | _
    BatchStatus.ABANDONED | _
    BatchStatus.FAILED    | _
    BatchStatus.STOPPED   | _
    BatchStatus.STOPPING  | _
  }

  @Unroll
  def "does not trigger an event when a task step exits with #status"() {
    given:
    def stepExecution = Stub(StepExecution) {
      getStatus() >> status
    }

    when:
    echoListener.afterTask(stage, stepExecution)

    then:
    0 * echoService._

    where:
    status               | _
    BatchStatus.STARTED  | _
    BatchStatus.STARTING | _
  }

  def "sends the correct data to echo"() {
    given:
    def stepExecution = Stub(StepExecution) {
      getStatus() >> BatchStatus.COMPLETED
    }
    and:
    def message
    echoService.recordEvent(_) >> {
      message = it[0]
      return null
    }

    when:
    echoListener.afterTask(stage, stepExecution)

    then:
    message.details.source == "Orca"
    message.details.application == pipeline.application
  }

}
