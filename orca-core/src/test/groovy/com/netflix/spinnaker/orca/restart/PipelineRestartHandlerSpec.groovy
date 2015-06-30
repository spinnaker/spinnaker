package com.netflix.spinnaker.orca.restart

import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class PipelineRestartHandlerSpec extends Specification {

  def pipelineStarter = Mock(PipelineStarter)
  def executionRepository = Stub(ExecutionRepository) {
    retrievePipeline(input.id) >> pipeline
  }

  @Shared def input = [id: "1", application: "orca", name: "test"]
  @Shared def pipeline = new Pipeline(id: input.id, application: input.application, name: input.name)
  @Subject handler = handlerFor(input)

  def "handler resumes the pipeline"() {
    when:
    handler.run()

    then:
    1 * pipelineStarter.resume(pipeline)
  }

  private PipelineRestartHandler handlerFor(Map input) {
    def handler = new PipelineRestartHandler(input)
    handler.pipelineStarter = pipelineStarter
    handler.executionRepository = executionRepository
    return handler
  }
}
