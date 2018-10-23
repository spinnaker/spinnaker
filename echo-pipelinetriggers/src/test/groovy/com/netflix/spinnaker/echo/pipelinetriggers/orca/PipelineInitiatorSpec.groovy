package com.netflix.spinnaker.echo.pipelinetriggers.orca

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.fiat.shared.FiatStatus
import spock.lang.Specification
import spock.lang.Unroll

import static rx.Observable.empty

class PipelineInitiatorSpec extends Specification {
  def registry = new NoopRegistry()
  def orca = Mock(OrcaService)
  def fiatStatus = Mock(FiatStatus)
  def objectMapper = Mock(ObjectMapper)


  @Unroll
  def "calls orca #orcaCalls times when enabled=#enabled flag"() {
    given:
    def pipelineInitiator = new PipelineInitiator(registry, orca, fiatStatus, objectMapper, enabled, 5, 5000)
    def pipeline = Pipeline.builder().application("application").name("name").id("id").type("pipeline").build()

    when:
    pipelineInitiator.startPipeline(pipeline)

    then:
    _ * fiatStatus.isEnabled() >> { return enabled }
    orcaCalls * orca.trigger(pipeline) >> empty()

    where:
    enabled || orcaCalls
    true    || 1
    false   || 0
  }

  @Unroll
  def "calls orca #orcaCalls to plan pipeline if templated"() {
    given:
    def pipelineInitiator = new PipelineInitiator(registry, orca, fiatStatus, objectMapper, true, 5, 5000)
    def pipeline = Pipeline.builder()
      .application("application")
      .name("name")
      .id("id")
      .type(type)
      .build()
    def pipelineMap = pipeline as Map

    when:
    pipelineInitiator.startPipeline(pipeline)

    then:
    1 * fiatStatus.isEnabled() >> { return true }
    orcaCalls * orca.plan(_) >> pipelineMap
    objectMapper.convertValue(pipelineMap, Pipeline.class) >> pipeline
    1 * orca.trigger(_) >> empty()

    where:
    type                || orcaCalls
    "pipeline"          || 0
    "templatedPipeline" || 1
  }
}
