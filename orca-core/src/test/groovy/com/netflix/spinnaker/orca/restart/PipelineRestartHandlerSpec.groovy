package com.netflix.spinnaker.orca.restart

import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.repository.JobRestartException
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class PipelineRestartHandlerSpec extends Specification {

  def pipelineStarter = Mock(PipelineStarter)
  def executionRepository = Stub(ExecutionRepository) {
    retrievePipeline(input.id) >> pipeline
  }
  def registry = new ExtendedRegistry(new DefaultRegistry())

  @Shared def input = [id: "1", application: "orca", name: "test"]
  @Shared def pipeline = new Pipeline(id: input.id, application: input.application, name: input.name)
  @Subject handler = handlerFor(input)

  def "handler resumes the pipeline"() {
    when:
    handler.run()

    then:
    1 * pipelineStarter.resume(pipeline)
  }

  def "counts successful restarts"() {
    when:
    handler.run()

    then:
    successCount() == old(successCount()) + 1
  }

  def "counts failed restarts"() {
    given:
    pipelineStarter.resume(pipeline) >> { throw new JobRestartException("o noes") }

    when:
    handler.run()

    then:
    thrown JobRestartException

    and:
    failureCount() == old(failureCount()) + 1
  }

  private PipelineRestartHandler handlerFor(Map input) {
    def handler = new PipelineRestartHandler(input)
    handler.pipelineStarter = pipelineStarter
    handler.executionRepository = executionRepository
    handler.extendedRegistry = registry
    return handler
  }

  private long successCount() {
    registry.counter("pipeline.restarts").count()
  }

  private long failureCount() {
    registry.counter("pipeline.failed.restarts").count()
  }
}
