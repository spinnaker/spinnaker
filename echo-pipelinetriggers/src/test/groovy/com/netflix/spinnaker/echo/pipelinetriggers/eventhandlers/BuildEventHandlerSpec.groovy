package com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.echo.build.BuildInfoService
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.model.trigger.BuildEvent
import com.netflix.spinnaker.echo.services.IgorService
import com.netflix.spinnaker.echo.test.RetrofitStubs
import com.netflix.spinnaker.kork.core.RetrySupport
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.echo.model.trigger.BuildEvent.Result.*

class BuildEventHandlerSpec extends Specification implements RetrofitStubs {
  def registry = new NoopRegistry()
  def objectMapper = new ObjectMapper()
  def igorService = Mock(IgorService)
  def buildInformation = new BuildInfoService(igorService, new RetrySupport())

  String MASTER_NAME = "jenkins-server"
  String JOB_NAME = "my-job"
  int BUILD_NUMBER = 7
  def PROPERTY_FILE = "property-file"
  static Map<String, Object> BUILD_INFO = [
    abc: 123
  ]
  static Map<String, Object> PROPERTIES = [
    def: 456
  ]

  @Subject
  def eventHandler = new BuildEventHandler(registry, objectMapper, Optional.of(buildInformation))

  @Unroll
  def "triggers pipelines for successful builds for #triggerType"() {
    given:
    def pipeline = createPipelineWith(trigger)
    def pipelines = [pipeline]

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 1
    matchingPipelines[0].application == pipeline.application
    matchingPipelines[0].name == pipeline.name

    where:
    event                         | trigger               | triggerType
    createBuildEventWith(SUCCESS) | enabledJenkinsTrigger | 'jenkins'
    createBuildEventWith(SUCCESS) | enabledTravisTrigger  | 'travis'
    createBuildEventWith(SUCCESS) | enabledWerckerTrigger | 'wercker'
  }

  @Unroll
  def "attaches #triggerType trigger to the pipeline"() {
    given:
    def pipelines = [pipeline]

    when:
    def result = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    result.size() == 1
    result[0].trigger.type == expectedTrigger.type
    result[0].trigger.master == expectedTrigger.master
    result[0].trigger.job == expectedTrigger.job
    result[0].trigger.buildNumber == event.content.project.lastBuild.number

    where:
    event                         | pipeline                                                       | triggerType | expectedTrigger
    createBuildEventWith(SUCCESS) | createPipelineWith(enabledJenkinsTrigger, nonJenkinsTrigger)   | 'jenkins'   | enabledJenkinsTrigger
    createBuildEventWith(SUCCESS) | createPipelineWith(enabledTravisTrigger, nonJenkinsTrigger)    | 'travis'    | enabledTravisTrigger
    createBuildEventWith(SUCCESS) | createPipelineWith(enabledWerckerTrigger, nonJenkinsTrigger)   | 'wercker'   | enabledWerckerTrigger
    createBuildEventWith(SUCCESS) | createPipelineWith(enabledConcourseTrigger, nonJenkinsTrigger) | 'concourse' | enabledConcourseTrigger
  }

  def "an event can trigger multiple pipelines"() {
    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == pipelines.size()

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
    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, [pipeline])

    then:
    matchingPipelines.size() == 0

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
    def pipelines = [pipeline]

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 0

    where:
    trigger                                 | description
    disabledJenkinsTrigger                  | "disabled jenkins"
    disabledTravisTrigger                   | "disabled travis"
    disabledWerckerTrigger                  | "disabled wercker"
    disabledConcourseTrigger                | "disabled concourse"
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
    def pipelines = [badPipeline, goodPipeline]

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 1
    matchingPipelines[0].id == goodPipeline.id

    where:
    trigger                                | field    | triggerType
    enabledJenkinsTrigger.withMaster(null) | "master" | "jenkins"
    enabledJenkinsTrigger.withJob(null)    | "job"    | "jenkins"
    enabledTravisTrigger.withMaster(null)  | "master" | "travis"
    enabledTravisTrigger.withJob(null)     | "job"    | "travis"
    enabledWerckerTrigger.withMaster(null) | "master" | "wercker"
    enabledWerckerTrigger.withJob(null)    | "job"    | "wercker"

    event = createBuildEventWith(SUCCESS)
    goodPipeline = createPipelineWith(enabledJenkinsTrigger)
    badPipeline = createPipelineWith(trigger)
  }

  def "fetches build info if defined"() {
    given:
    def trigger = enabledJenkinsTrigger
      .withMaster(MASTER_NAME)
      .withJob(JOB_NAME)
      .withBuildNumber(BUILD_NUMBER)

    def inputPipeline = createPipelineWith(enabledJenkinsTrigger).withTrigger(trigger)
    def event = getBuildEvent()

    when:
    def outputTrigger = eventHandler.buildTrigger(event).apply(trigger)

    then:
    1 * igorService.getBuild(BUILD_NUMBER, MASTER_NAME, JOB_NAME) >> BUILD_INFO
    outputTrigger.buildInfo.equals(BUILD_INFO)
  }

  def "fetches property file if defined"() {
    given:
    def trigger = enabledJenkinsTrigger
      .withMaster(MASTER_NAME)
      .withJob(JOB_NAME)
      .withBuildNumber(BUILD_NUMBER)
      .withPropertyFile(PROPERTY_FILE)

    def inputPipeline = createPipelineWith(enabledJenkinsTrigger).withTrigger(trigger)
    def event = getBuildEvent()

    when:
    def outputTrigger = eventHandler.buildTrigger(event).apply(trigger)

    then:
    1 * igorService.getBuild(BUILD_NUMBER, MASTER_NAME, JOB_NAME) >> BUILD_INFO
    1 * igorService.getPropertyFile(BUILD_NUMBER, PROPERTY_FILE, MASTER_NAME, JOB_NAME) >> PROPERTIES
    outputTrigger.buildInfo.equals(BUILD_INFO)
    outputTrigger.properties.equals(PROPERTIES)
  }

  def "retries on failure to communicate with igor"() {
    given:
    def trigger = enabledJenkinsTrigger
      .withMaster(MASTER_NAME)
      .withJob(JOB_NAME)
      .withBuildNumber(BUILD_NUMBER)
      .withPropertyFile(PROPERTY_FILE)

    def inputPipeline = createPipelineWith(enabledJenkinsTrigger).withTrigger(trigger)
    def event = getBuildEvent()

    when:
    def outputTrigger = eventHandler.buildTrigger(event).apply(trigger)

    then:
    2 * igorService.getBuild(BUILD_NUMBER, MASTER_NAME, JOB_NAME) >> { throw new RuntimeException() } >> BUILD_INFO
    1 * igorService.getPropertyFile(BUILD_NUMBER, PROPERTY_FILE, MASTER_NAME, JOB_NAME) >> PROPERTIES
    outputTrigger.buildInfo.equals(BUILD_INFO)
    outputTrigger.properties.equals(PROPERTIES)
  }

  def getBuildEvent() {
    def build = new BuildEvent.Build(number: BUILD_NUMBER, building: false, result: SUCCESS)
    def project = new BuildEvent.Project(name: JOB_NAME, lastBuild: build)
    return new BuildEvent(content: new BuildEvent.Content(master: MASTER_NAME, project: project))
  }
}
