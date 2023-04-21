/*
 * Copyright 2023 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.echo.pipelinetriggers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers.BaseTriggerEventHandler;
import com.netflix.spinnaker.echo.pipelinetriggers.orca.OrcaService;
import com.netflix.spinnaker.echo.services.Front50Service;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

public class PipelineCacheTest {
  // minimal set of beans necessary to initialize PipelineCache, with additional
  // BaseTriggerEventHandlers to verify behavior.
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(PipelineCache.class)
          .withBean(PipelineCacheConfigurationProperties.class)
          .withBean(ObjectMapper.class)
          .withBean(NoopRegistry.class)
          .withConfiguration(UserConfigurations.of(ConfigWithTriggerEventHandlers.class));

  @Test
  void testPipelineCacheSupportedTriggerTypes() {
    runner.run(
        ctx -> {
          PipelineCache pipelineCache = ctx.getBean(PipelineCache.class);
          assertThat(pipelineCache.getSupportedTriggerTypes())
              .isEqualTo(Trigger.Type.CRON.toString() + ",trigger-type-one,trigger-type-two");
        });
  }

  /**
   * To exercise the logic in PipelineCache that determines supported trigger types from the
   * available BeanTriggerEventHandler beans.
   */
  @TestConfiguration
  static class ConfigWithTriggerEventHandlers {
    @Bean
    Front50Service front50Service() {
      return mock(Front50Service.class);
    }

    @Bean
    OrcaService orcaService() {
      return mock(OrcaService.class);
    }

    @Bean
    BaseTriggerEventHandler triggerEventHandlerOne() {
      BaseTriggerEventHandler triggerEventHandler = mock(BaseTriggerEventHandler.class);
      when(triggerEventHandler.supportedTriggerTypes()).thenReturn(List.of("trigger-type-one"));
      return triggerEventHandler;
    }

    @Bean
    BaseTriggerEventHandler triggerEventHandlerTwo() {
      BaseTriggerEventHandler triggerEventHandler = mock(BaseTriggerEventHandler.class);
      when(triggerEventHandler.supportedTriggerTypes()).thenReturn(List.of("trigger-type-two"));
      return triggerEventHandler;
    }

    /** To verify that duplicates don't make it to the resulting comma-separated string */
    @Bean
    BaseTriggerEventHandler anotherTriggerEventHandlerTwo() {
      BaseTriggerEventHandler triggerEventHandler = mock(BaseTriggerEventHandler.class);
      when(triggerEventHandler.supportedTriggerTypes()).thenReturn(List.of("trigger-type-two"));
      return triggerEventHandler;
    }

    /**
     * To verify that cron only makes it once to the resulting comma-separated string, if there's
     * ever an event handler for it.
     */
    @Bean
    BaseTriggerEventHandler cronTriggerEventHandler() {
      BaseTriggerEventHandler triggerEventHandler = mock(BaseTriggerEventHandler.class);
      when(triggerEventHandler.supportedTriggerTypes())
          .thenReturn(List.of(Trigger.Type.CRON.toString()));
      return triggerEventHandler;
    }
  }
}
