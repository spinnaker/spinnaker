package com.netflix.spinnaker.echo.pipelinetriggers.orca


import spock.lang.Specification
import spock.lang.Subject
import com.netflix.spinnaker.echo.model.Pipeline
import org.springframework.boot.actuate.metrics.CounterService
import static rx.Observable.empty

class PipelineInitiatorSpec extends Specification {

  def counter = Stub(CounterService)
  def orca = Mock(OrcaService)

  def "calls Orca if enabled"() {
    given:
    @Subject pipelineInitiator = new PipelineInitiator(counter, orca, true, false, 5, 5000)

    when:
    pipelineInitiator.call(pipeline)

    then:
    1 * orca.trigger(pipeline) >> empty()

    where:
    pipeline = Pipeline.builder().application("application").name("name").id("id").build()
  }

  def "does not call Orca if disabled"() {
    given:
    @Subject pipelineInitiator = new PipelineInitiator(counter, orca, false, false, 5, 5000)

    when:
    pipelineInitiator.call(pipeline)

    then:
    0 * _

    where:
    pipeline = Pipeline.builder().application("application").name("name").id("id").build()
  }

}
