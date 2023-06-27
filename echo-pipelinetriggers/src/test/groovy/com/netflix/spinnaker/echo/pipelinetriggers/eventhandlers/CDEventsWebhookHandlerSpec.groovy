/*
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
import com.netflix.spinnaker.echo.model.pubsub.MessageDescription
import com.netflix.spinnaker.echo.model.pubsub.PubsubSystem
import com.netflix.spinnaker.echo.model.trigger.PubsubEvent
import com.netflix.spinnaker.echo.test.RetrofitStubs
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact
import groovy.json.JsonOutput
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class CDEventsWebhookHandlerSpec extends Specification implements RetrofitStubs {
  def registry = new NoopRegistry()
  def objectMapper = EchoObjectMapper.getInstance()
  def handlerSupport = new EventHandlerSupport()
  def fiatPermissionEvaluator = Mock(FiatPermissionEvaluator)

  @Shared
  def goodExpectedArtifacts = [
      ExpectedArtifact.builder()
        .matchArtifact(
          Artifact.builder()
            .name('myArtifact')
            .type('artifactType')
            .build())
        .id('goodId')
        .build()
  ]

  @Subject
  def eventHandler = new CDEventsWebhookHandler(registry, objectMapper, fiatPermissionEvaluator)

  void setup() {
    fiatPermissionEvaluator.hasPermission(_ as String, _ as String, "APPLICATION", "EXECUTE") >> true
  }

  def 'triggers pipelines for successful builds for CDEvent'() {
    given:
    def pipeline = createPipelineWith(goodExpectedArtifacts, trigger)
    def pipelines = handlerSupport.pipelineCache(pipeline)

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)
    print("matchingPipelines=======>>>> " +matchingPipelines)

    then:
    matchingPipelines.size() == 1
    matchingPipelines[0].application == pipeline.application
    matchingPipelines[0].name == pipeline.name

    where:
    event = createCDEvent('pipelineRunFinished',
        [foo: 'bar', artifacts: [[name: 'myArtifact', type: 'artifactType']]])
    trigger = enabledCDEventsTrigger
        .withSource('pipelineRunFinished')
        .withPayloadConstraints([foo: 'bar'])
        .withExpectedArtifactIds(['goodId'])
  }

  def 'attaches cdevents trigger to the pipeline'() {
    given:
    def pipelines = handlerSupport.pipelineCache(pipeline)

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 1
    matchingPipelines[0].trigger.type == enabledCDEventsTrigger.type

    where:
    event = createCDEvent('pipelineRunStarted')
    pipeline = createPipelineWith([],
        enabledCDEventsTrigger.withSource('pipelineRunStarted'),
      disabledCDEventsTrigger)
  }

  def "triggers pipeline on matching attribute constraints"() {
    given:
    def pipeline = createPipelineWith(goodExpectedArtifacts, trigger)
    def pipelines = handlerSupport.pipelineCache(pipeline)
    def requestHeaders = new TreeMap<>()
    def listHeaders = new ArrayList<>()
    listHeaders.add("dev.cdevents.artifactPublished")
    requestHeaders.put("ce-type", listHeaders)
    def event = createCDEventRequestHeaders('artifactPublished',
      [foo: 'bar', artifacts: [[name: 'myArtifact', type: 'artifactType']]], requestHeaders )

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 1
    matchingPipelines[0].application == pipeline.application
    matchingPipelines[0].name == pipeline.name

    where:
    trigger = enabledCDEventsTrigger
      .withSource('artifactPublished')
      .withAttributeConstraints(['ce-type':'dev.cdevents.artifactPublished'])
      .withPayloadConstraints([foo: 'bar'])
      .withExpectedArtifactIds(['goodId'])

  }

  @Unroll
  def "does not trigger #description pipelines for CDEvent"() {
    given:
    def pipelines = handlerSupport.pipelineCache(pipeline)

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 0

    where:
    trigger                                              | description
    disabledCDEventsTrigger                               | 'disabled cdevents trigger'
    enabledCDEventsTrigger.withSource('wrongName') | 'different source name'
    enabledCDEventsTrigger.withSource('artifactPackaged').withPayloadConstraints([foo: 'bar']) |
        'unsatisfied payload constraints'
    enabledWebhookTrigger.withSource('artifactPackaged') .withExpectedArtifactIds(['goodId']) |
        'unmatched expected artifact'

    pipeline = createPipelineWith(goodExpectedArtifacts, trigger)
    event = createCDEvent('artifactPackaged')
  }
}
