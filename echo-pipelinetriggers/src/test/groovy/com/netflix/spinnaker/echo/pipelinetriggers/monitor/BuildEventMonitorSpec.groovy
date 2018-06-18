package com.netflix.spinnaker.echo.pipelinetriggers.monitor

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.echo.model.Event
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache
import com.netflix.spinnaker.echo.test.RetrofitStubs
import rx.Observable
import rx.functions.Action1
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.echo.model.trigger.BuildEvent.Result.*

class BuildEventMonitorSpec extends Specification implements RetrofitStubs {
  def objectMapper = new ObjectMapper()
  def pipelineCache = Mock(PipelineCache)
  def subscriber = Mock(Action1)
  def registry = new NoopRegistry()

  @Subject
  def monitor = new BuildEventMonitor(pipelineCache, subscriber, registry)

  @Unroll
  def "triggers pipelines for successful builds for #triggerType"() {
    given:
    def pipeline = createPipelineWith(trigger)
    pipelineCache.getPipelines() >> Observable.just([pipeline])

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    1 * subscriber.call({
      it.application == pipeline.application && it.name == pipeline.name
    })

    where:
    event                         | trigger               | triggerType
    createBuildEventWith(SUCCESS) | enabledJenkinsTrigger | 'jenkins'
    createBuildEventWith(SUCCESS) | enabledTravisTrigger | 'travis'
  }

  @Unroll
  def "attaches #triggerType trigger to the pipeline"() {
    given:
    pipelineCache.getPipelines() >> Observable.just([pipeline])

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    1 * subscriber.call({
      it.trigger.type == enabledJenkinsTrigger.type
      it.trigger.master == enabledJenkinsTrigger.master
      it.trigger.job == enabledJenkinsTrigger.job
      it.trigger.buildNumber == event.content.project.lastBuild.number
    })

    where:
    event                         | pipeline                                                     | triggerType
    createBuildEventWith(SUCCESS) | createPipelineWith(enabledJenkinsTrigger, nonJenkinsTrigger) | 'jenkins'
    createBuildEventWith(SUCCESS) | createPipelineWith(enabledTravisTrigger, nonJenkinsTrigger)  | 'travis'
  }

  def "an event can trigger multiple pipelines"() {
    given:
    pipelineCache.getPipelines() >> Observable.just(pipelines)

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    pipelines.size() * subscriber.call(_ as Pipeline)

    where:
    event = createBuildEventWith(SUCCESS)
    pipelines = (1..2).collect {
      Pipeline.builder()
        .application("application")
        .name("pipeline$it")
        .id("id")
        .triggers([enabledJenkinsTrigger])
        .build()
    }
  }

  @Unroll
  def "does not trigger pipelines for #description builds"() {
    given:
    pipelineCache.getPipelines() >> Observable.just([pipeline])

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    0 * subscriber._

    where:
    result   | _
    BUILDING | _
    FAILURE  | _
    ABORTED  | _
    null     | _

    pipeline = createPipelineWith(enabledJenkinsTrigger)
    event = createBuildEventWith(result)
    description = result ?: "unbuilt"
  }

  @Unroll
  def "does not trigger #description pipelines"() {
    given:
    pipelineCache.getPipelines() >> Observable.just([pipeline])

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    0 * subscriber._

    where:
    trigger                                 | description
    disabledJenkinsTrigger                  | "disabled jenkins"
    disabledTravisTrigger                   | "disabled travis"
    nonJenkinsTrigger                       | "non-Jenkins"
    enabledStashTrigger                     | "stash"
    enabledBitBucketTrigger                 | "bitbucket"
    enabledJenkinsTrigger.withMaster("FOO") | "different master"
    enabledJenkinsTrigger.withJob("FOO")    | "different job"

    pipeline = createPipelineWith(trigger)
    event = createBuildEventWith(SUCCESS)
  }

  @Unroll
  def "does not trigger a pipeline that has an enabled #triggerType trigger with missing #field"() {
    given:
    pipelineCache.getPipelines() >> Observable.just([badPipeline, goodPipeline])
    println objectMapper.writeValueAsString(createBuildEventWith(SUCCESS))

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    1 * subscriber.call({ it.id == goodPipeline.id })

    where:
    trigger                                | field    | triggerType
    enabledJenkinsTrigger.withMaster(null) | "master" | "jenkins"
    enabledJenkinsTrigger.withJob(null)    | "job"    | "jenkins"
    enabledTravisTrigger.withMaster(null)  | "master" | "travis"
    enabledTravisTrigger.withJob(null)     | "job"    | "travis"

    event = createBuildEventWith(SUCCESS)
    goodPipeline = createPipelineWith(enabledJenkinsTrigger)
    badPipeline = createPipelineWith(trigger)
  }
}
