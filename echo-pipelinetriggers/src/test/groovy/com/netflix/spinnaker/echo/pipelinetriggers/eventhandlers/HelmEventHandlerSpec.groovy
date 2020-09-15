/*
 * Copyright 2020 Apple, Inc.
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
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.test.RetrofitStubs
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class HelmEventHandlerSpec extends Specification implements RetrofitStubs {
  def registry = new NoopRegistry()
  def objectMapper = EchoObjectMapper.getInstance()
  def handlerSupport = new EventHandlerSupport()
  def fiatPermissionEvaluator = Mock(FiatPermissionEvaluator)

  @Subject
  def eventHandler = new HelmEventHandler(registry, objectMapper, fiatPermissionEvaluator)

  void setup() {
    fiatPermissionEvaluator.hasPermission(_ as String, _ as String, "APPLICATION", "EXECUTE") >> true
  }

  @Unroll
  def "honors pipeline trigger semver"() {
    given:
    def pipeline = createPipelineWith(trigger)
    def pipelines = handlerSupport.pipelineCache(pipeline)

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines)

    then:
    matchingPipelines.size() == (matches ? 1 : 0)

    where:
    event                    | trigger                                  | matches
    createHelmEvent("1.0.0") | enabledHelmTrigger.withVersion(null)     | true
    createHelmEvent("1.0.0") | enabledHelmTrigger.withVersion("")       | true
    createHelmEvent("1.0.1") | enabledHelmTrigger.withVersion("~1.0.0") | true
    createHelmEvent("1.1.0") | enabledHelmTrigger.withVersion("~1.0.0") | false
    createHelmEvent("1.0.1") | enabledHelmTrigger.withVersion("^1.0.0") | true
    createHelmEvent("1.1.0") | enabledHelmTrigger.withVersion("^1.0.0") | true
    createHelmEvent("1.0.0") | enabledHelmTrigger.withVersion("1.0.0")  | true
    createHelmEvent("1.0.1") | enabledHelmTrigger.withVersion("1.0.0")  | false
  }

  def "an event can trigger multiple pipelines"() {
    given:
    def cache = handlerSupport.pipelineCache(pipelines)

    when:
    def matchingPipelines = eventHandler.getMatchingPipelines(event, cache)

    then:
    matchingPipelines.size() == pipelines.size()

    where:
    event = createHelmEvent("1.0.0")
    pipelines = (1..2).collect {
      Pipeline.builder()
        .application("application")
        .name("pipeline$it")
        .id("id")
        .triggers([enabledHelmTrigger])
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
    trigger                                | description
    disabledHelmTrigger                    | "disabled Helm trigger"
    nonJenkinsTrigger                      | "non-Helm"
    enabledHelmTrigger.withAccount("FAKE") | "wrong account"
    enabledHelmTrigger.withAccount(null)   | "no account"

    pipeline = createPipelineWith(trigger)
    event = createHelmEvent()
  }
}
