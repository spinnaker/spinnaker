package com.netflix.spinnaker.echo.pipelinetriggers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.echo.model.Event
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.services.Front50Service
import com.netflix.spinnaker.echo.test.RetrofitStubs
import rx.functions.Action1
import rx.schedulers.Schedulers
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.echo.model.BuildEvent.Result.*
import static java.util.concurrent.TimeUnit.SECONDS
import static rx.Observable.empty
import static rx.Observable.just

class BuildEventMonitorSpec extends Specification implements RetrofitStubs {
  def objectMapper = new ObjectMapper()
  def scheduler = Schedulers.test()
  def front50 = Mock(Front50Service)
  def subscriber = Mock(Action1)
  def registry = new ExtendedRegistry(new NoopRegistry())

  @Shared
  def interval = 30

  @Subject
  def monitor = new BuildEventMonitor(scheduler, interval, front50, subscriber, registry)

  private waitForTicks(int n) {
    scheduler.advanceTimeBy(n * interval, SECONDS)
  }

  def "doesn't poll until started"() {
    when:
    waitForTicks(1)

    then:
    0 * _
  }

  def "doesn't poll after being stopped"() {
    given:
    monitor.start()
    monitor.stop()

    when:
    waitForTicks(1)

    then:
    0 * _
  }

  def "tolerates stop before start"() {
    when:
    monitor.stop()

    then:
    notThrown Throwable
  }

  def "tolerates multiple stop calls"() {
    given:
    monitor.start()
    monitor.stop()

    when:
    monitor.stop()

    then:
    notThrown Throwable
  }

  def "tolerates multiple start calls"() {
    given:
    monitor.start()
    monitor.start()
    monitor.stop()

    when:
    waitForTicks(1)

    then:
    0 * _
  }

  def "can be restarted after shutting down"() {
    given:
    monitor.start()
    monitor.stop()
    monitor.start()

    when:
    waitForTicks(1)

    then:
    1 * front50.getPipelines() >> empty()
  }

  @Unroll
  def "polls Mayo #ticks times in #delayTime seconds"() {
    given:
    monitor.start()

    when:
    waitForTicks(ticks)

    then:
    ticks * front50.getPipelines() >> empty()

    where:
    ticks | _
    1     | _
    2     | _

    delayTime = ticks * interval
  }

  def "keeps polling if Front50 returns an error"() {
    given:
    def pipeline = new Pipeline("application", "Pipeline", "P1", false, false, [], [], [], null, null)
    monitor.start()

    when:
    waitForTicks(3)

    then:
    front50.getPipelines() >> just([]) >> { throw unavailable() } >> just([pipeline])
    monitor.pipelines.get() == [pipeline]
  }

  def "triggers pipelines for successful builds"() {
    given:
    monitor.pipelines.set([pipeline])

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    1 * subscriber.call({
      it.application == pipeline.application && it.name == pipeline.name
    })

    where:
    event = createBuildEventWith(SUCCESS)
    pipeline = createPipelineWith(enabledJenkinsTrigger)
  }

  def "attaches the trigger to the pipeline"() {
    given:
    monitor.pipelines.set([pipeline])

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
    event = createBuildEventWith(SUCCESS)
    pipeline = createPipelineWith(enabledJenkinsTrigger, nonJenkinsTrigger)
  }

  def "an event can trigger multiple pipelines"() {
    given:
    monitor.pipelines.set(pipelines)

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
    monitor.pipelines.set([pipeline])

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
    monitor.pipelines.set([pipeline])

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    0 * subscriber._

    where:
    trigger                                 | description
    disabledJenkinsTrigger                  | "disabled"
    nonJenkinsTrigger                       | "non-Jenkins"
    enabledJenkinsTrigger.withMaster("FOO") | "different master"
    enabledJenkinsTrigger.withJob("FOO")    | "different job"

    pipeline = createPipelineWith(trigger)
    event = createBuildEventWith(SUCCESS)
  }

  @Unroll
  def "does not trigger a pipeline that has an enabled trigger with missing #field"() {
    given:
    monitor.pipelines.set([badPipeline, goodPipeline])
    println objectMapper.writeValueAsString(createBuildEventWith(SUCCESS))

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    1 * subscriber.call({ it.id == goodPipeline.id })

    where:
    trigger                                | field
    enabledJenkinsTrigger.withMaster(null) | "master"
    enabledJenkinsTrigger.withJob(null)    | "job"

    event = createBuildEventWith(SUCCESS)
    goodPipeline = createPipelineWith(enabledJenkinsTrigger)
    badPipeline = createPipelineWith(trigger)
  }
}
