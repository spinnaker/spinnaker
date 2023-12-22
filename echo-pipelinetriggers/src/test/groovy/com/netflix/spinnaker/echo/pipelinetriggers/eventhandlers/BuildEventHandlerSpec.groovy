package com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.echo.build.BuildInfoService
import com.netflix.spinnaker.echo.config.IgorConfigurationProperties
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.model.trigger.BuildEvent
import com.netflix.spinnaker.echo.services.IgorService
import com.netflix.spinnaker.echo.test.RetrofitStubs
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.kork.core.RetrySupport
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.echo.model.trigger.BuildEvent.Result.*

class BuildEventHandlerSpec extends Specification implements RetrofitStubs {
  def registry = new NoopRegistry()
  def objectMapper = EchoObjectMapper.getInstance()
  def igorService = Mock(IgorService)
  def buildInformation = new BuildInfoService(igorService, new RetrySupport(), new IgorConfigurationProperties(jobNameAsQueryParameter: false))
  def handlerSupport = new EventHandlerSupport()
  def fiatPermissionEvaluator = Mock(FiatPermissionEvaluator)

  String MASTER_NAME = "jenkins-server"
  String JOB_NAME = "my-job"
  int BUILD_NUMBER = 7
  def PROPERTY_FILE = "property-file"
  static Map<String, Object> BUILD_INFO = [
    abc: 123
  ]
  static Map<String, Object> PROPERTIES = [
    def: 456,
    branch: "feature/my-thing"
  ]
  static Map<String, Object> CONSTRAINTS = [
    def: "^[0-9]*\$", // def must be a positive number,
    branch: "^(feature)/.*\$" // only trigger on branch name like "feature/***"
  ]

  @Subject
  def eventHandler = new BuildEventHandler(registry, objectMapper, Optional.of(buildInformation), fiatPermissionEvaluator)

  void setup() {
    fiatPermissionEvaluator.hasPermission(_ as String, _ as String, "APPLICATION", "EXECUTE") >> true
  }

  @Unroll
  def "triggers pipelines for successful builds for #triggerType"() {
    given:
    def pipeline = createPipelineWith(trigger)
    def pipelines = handlerSupport.pipelineCache(pipeline)

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
    def pipelines = handlerSupport.pipelineCache(pipeline)

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
    given:
    def cache = handlerSupport.pipelineCache(pipelines)

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, cache)

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

  def "an event triggers a pipeline with 2 triggers only once"() {
    given:
    def pipeline = Pipeline.builder()
      .application("application")
      .name("pipeline")
      .id("id")
      .triggers([
        enabledJenkinsTrigger,
        enabledJenkinsTrigger.withJob("someOtherJob")])
      .build()
    def cache = handlerSupport.pipelineCache(pipeline)
    def event = createBuildEventWith(SUCCESS)

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, cache)

    then:
    matchingPipelines.size() == 1
    matchingPipelines.get(0).trigger.job == "job"
  }

  def "an event triggers a pipeline with 2 matching triggers only once"() {
    given:
    def pipeline = Pipeline.builder()
      .application("application")
      .name("pipeline")
      .id("id")
      .triggers([
        enabledJenkinsTrigger,
        enabledJenkinsTrigger])
      .build()
    def cache = handlerSupport.pipelineCache(pipeline)
    def event = createBuildEventWith(SUCCESS)

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, cache)

    then:
    matchingPipelines.size() == 1
  }


  @Unroll
  def "does not trigger pipelines for #description builds"() {
    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, handlerSupport.pipelineCache(pipeline))

    then:
    matchingPipelines.isEmpty()

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
    def pipelines = handlerSupport.pipelineCache(pipeline)

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.isEmpty()

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
    def pipelines = handlerSupport.pipelineCache(badPipeline, goodPipeline)

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

  def "getBuildInfo method gets job name from query when flag is true"()
  {
    given:
    def mockBuildInfo = BUILD_INFO
    def trigger = enabledJenkinsTrigger.withMaster(MASTER_NAME).withBuildNumber(BUILD_NUMBER)
    createPipelineWith(enabledJenkinsTrigger).withTrigger(trigger)
    def event = getBuildEvent()

    def retrySupport = new RetrySupport()
    def configProperties = new IgorConfigurationProperties(jobNameAsQueryParameter: true)
    def buildInfoService = new BuildInfoService(igorService, retrySupport, configProperties)

    def permissionEvaluator = fiatPermissionEvaluator
    def buildEventHandler = new BuildEventHandler(registry, objectMapper, Optional.of(buildInfoService), permissionEvaluator)

    when:
    def outputTrigger = buildEventHandler.buildTrigger(event).apply(trigger)

    then:
    1 * igorService.getBuildStatusWithJobQueryParameter(BUILD_NUMBER, MASTER_NAME, JOB_NAME) >> mockBuildInfo
    outputTrigger.buildInfo == mockBuildInfo
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

 def "checks constraints on property file if defined"() {
    given:
    def trigger = enabledJenkinsTrigger
      .withMaster(MASTER_NAME)
      .withJob(JOB_NAME)
      .withBuildNumber(BUILD_NUMBER)
      .withPropertyFile(PROPERTY_FILE)
      .withPayloadConstraints(CONSTRAINTS)

    def inputPipeline = createPipelineWith(enabledJenkinsTrigger).withTrigger(trigger)
    def event = getBuildEvent()

    when:
    def matchTriggerPredicate = eventHandler.matchTriggerFor(event).test(trigger)

    then:
    1 * igorService.getPropertyFile(BUILD_NUMBER, PROPERTY_FILE, MASTER_NAME, JOB_NAME) >> PROPERTIES
    matchTriggerPredicate.equals(true)
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

  @Unroll
  def "#description1 trigger a pipeline if the user #description2 access to the application"() {
    given:
    def pipeline = Pipeline.builder()
      .application("application")
      .name("pipeline")
      .id("id")
      .triggers([trigger])
      .build()

    def cache = handlerSupport.pipelineCache(pipeline)
    def event = createBuildEventWith(SUCCESS)

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, cache)

    then:
    0 * fiatPermissionEvaluator.hasPermission(_ as String, _ as String, "APPLICATION", "EXECUTE")
    1 * fiatPermissionEvaluator.hasPermission(trigger.runAsUser?: "anonymous", "application", "APPLICATION", "EXECUTE") >> hasPermission
    matchingPipelines.size() == (hasPermission ? 1 : 0)

    where:
    trigger                            | hasPermission | description1 | description2
    enabledConcourseTrigger            | false         | "should not" | "does not have"
    enabledJenkinsTriggerWithRunAsUser | true          | "should"     | "has"
  }

  def getBuildEvent() {
    def build = new BuildEvent.Build(number: BUILD_NUMBER, building: false, result: SUCCESS)
    def project = new BuildEvent.Project(name: JOB_NAME, lastBuild: build)
    return new BuildEvent(content: new BuildEvent.Content(master: MASTER_NAME, project: project))
  }
}
