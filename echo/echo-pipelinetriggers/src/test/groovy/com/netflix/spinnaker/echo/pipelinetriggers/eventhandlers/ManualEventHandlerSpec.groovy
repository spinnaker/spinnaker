/*
 *
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers

import com.netflix.spinnaker.echo.artifacts.ArtifactInfoService
import com.netflix.spinnaker.echo.build.BuildInfoService
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.model.trigger.ManualEvent
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache
import com.netflix.spinnaker.echo.test.RetrofitStubs
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import spock.lang.Specification
import spock.lang.Subject

class ManualEventHandlerSpec extends Specification implements RetrofitStubs {
  def objectMapper = EchoObjectMapper.getInstance()
  def buildInfoService = Mock(BuildInfoService)
  def artifactInfoService = Mock(ArtifactInfoService)
  def pipelineCache = Mock(PipelineCache)

  Artifact artifact = Artifact.builder()
    .type("deb")
    .customKind(false)
    .name("my-package")
    .version("v1.1.1")
    .location("https://artifactory/my-package/")
    .reference("https://artifactory/my-package/")
    .metadata([:])
    .artifactAccount("account")
    .provenance("provenance")
    .uuid("123456")
    .build()

  @Subject
  def eventHandler = new ManualEventHandler(
    objectMapper,
    Optional.of(buildInfoService),
    Optional.of(artifactInfoService),
    pipelineCache
  )

  def "should replace artifact with full version if it exists"() {
    given:
    Map<String, Object> triggerArtifact = [
      name: "my-package",
      version: "v1.1.1",
      location: "artifactory"
    ]

    Trigger trigger = Trigger.builder().enabled(true).type("artifact").artifactName("my-package").build()
    Pipeline inputPipeline = createPipelineWith(trigger)
    Trigger manualTrigger = Trigger.builder().type("manual").artifacts([triggerArtifact]).build()

    when:
    artifactInfoService.getArtifactByVersion("artifactory", "my-package", "v1.1.1") >> artifact
    def resolvedPipeline = eventHandler.buildTrigger(inputPipeline, manualTrigger)

    then:
    resolvedPipeline.receivedArtifacts.size() == 1
    resolvedPipeline.receivedArtifacts.first().name == "my-package"
    resolvedPipeline.receivedArtifacts.first().reference == "https://artifactory/my-package/"
  }

  def "should resolve artifact if it exists"() {
    given:
    Map<String, Object> triggerArtifact = [
      name: "my-package",
      version: "v1.1.1",
      location: "artifactory"
    ]
    List<Map<String, Object>> triggerArtifacts = [triggerArtifact]

    when:
    artifactInfoService.getArtifactByVersion("artifactory", "my-package", "v1.1.1") >> artifact
    List<Artifact> resolvedArtifacts = eventHandler.resolveArtifacts(triggerArtifacts)

    then:
    resolvedArtifacts.size() == 1
    resolvedArtifacts.first().name == "my-package"
  }

  def "should not resolve artifact if it doesn't exist"() {
    given:
    Map<String, Object> triggerArtifact = [
      name: "my-package",
      version: "v2.2.2",
      location: "artifactory"
    ]
    List<Map<String, Object>> triggerArtifacts = [triggerArtifact]

    when:
    artifactInfoService.getArtifactByVersion("artifactory", "my-package", "v2.2.2") >> { throw makeSpinnakerHttpException() }
    List<Artifact> resolvedArtifacts = eventHandler.resolveArtifacts(triggerArtifacts)
    Map<String, Object> firstArtifact = objectMapper.convertValue(resolvedArtifacts.first(), Map.class)
    firstArtifact = firstArtifact.findAll { key, value -> value && key != "customKind"}

    then:
    resolvedArtifacts.size() == 1
    firstArtifact == triggerArtifact
  }

  def "should do nothing with artifact if it doesn't exist"() {
    given:
    Map<String, Object> triggerArtifact = [
      name: "my-package",
      version: "v2.2.2",
      location: "artifactory"
    ]
    Trigger trigger = Trigger.builder().enabled(true).type("artifact").artifactName("my-package").build()
    Pipeline inputPipeline = createPipelineWith(trigger)
    Trigger manualTrigger = Trigger.builder().type("manual").artifacts([triggerArtifact]).build()

    when:
    artifactInfoService.getArtifactByVersion("artifactory", "my-package", "v2.2.2") >> { throw makeSpinnakerHttpException() }
    def resolvedPipeline = eventHandler.buildTrigger(inputPipeline, manualTrigger)

    then:
    resolvedPipeline.receivedArtifacts.size() == 1
  }

  def "should do nothing with artifact if it doesn't have the right fields"() {
    given:
    Map<String, Object> triggerArtifact = [
      name: "my-package",
      version: "v2.2.2",
    ]
    Trigger trigger = Trigger.builder().enabled(true).type("artifact").artifactName("my-package").build()
    Pipeline inputPipeline = createPipelineWith(trigger)
    Trigger manualTrigger = Trigger.builder().type("manual").artifacts([triggerArtifact]).build()

    when:
    artifactInfoService.getArtifactByVersion("artifactory", "my-package", "v2.2.2") >> { throw makeSpinnakerHttpException() }
    def resolvedPipeline = eventHandler.buildTrigger(inputPipeline, manualTrigger)

    then:
    resolvedPipeline.receivedArtifacts.size() == 1
  }

  def "should trigger a pipeline refresh before building the trigger"() {
    given:
    Pipeline inputPipeline = Pipeline.builder()
      .application("application")
      .name("stale")
      .id("boop-de-boop")
      .build()

    def user = "definitely not a robot"
    def manualEvent = new ManualEvent()
    manualEvent.content = new ManualEvent.Content()
    manualEvent.content.pipelineNameOrId = inputPipeline.id
    manualEvent.content.application = inputPipeline.application
    manualEvent.content.trigger = Trigger.builder().user(user).build()


    when: 'we generate the pipeline config we are about to start'
    def materializedPipeline = eventHandler.withMatchingTrigger(manualEvent, inputPipeline)

    then: 'we get a fresh config from front50, not the potentially stale config from the pipeline cache'
    1 * pipelineCache.refresh(inputPipeline) >> Pipeline.builder()
      .application("application")
      .name("fresh")
      .id("boop-de-boop")
      .build()
    materializedPipeline.isPresent()
    materializedPipeline.get().name == "fresh"

    and: 'the materialized pipeline has the trigger field populated from the manual event'
    materializedPipeline.get().trigger.user == user
  }

  def "doesn't retrieve the pipeline by id from front50 if it's not present in the pipeline cache, unless configured to do so"() {
    given:
    String application = "application"
    String pipelineName = "my-pipeline-name"
    String pipelineId = "my-pipeline-id"

    Pipeline inputPipeline = Pipeline.builder()
      .application(application)
      .name(pipelineName)
      .id(pipelineId)
      .build()

    def manualEvent = new ManualEvent()
    manualEvent.content = new ManualEvent.Content()
    manualEvent.content.pipelineNameOrId = inputPipeline.id
    manualEvent.content.application = inputPipeline.application
    manualEvent.content.trigger = Trigger.builder().build()

    when:
    List<Pipeline> pipelines = eventHandler.getMatchingPipelines(manualEvent, pipelineCache)

    then:
    1 * pipelineCache.getPipelinesSync() >> Collections.emptyList()
    1 * pipelineCache.isFilterFront50Pipelines() >> false
    0 * pipelineCache._

    pipelines.size() == 0
  }

  def "retrieves the pipeline by name from front50 if it's not present in the pipeline cache, when configured to do so"() {
    given:
    String application = "application"
    String pipelineName = "my-pipeline-name"
    String pipelineId = "my-pipeline-id"

    Pipeline inputPipeline = Pipeline.builder()
      .application(application)
      .name(pipelineName)
      .id(pipelineId)
      .build()

    def manualEvent = new ManualEvent()
    manualEvent.content = new ManualEvent.Content()
    manualEvent.content.pipelineNameOrId = inputPipeline.name
    manualEvent.content.application = inputPipeline.application
    manualEvent.content.trigger = Trigger.builder().build()

    when:
    List<Pipeline> pipelines = eventHandler.getMatchingPipelines(manualEvent, pipelineCache)

    then:
    1 * pipelineCache.getPipelinesSync() >> Collections.emptyList()
    1 * pipelineCache.isFilterFront50Pipelines() >> true
    1 * pipelineCache.getPipelineByName(application, pipelineName) >> Optional.of(inputPipeline)
    0 * pipelineCache._

    pipelines.size() == 1

    // pipelines.first() != inputPipeline because ManualEventHandler calls
    // buildTrigger which adds a trigger.  It's enough to compare id,
    // application, and name.  Leave examining the trigger for another test.
    pipelines.first().id == pipelineId
    pipelines.first().application == application
    pipelines.first().name == pipelineName
  }

  def "retrieves the pipeline by id from front50 if it's not present in the pipeline cache, nor available by name, when configured to do so"() {
    given:
    String application = "application"
    String pipelineName = "my-pipeline-name"
    String pipelineId = "my-pipeline-id"

    Pipeline inputPipeline = Pipeline.builder()
      .application(application)
      .name(pipelineName)
      .id(pipelineId)
      .build()

    def manualEvent = new ManualEvent()
    manualEvent.content = new ManualEvent.Content()
    manualEvent.content.pipelineNameOrId = inputPipeline.id
    manualEvent.content.application = inputPipeline.application
    manualEvent.content.trigger = Trigger.builder().build()

    when:
    List<Pipeline> pipelines = eventHandler.getMatchingPipelines(manualEvent, pipelineCache)

    then:
    1 * pipelineCache.getPipelinesSync() >> Collections.emptyList()
    1 * pipelineCache.isFilterFront50Pipelines() >> true
    1 * pipelineCache.getPipelineByName(application, pipelineId) >> Optional.empty()
    1 * pipelineCache.getPipelineById(pipelineId) >> Optional.of(inputPipeline)
    0 * pipelineCache._

    pipelines.size() == 1
    // pipelines.first() != inputPipeline because ManualEventHandler calls
    // buildTrigger which adds a trigger.  It's enough to compare id,
    // application, and name.  Leave examining the trigger for another test.
    pipelines.first().id == pipelineId
    pipelines.first().application == application
    pipelines.first().name == pipelineName
  }

  def "retrieves the pipeline from front50 if it's not present in the pipeline cache, and ignores it if disabled, when configured to do so"() {
    given:
    String application = "application"
    String pipelineName = "my-pipeline-name"
    String pipelineId = "my-pipeline-id"

    Pipeline inputPipeline = Pipeline.builder()
      .application(application)
      .name(pipelineName)
      .id(pipelineId)
      .disabled(true)
      .build()

    def manualEvent = new ManualEvent()
    manualEvent.content = new ManualEvent.Content()
    manualEvent.content.pipelineNameOrId = inputPipeline.name
    manualEvent.content.application = inputPipeline.application
    manualEvent.content.trigger = Trigger.builder().build()

    when:
    List<Pipeline> pipelines = eventHandler.getMatchingPipelines(manualEvent, pipelineCache)

    then:
    1 * pipelineCache.getPipelinesSync() >> Collections.emptyList()
    1 * pipelineCache.isFilterFront50Pipelines() >> true
    1 * pipelineCache.getPipelineByName(application, pipelineName) >> Optional.of(inputPipeline)
    0 * pipelineCache._

    pipelines.size() == 0
  }

  def "retrieves the pipeline from front50 if it's not present in the pipeline cache, and rejects it if it doesn't match the trigger, when configured to do so"() {
    given:
    String application = "application"
    String pipelineName = "my-pipeline-name"
    String pipelineId = "my-pipeline-id"

    Pipeline inputPipeline = Pipeline.builder()
      .application(application)
      .name(pipelineName)
      .id(pipelineId)
      .disabled(true)
      .build()

    def manualEvent = new ManualEvent()
    manualEvent.content = new ManualEvent.Content()
    manualEvent.content.pipelineNameOrId = inputPipeline.name
    manualEvent.content.application = inputPipeline.application
    manualEvent.content.trigger = Trigger.builder().build()

    when:
    List<Pipeline> pipelines = eventHandler.getMatchingPipelines(manualEvent, pipelineCache)

    then:
    1 * pipelineCache.getPipelinesSync() >> Collections.emptyList()
    1 * pipelineCache.isFilterFront50Pipelines() >> true

    // If either the name or id match, it's considered matching, so provide both
    // a different name and different id from front50.
    1 * pipelineCache.getPipelineByName(application, pipelineName) >> Optional.of(Pipeline.builder().application(application).name("some-other-name").id("some-other-id").build())
    0 * pipelineCache._

    pipelines.size() == 0
  }

  def "handles exceptions retrieving the pipeline from front50 if it's not present in the pipeline cache, when configured to do so"() {
    given:
    String application = "application"
    String pipelineName = "my-pipeline-name"
    String pipelineId = "my-pipeline-id"

    Pipeline inputPipeline = Pipeline.builder()
      .application(application)
      .name(pipelineName)
      .id(pipelineId)
      .disabled(true)
      .build()

    RuntimeException arbitraryException = new RuntimeException("arbitrary message")

    def manualEvent = new ManualEvent()
    manualEvent.content = new ManualEvent.Content()
    // arbitary choice whether to use id or name
    manualEvent.content.pipelineNameOrId = inputPipeline.name
    manualEvent.content.application = inputPipeline.application
    manualEvent.content.trigger = Trigger.builder().build()

    when:
    List<Pipeline> pipelines = eventHandler.getMatchingPipelines(manualEvent, pipelineCache)

    then:
    1 * pipelineCache.getPipelinesSync() >> Collections.emptyList()
    1 * pipelineCache.isFilterFront50Pipelines() >> true
    1 * pipelineCache.getPipelineByName(application, pipelineName) >> { throw arbitraryException }
    0 * pipelineCache._

    pipelines.size() == 1
    pipelines.first().application == application

    // ManualEventHandler doesn't know whether the trigger specifies a name or
    // id.  Because name is a required field in pipelines, it uses it as the
    // name.  It happens to match because that's what we specified in the
    // trigger.
    pipelines.first().name == pipelineName
    pipelines.first().errorMessage == arbitraryException.toString()
  }

  SpinnakerHttpException makeSpinnakerHttpException(){
    String url = "https://some-url";
    retrofit2.Response retrofit2Response =
      retrofit2.Response.error(
        404,
        ResponseBody.create(
          "{ \"message\": \"arbitrary message\" }", MediaType.parse("application/json")));

    Retrofit retrofit =
      new Retrofit.Builder()
        .baseUrl(url)
        .addConverterFactory(JacksonConverterFactory.create())
        .build();

    new SpinnakerHttpException(retrofit2Response, retrofit);
  }
}
