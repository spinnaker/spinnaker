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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.artifacts.ArtifactInfoService
import com.netflix.spinnaker.echo.build.BuildInfoService
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.model.trigger.ManualEvent
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache
import com.netflix.spinnaker.echo.test.RetrofitStubs
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import retrofit.RetrofitError
import retrofit.client.Response
import spock.lang.Specification
import spock.lang.Subject

class ManualEventHandlerSpec extends Specification implements RetrofitStubs {
  def objectMapper = new ObjectMapper()
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
    artifactInfoService.getArtifactByVersion("artifactory", "my-package", "v2.2.2") >> {
      throw RetrofitError.httpError("http://foo", new Response("http://foo", 404, "not found", [], null), null, null)
    }
    List<Artifact> resolvedArtifacts = eventHandler.resolveArtifacts(triggerArtifacts)
    Map<String, Object> firstArtifact = objectMapper.convertValue(resolvedArtifacts.first(), Map.class)
    firstArtifact = firstArtifact.findAll { key, value -> value != null && key != "customKind"}

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
    artifactInfoService.getArtifactByVersion("artifactory", "my-package", "v2.2.2") >> {
      throw RetrofitError.httpError("http://foo", new Response("http://foo", 404, "not found", [], null), null, null)
    }
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
    artifactInfoService.getArtifactByVersion("artifactory", "my-package", "v2.2.2") >> {
      throw RetrofitError.httpError("http://foo", new Response("http://foo", 404, "not found", [], null), null, null)
    }
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
}


