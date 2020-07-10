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

package com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.model.trigger.PluginEvent
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.util.function.Predicate

class PluginEventHandlerSpec extends Specification {
  def registry = new NoopRegistry()
  def objectMapper = EchoObjectMapper.getInstance()
  def fiatPermissionEvaluator = Mock(FiatPermissionEvaluator)

  @Subject
  def eventHandler = new PluginEventHandler(registry, objectMapper, fiatPermissionEvaluator)

  @Unroll
  def "matchTriggerFor matches on pluginEventType"() {
    given:
    PluginEvent event = new PluginEvent();
    event.details = [:]
    event.details.attributes = [:]

    when:
    Trigger trigger = Trigger.builder().type("plugin").pluginEventType(triggerType).build()
    event.details.attributes.pluginEventType = eventType

    then:
    Predicate<Trigger> predicate = eventHandler.matchTriggerFor(event)
    predicate.test(trigger) == matches

    where:
    eventType                   | triggerType                 | matches
    "RELEASED"                  | "RELEASED"                  | true
    "PREFERRED_VERSION_UPDATED" | "PREFERRED_VERSION_UPDATED" | true
    "PREFERRED_VERSION_UPDATED" | "RELEASED"                  | false
  }
}
