/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.pipelinetriggers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.echo.model.Event
import com.netflix.spinnaker.echo.model.pubsub.PubsubSystem
import com.netflix.spinnaker.echo.pipelinetriggers.monitor.PubsubEventMonitor
import com.netflix.spinnaker.echo.test.RetrofitStubs
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact
import rx.functions.Action1
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class PubsubEventMonitorSpec extends Specification implements RetrofitStubs {
  def objectMapper = new ObjectMapper()
  def pipelineCache = Mock(PipelineCache)
  def subscriber = Mock(Action1)
  def registry = Stub(Registry) {
    createId(*_) >> Stub(Id)
    counter(*_) >> Stub(Counter)
    gauge(*_) >> Integer.valueOf(1)
  }
  @Shared def goodArtifacts = [new Artifact(name: 'myArtifact', type: 'artifactType')]
  @Shared def badExpectedArtifacts = [
    new ExpectedArtifact(
      matchArtifact: new Artifact(
        name: 'myBadArtifact',
        type: 'artifactType',
      ),
      id: 'badId'
    )
  ]

  @Shared def goodExpectedArtifacts = [
      new ExpectedArtifact(
        matchArtifact: new Artifact(
          name: 'myArtifact',
          type: 'artifactType',
        ),
        id: 'goodId'
      )
  ]

  @Shared def goodRegexExpectedArtifacts = [
      new ExpectedArtifact(
        matchArtifact: new Artifact(
          name: 'myArtifact',
          type: 'artifact.*',
        ),
        id: 'goodId'
      )
  ]

  @Subject
  def monitor = new PubsubEventMonitor(pipelineCache, subscriber, registry)

  @Unroll
  def "triggers pipelines for successful builds for #triggerType"() {
    given:
    def pipeline = createPipelineWith(goodExpectedArtifacts, trigger)
    pipelineCache.getPipelines() >> [pipeline]

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    1 * subscriber.call({
      it.application == pipeline.application && it.name == pipeline.name
    })

    where:
    event                                                                                                | trigger
    createPubsubEvent(PubsubSystem.GOOGLE, "projects/project/subscriptions/subscription", null)          | enabledGooglePubsubTrigger
    createPubsubEvent(PubsubSystem.GOOGLE, "projects/project/subscriptions/subscription", [])            | enabledGooglePubsubTrigger
    createPubsubEvent(PubsubSystem.GOOGLE, "projects/project/subscriptions/subscription", goodArtifacts) | enabledGooglePubsubTrigger.withExpectedArtifactIds(goodExpectedArtifacts*.id)
    createPubsubEvent(PubsubSystem.GOOGLE, "projects/project/subscriptions/subscription", goodArtifacts) | enabledGooglePubsubTrigger.withExpectedArtifactIds(goodRegexExpectedArtifacts*.id)
    createPubsubEvent(PubsubSystem.GOOGLE, "projects/project/subscriptions/subscription", goodArtifacts) | enabledGooglePubsubTrigger // Trigger doesn't care about artifacts.
    // TODO(jacobkiefer): Add Kafka cases when that is implemented.
  }

  @Unroll
  def "does not trigger #description pipelines"() {
    given:
    pipelineCache.getPipelines() >> [pipeline]

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    0 * subscriber._

    where:
    trigger                     | description
    disabledGooglePubsubTrigger | "disabled Google pubsub trigger"

    pipeline = createPipelineWith(goodExpectedArtifacts, trigger)
    event = createPubsubEvent(PubsubSystem.GOOGLE, "projects/project/subscriptions/subscription", [])
    // TODO(jacobkiefer): Add Kafka cases when that is implemented.
  }

  def "attaches Google pubsub trigger to the pipeline"() {
    given:
    pipelineCache.getPipelines() >> [pipeline]

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    1 * subscriber.call({
      it.trigger.type == enabledGooglePubsubTrigger.type
      it.trigger.pubsubSystem == enabledGooglePubsubTrigger.pubsubSystem
      it.trigger.subscriptionName == enabledGooglePubsubTrigger.subscriptionName
    })

    where:
    event = createPubsubEvent(PubsubSystem.GOOGLE, "projects/project/subscriptions/subscription", [])
    pipeline = createPipelineWith(goodExpectedArtifacts, enabledGooglePubsubTrigger, disabledGooglePubsubTrigger)
  }

  @Unroll
  def "does not trigger #description pipelines for Google pubsub"() {
    given:
    pipelineCache.getPipelines() >> [pipeline]

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    0 * subscriber._

    where:
    trigger                                                        | description
    disabledGooglePubsubTrigger                                    | "disabled Google pubsub trigger"
    enabledGooglePubsubTrigger.withSubscriptionName("wrongName")   | "different subscription name"
    enabledGooglePubsubTrigger.withPubsubSystem("noogle")            | "different subscription name"

    pipeline = createPipelineWith(goodExpectedArtifacts, trigger)
    event = createPubsubEvent(PubsubSystem.GOOGLE, "projects/project/subscriptions/subscription", [])
  }

  @Unroll
  def "does not trigger #description pipelines containing artifacts for Google pubsub"() {
    given:
    pipelineCache.getPipelines() >> [pipeline]

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    0 * subscriber._

    where:
    trigger                                                                    | description
    enabledGooglePubsubTrigger.withExpectedArtifactIds(badExpectedArtifacts*.id) | "non-matching artifact in message"

    pipeline = createPipelineWith(goodExpectedArtifacts, trigger)
    event = createPubsubEvent(PubsubSystem.GOOGLE, "projects/project/subscriptions/subscription", goodArtifacts)
  }

  @Unroll
  def "does not trigger a pipeline that has an enabled pubsub trigger with missing #field"() {
    given:
    pipelineCache.getPipelines() >> [badPipeline, goodPipeline]

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    1 * subscriber.call({ it.id == goodPipeline.id })

    where:
    trigger                                               | field
    enabledGooglePubsubTrigger.withSubscriptionName(null) | "subscriptionName"
    enabledGooglePubsubTrigger.withPubsubSystem(null)     | "pubsubSystem"

    event = createPubsubEvent(PubsubSystem.GOOGLE, "projects/project/subscriptions/subscription", [])
    goodPipeline = createPipelineWith(goodExpectedArtifacts, enabledGooglePubsubTrigger)
    badPipeline = createPipelineWith(goodExpectedArtifacts, trigger)
  }
}
