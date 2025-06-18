/*
 * Copyright 2025 Harness, Inc.
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

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.echo.api.events.Metadata
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.model.trigger.AbstractDockerEvent
import com.netflix.spinnaker.echo.model.trigger.HelmOciEvent
import com.netflix.spinnaker.echo.test.RetrofitStubs
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class HelmOciEventHandlerSpec extends Specification implements RetrofitStubs {
  def registry = new NoopRegistry()
  def objectMapper = EchoObjectMapper.getInstance()
  def handlerSupport = new EventHandlerSupport()
  def fiatPermissionEvaluator = Mock(FiatPermissionEvaluator)

  @Subject
  def eventHandler = new HelmOciEventHandler(registry, objectMapper, fiatPermissionEvaluator)

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
    event                       | trigger              | triggerType
    createHelmOciEvent('tag')   | enabledHelmOciTrigger | 'helm/oci'
  }

  def "attaches helm oci trigger to the pipeline"() {
    given:
    def pipelines = handlerSupport.pipelineCache(pipeline)

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 1
    matchingPipelines[0].trigger.type == enabledHelmOciTrigger.type
    matchingPipelines[0].trigger.account == enabledHelmOciTrigger.account
    matchingPipelines[0].trigger.repository == enabledHelmOciTrigger.repository
    matchingPipelines[0].trigger.tag == enabledHelmOciTrigger.tag
    matchingPipelines[0].receivedArtifacts.size() == 1
    matchingPipelines[0].receivedArtifacts.get(0) == artifact

    where:
    event = createHelmOciEvent(enabledHelmOciTrigger.tag)
    artifact = Artifact.builder()
      .type("helm/image")
      .name(event.content.registry + "/" + event.content.repository)
      .version(event.content.tag)
      .reference(event.content.registry + "/" + event.content.repository + ":" + event.content.tag)
      .build()
    pipeline = createPipelineWith(enabledJenkinsTrigger, nonJenkinsTrigger, enabledHelmOciTrigger, disabledHelmOciTrigger)
  }

  def "attaches helm oci trigger digest to the pipeline"() {
    given:
    def pipelines = handlerSupport.pipelineCache(pipeline)

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 1
    matchingPipelines[0].trigger.type == enabledHelmOciTrigger.type
    matchingPipelines[0].trigger.account == enabledHelmOciTrigger.account
    matchingPipelines[0].trigger.repository == enabledHelmOciTrigger.repository
    matchingPipelines[0].trigger.tag == "tag"
    matchingPipelines[0].trigger.digest == "sha123"
    matchingPipelines[0].receivedArtifacts.size() == 1
    matchingPipelines[0].receivedArtifacts.get(0) == artifact

    where:
    event = createHelmOciEvent("tag", "sha123")
    artifact = Artifact.builder()
      .type("helm/image")
      .name(event.content.registry + "/" + event.content.repository)
      .version(event.content.tag)
      .reference(event.content.registry + "/" + event.content.repository + ":" + event.content.tag)
      .build()
    pipeline = createPipelineWith(enabledJenkinsTrigger, nonJenkinsTrigger, enabledHelmOciTrigger, disabledHelmOciTrigger)
  }

  def "an event can trigger multiple pipelines"() {
    given:
    def cache = handlerSupport.pipelineCache(pipelines)

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, cache)

    then:
    matchingPipelines.size() == pipelines.size()

    where:
    event = createHelmOciEvent(enabledHelmOciTrigger.tag)
    pipelines = (1..2).collect {
      Pipeline.builder()
        .application("application")
        .name("pipeline$it")
        .id("id")
        .triggers([enabledHelmOciTrigger])
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
    disabledHelmOciTrigger | "disabled helm oci trigger"
    nonJenkinsTrigger     | "non-HelmOci"

    pipeline = createPipelineWith(trigger)
    event = createHelmOciEvent(enabledHelmOciTrigger.tag)
  }

  @Unroll
  def "does not trigger #description pipelines for helm oci"() {
    given:
    def pipelines = handlerSupport.pipelineCache(pipeline)

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 0

    where:
    trigger                                                | description
    disabledHelmOciTrigger                                 | "disabled helm oci trigger"
    enabledHelmOciTrigger.withAccount("notRegistry")       | "different registry"
    enabledHelmOciTrigger.withRepository("notRepository")  | "different repository"

    pipeline = createPipelineWith(trigger)
    event = createHelmOciEvent(enabledHelmOciTrigger.tag)
  }

  @Unroll
  def "does not trigger a pipeline that has an enabled helm oci trigger with missing #field"() {
    given:
    def pipelines = handlerSupport.pipelineCache(badPipeline, goodPipeline)

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 1
    matchingPipelines[0].id == goodPipeline.id

    where:
    trigger                                     | field
    enabledHelmOciTrigger.withAccount(null)     | "account"
    enabledHelmOciTrigger.withRepository(null)  | "repository"

    event = createHelmOciEvent(enabledHelmOciTrigger.tag)
    goodPipeline = createPipelineWith(enabledHelmOciTrigger)
    badPipeline = createPipelineWith(trigger)
  }

  @Unroll
  def "triggers a pipeline that has an enabled helm oci trigger with regex"() {
    given:
    def pipeline = createPipelineWith(trigger)
    def pipelines = handlerSupport.pipelineCache(pipeline)

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 1
    matchingPipelines[0].id == pipeline.id

    where:
    trigger                                | field
    enabledHelmOciTrigger.withTag("\\d+")  | "regex tag"

    event = createHelmOciEvent("2")
  }

}
