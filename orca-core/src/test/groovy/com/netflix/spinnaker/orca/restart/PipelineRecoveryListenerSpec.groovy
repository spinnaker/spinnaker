package com.netflix.spinnaker.orca.restart

import com.netflix.appinfo.InstanceInfo
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.repository.JobRestartException
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.support.StaticApplicationContext
import rx.Observable
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.ExecutionStatus.*

class PipelineRecoveryListenerSpec extends Specification {

  def executionRepository = Stub(ExecutionRepository)
  def pipelineStarter = Mock(PipelineStarter)
  @Shared currentInstance = InstanceInfo.Builder.newBuilder().setAppName("orca").setHostName("localhost").build()
  def registry = new ExtendedRegistry(new DefaultRegistry())

  @Subject listener = new PipelineRecoveryListener(executionRepository, pipelineStarter, currentInstance, registry)
  def event = new ContextRefreshedEvent(new StaticApplicationContext())

  def "resumes pipelines that were in-progress on the current instance"() {
    given:
    executionRepository.retrievePipelines() >> Observable.just(pipeline1, pipeline2)

    when:
    listener.onApplicationEvent(event)

    then:
    1 * pipelineStarter.resume(pipeline1)
    1 * pipelineStarter.resume(pipeline2)

    where:
    pipeline1 = pipelineWithStatus(RUNNING, currentInstance.id)
    pipeline2 = pipelineWithStatus(NOT_STARTED, currentInstance.id)
  }

  def "continues if a restart fails"() {
    given:
    executionRepository.retrievePipelines() >> Observable.just(pipeline1, pipeline2)

    and:
    pipelineStarter.resume(pipeline1) >> { throw new JobRestartException("o noes") }

    when:
    listener.onApplicationEvent(event)

    then:
    1 * pipelineStarter.resume(pipeline2)

    where:
    pipeline1 = pipelineWithStatus(RUNNING, currentInstance.id)
    pipeline2 = pipelineWithStatus(NOT_STARTED, currentInstance.id)
  }

  def "tracks successful restarts"() {
    given:
    executionRepository.retrievePipelines() >> Observable.just(pipeline)

    when:
    listener.onApplicationEvent(event)

    then:
    successCount() == old(successCount()) + 1

    where:
    pipeline = pipelineWithStatus(RUNNING, currentInstance.id)
  }

  def "tracks failed restarts"() {
    given:
    executionRepository.retrievePipelines() >> Observable.just(pipeline)

    and:
    pipelineStarter.resume(pipeline) >> { throw new JobRestartException("o noes") }

    when:
    listener.onApplicationEvent(event)

    then:
    failureCount() == old(failureCount()) + 1

    where:
    pipeline = pipelineWithStatus(RUNNING, currentInstance.id)
  }

  def "ignores pipelines belonging to other instances"() {
    given:
    executionRepository.retrievePipelines() >> Observable.just(pipeline1, pipeline2)

    when:
    listener.onApplicationEvent(event)

    then:
    0 * pipelineStarter.resume(pipeline1)
    1 * pipelineStarter.resume(pipeline2)

    where:
    pipeline1 = pipelineWithStatus(RUNNING, "some other instance")
    pipeline2 = pipelineWithStatus(RUNNING, currentInstance.id)
  }

  @Unroll
  def "ignores pipelines in #status state"() {
    given:
    executionRepository.retrievePipelines() >> Observable.just(pipeline1, pipeline2)

    expect:
    pipeline1.status == status

    when:
    listener.onApplicationEvent(event)

    then:
    0 * pipelineStarter.resume(pipeline1)
    1 * pipelineStarter.resume(pipeline2)

    where:
    status    | _
    CANCELED  | _
    SUCCEEDED | _
    FAILED    | _
    TERMINAL  | _
    SUSPENDED | _

    pipeline1 = pipelineWithStatus(status, currentInstance.id)
    pipeline2 = pipelineWithStatus(RUNNING, currentInstance.id)
  }

  private Pipeline pipelineWithStatus(ExecutionStatus status, String executingInstance) {
    def pipeline = new Pipeline(id: UUID.randomUUID(), executingInstance: executingInstance)

    // have to do this because pipeline status is dependent on the stages within it and stages must have tasks before they are considered
    def stage = new PipelineStage(pipeline, "whatever")
    stage.tasks << new DefaultTask()
    pipeline.stages << stage
    // yeah, now on with the business

    if (status == CANCELED) {
      pipeline.canceled = true
    } else {
      stage.status = status
    }
    return pipeline
  }

  private long successCount() {
    registry.counter("pipeline.restarts").count()
  }

  private long failureCount() {
    registry.counter("pipeline.failed.restarts").count()
  }
}
