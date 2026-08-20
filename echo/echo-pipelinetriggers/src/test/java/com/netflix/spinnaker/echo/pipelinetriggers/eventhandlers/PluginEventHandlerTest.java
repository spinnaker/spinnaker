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

package com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.echo.api.events.Metadata;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.PluginEvent;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PluginEventHandlerTest {
  private final PluginEventHandler eventHandler =
      new PluginEventHandler(
          new NoopRegistry(), EchoObjectMapper.getInstance(), mock(FiatPermissionEvaluator.class));

  @ParameterizedTest
  @CsvSource(
      value = {
        "RELEASED,RELEASED,true",
        "PREFERRED_VERSION_UPDATED,PREFERRED_VERSION_UPDATED,true",
        "PREFERRED_VERSION_UPDATED,RELEASED,false"
      },
      delimiterString = ",")
  void matchTriggerForMatchesOnPluginEventType(
      String eventType, String triggerType, boolean expected) {
    PluginEvent event = new PluginEvent();
    Metadata details = new Metadata();
    details.setAttributes(Map.of("pluginEventType", eventType));
    event.setDetails(details);

    Trigger trigger = Trigger.builder().type("plugin").pluginEventType(triggerType).build();

    Predicate<Trigger> predicate = eventHandler.matchTriggerFor(event);

    assertThat(predicate.test(trigger)).isEqualTo(expected);
  }
}
