/*
 * Copyright 2016 Google, Inc.
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
 */

package com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.test.RetrofitStubs
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class DockerEventHandlerSpec extends Specification implements RetrofitStubs {
  def registry = new NoopRegistry()
  def objectMapper = new ObjectMapper()
  def handlerSupport = new EventHandlerSupport()

  @Subject
  def eventHandler = new DockerEventHandler(registry, objectMapper)

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
    event               | trigger              | triggerType
    createDockerEvent() | enabledDockerTrigger | 'docker'
  }

  def "attaches docker trigger to the pipeline"() {
    given:
    def pipelines = handlerSupport.pipelineCache(pipeline)

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 1
    matchingPipelines[0].trigger.type == enabledDockerTrigger.type
    matchingPipelines[0].trigger.account == enabledDockerTrigger.account
    matchingPipelines[0].trigger.repository == enabledDockerTrigger.repository
    matchingPipelines[0].trigger.tag == enabledDockerTrigger.tag
    matchingPipelines[0].receivedArtifacts.size() == 1
    matchingPipelines[0].receivedArtifacts.get(0) == artifact

    where:
    event = createDockerEvent()
    artifact = Artifact.builder()
      .type("docker/image")
      .name(event.content.registry + "/" + event.content.repository)
      .version(event.content.tag)
      .reference(event.content.registry + "/" + event.content.repository + ":" + event.content.tag)
      .build()
    pipeline = createPipelineWith(enabledJenkinsTrigger, nonJenkinsTrigger, enabledDockerTrigger, disabledDockerTrigger)
  }

  def "an event can trigger multiple pipelines"() {
    given:
    def cache = handlerSupport.pipelineCache(pipelines)

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, cache)

    then:
    matchingPipelines.size() == pipelines.size()

    where:
    event = createDockerEvent()
    pipelines = (1..2).collect {
      Pipeline.builder()
        .application("application")
        .name("pipeline$it")
        .id("id")
        .triggers([enabledDockerTrigger])
        .build()
    }
  }

  @Unroll
  def "does not trigger #description pipelines"() {
    given:
    def pipelines = handlerSupport.pipelineCache(pipeline)

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 0

    where:
    trigger               | description
    disabledDockerTrigger | "disabled docker trigger"
    nonJenkinsTrigger     | "non-Jenkins"

    pipeline = createPipelineWith(trigger)
    event = createDockerEvent()
  }

  @Unroll
  def "does not trigger #description pipelines for docker"() {
    given:
    def pipelines = handlerSupport.pipelineCache(pipeline)

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 0

    where:
    trigger                                               | description
    disabledDockerTrigger                                 | "disabled docker trigger"
    enabledDockerTrigger.withAccount("notRegistry")       | "different registry"
    enabledDockerTrigger.withRepository("notRepository")  | "different repository"

    pipeline = createPipelineWith(trigger)
    event = createDockerEvent()
  }


  @Unroll
  def "does not trigger a pipeline that has an enabled docker trigger with missing #field"() {
    given:
    def pipelines = handlerSupport.pipelineCache(badPipeline, goodPipeline)

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 1
    matchingPipelines[0].id == goodPipeline.id

    where:
    trigger                                    | field
    enabledDockerTrigger.withAccount(null)     | "account"
    enabledDockerTrigger.withRepository(null)  | "repository"

    event = createDockerEvent()
    goodPipeline = createPipelineWith(enabledDockerTrigger)
    badPipeline = createPipelineWith(trigger)
  }

  @Unroll
  def "triggers a pipeline that has an enabled docker trigger with regex"() {
    given:
    def pipeline = createPipelineWith(trigger)
    def pipelines = handlerSupport.pipelineCache(pipeline)

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 1
    matchingPipelines[0].id == pipeline.id

    where:
    trigger                               | field
    enabledDockerTrigger.withTag("\\d+")  | "regex tag"

    event = createDockerEvent("2")
  }

  @Unroll
  def "triggers a pipeline that has an enabled docker trigger with empty string for regex"() {
    given:
    def pipeline = createPipelineWith(trigger)
    def pipelines = handlerSupport.pipelineCache(pipeline)

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 1
    matchingPipelines[0].id == pipeline.id

    where:
    trigger                               | field
    enabledDockerTrigger.withTag("")  | "regex tag"

    event = createDockerEvent("2")
  }

  @Unroll
  def "triggers a pipeline that has an enabled docker trigger with only whitespace for regex"() {
    given:
    def pipeline = createPipelineWith(trigger)
    def pipelines = handlerSupport.pipelineCache(pipeline)

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 1
    matchingPipelines[0].id == pipeline.id

    where:
    trigger                               | field
    enabledDockerTrigger.withTag(" \t")  | "regex tag"

    event = createDockerEvent("2")
  }

  @Unroll
  def "does not trigger a pipeline that has an enabled docker trigger with regex"() {
    given:
    def pipeline = createPipelineWith(trigger)
    def pipelines = handlerSupport.pipelineCache(pipeline)

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 0

    where:
    trigger                               | field
    enabledDockerTrigger.withTag("\\d+")  | "regex tag"

    event = createDockerEvent()
  }
}
