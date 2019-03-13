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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.echo.test.RetrofitStubs
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class WebhookEventHandlerSpec extends Specification implements RetrofitStubs {
  def registry = new NoopRegistry()
  def objectMapper = new ObjectMapper()

  @Shared
  def goodExpectedArtifacts = [
      new ExpectedArtifact(
          matchArtifact: new Artifact(
              name: 'myArtifact',
              type: 'artifactType',
          ),
          id: 'goodId'
      )
  ]

  @Subject
  def eventHandler = new WebhookEventHandler(registry, objectMapper)

  def 'triggers pipelines for successful builds for webhook'() {
    given:
    def pipeline = createPipelineWith(goodExpectedArtifacts, trigger)
    def pipelines = [pipeline]

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 1
    matchingPipelines[0].application == pipeline.application
    matchingPipelines[0].name == pipeline.name

    where:
    event = createWebhookEvent('myCIServer',
        [foo: 'bar', artifacts: [[name: 'myArtifact', type: 'artifactType']]])
    trigger = enabledWebhookTrigger
        .withSource('myCIServer')
        .withPayloadConstraints([foo: 'bar'])
        .withExpectedArtifactIds(['goodId'])
  }

  def 'attaches webhook trigger to the pipeline'() {
    given:
    def pipelines = [pipeline]

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 1
    matchingPipelines[0].trigger.type == enabledWebhookTrigger.type

    where:
    event = createWebhookEvent('myCIServer')
    pipeline = createPipelineWith([],
        enabledWebhookTrigger.withSource('myCIServer'),
        disabledWebhookTrigger)
  }

  @Unroll
  def "does not trigger #description pipelines for webhook"() {
    given:
    def pipelines = [pipeline]

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == 0

    where:
    trigger                                              | description
    disabledWebhookTrigger                               | 'disabled webhook trigger'
    enabledWebhookTrigger.withSource('wrongName') | 'different source name'
    enabledWebhookTrigger.withSource('myCIServer').withPayloadConstraints([foo: 'bar']) |
        'unsatisfied payload constraints'
    enabledWebhookTrigger.withSource('myCIServer') .withExpectedArtifactIds(['goodId']) |
        'unmatched expected artifact'

    pipeline = createPipelineWith(goodExpectedArtifacts, trigger)
    event = createWebhookEvent('myCIServer')
  }
}
