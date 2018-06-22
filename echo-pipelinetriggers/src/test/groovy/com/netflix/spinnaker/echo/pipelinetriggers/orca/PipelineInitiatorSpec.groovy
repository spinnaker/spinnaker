package com.netflix.spinnaker.echo.pipelinetriggers.orca

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.fiat.shared.FiatStatus
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static rx.Observable.empty

class PipelineInitiatorSpec extends Specification {
  def registry = new NoopRegistry()
  def orca = Mock(OrcaService)
  def fiatStatus = Mock(FiatStatus)


  @Unroll
  def "calls orca #orcaCalls times when enabled=#enabled flag"() {
    given:
    def pipelineInitiator = new PipelineInitiator(registry, orca, fiatStatus, enabled, 5, 5000)
    def pipeline = Pipeline.builder().application("application").name("name").id("id").build()

    when:
    pipelineInitiator.call(pipeline)

    then:
    _ * fiatStatus.isEnabled() >> { return enabled }
    orcaCalls * orca.trigger(pipeline) >> empty()

    where:
    enabled || orcaCalls
    true    || 1
    false   || 0
  }
}
