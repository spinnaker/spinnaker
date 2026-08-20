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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers.BaseTriggerEventHandler;
import com.netflix.spinnaker.echo.pipelinetriggers.orca.OrcaService;
import com.netflix.spinnaker.echo.services.Front50Service;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.mock.Calls;

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

  // --- Direct unit tests exercising a hand-constructed PipelineCache instance ---

  private static final String URL = "http://echo";
  private static final String SUPPORTED_TRIGGER = "arbitrary";
  private static final String SUPPORTED_TRIGGERS = SUPPORTED_TRIGGER + ",cron";
  private static final int INTERVAL = 30;
  private static final int SLEEP_MS = 100;

  private Front50Service front50;
  private OrcaService orca;
  private NoopRegistry registry;
  private ObjectMapper objectMapper;
  private PipelineCacheConfigurationProperties pipelineCacheConfigurationProperties;
  private PipelineCache pipelineCache;

  @BeforeEach
  void setUpDirectUnitTests() {
    front50 = mock(Front50Service.class);
    orca = mock(OrcaService.class);
    registry = new NoopRegistry();
    objectMapper = EchoObjectMapper.getInstance();
    pipelineCacheConfigurationProperties = new PipelineCacheConfigurationProperties();

    // To verify that PipelineCache passes the expected supportedTriggers to front50's
    // getPipelines endpoint.
    BaseTriggerEventHandler baseTriggerEventHandler = mock(BaseTriggerEventHandler.class);
    when(baseTriggerEventHandler.supportedTriggerTypes()).thenReturn(List.of(SUPPORTED_TRIGGER));
    List<BaseTriggerEventHandler> triggerHandlers = List.of(baseTriggerEventHandler);

    pipelineCache =
        new PipelineCache(
            mock(ScheduledExecutorService.class),
            INTERVAL,
            SLEEP_MS,
            pipelineCacheConfigurationProperties,
            objectMapper,
            front50,
            orca,
            registry,
            triggerHandlers);
  }

  private static SpinnakerHttpException unavailable() {
    return new SpinnakerHttpException(
        Response.error(
            HttpURLConnection.HTTP_UNAVAILABLE,
            ResponseBody.create(
                "{ \"message\": \"arbitrary message\" }", MediaType.get("application/json"))),
        new Retrofit.Builder()
            .baseUrl(URL)
            .addConverterFactory(JacksonConverterFactory.create())
            .build());
  }

  @Test
  void keepsPollingIfFront50ReturnsAnError() {
    Map<String, Object> pipelineMap =
        Map.of("application", "application", "name", "Pipeline", "id", "P1");
    Pipeline pipeline =
        Pipeline.builder().application("application").name("Pipeline").id("P1").build();

    when(front50.getPipelines())
        .thenReturn(Calls.response(List.of()))
        .thenThrow(unavailable())
        .thenReturn(Calls.response(List.of(pipelineMap)));
    pipelineCache.start();

    // null pipelines when we have not polled yet
    assertThat(pipelineCache.getPipelines()).isNull();

    // we complete our first polling cycle: we reflect the initial value
    pipelineCache.pollPipelineConfigs();
    assertThat(pipelineCache.getPipelines()).isEmpty();

    // a polling cycle encounters an error: we still return the cached value
    pipelineCache.pollPipelineConfigs();
    assertThat(pipelineCache.getPipelines()).isEmpty();

    // we recover after a failed poll: we return the updated value
    pipelineCache.pollPipelineConfigs();
    assertThat(pipelineCache.getPipelines()).containsExactly(pipeline);
  }

  @ParameterizedTest(name = "filters front50 pipelines when configured to do so ({0})")
  @ValueSource(booleans = {true, false})
  void filtersFront50PipelinesWhenConfiguredToDoSo(boolean filterFront50Pipelines) {
    pipelineCacheConfigurationProperties.setFilterFront50Pipelines(filterFront50Pipelines);

    if (filterFront50Pipelines) {
      when(front50.getPipelines(true, true, SUPPORTED_TRIGGERS))
          .thenReturn(Calls.response(List.of()));
    } else {
      when(front50.getPipelines()).thenReturn(Calls.response(List.of()));
    }

    pipelineCache.start();
    pipelineCache.pollPipelineConfigs();

    if (filterFront50Pipelines) {
      verify(front50, times(1)).getPipelines(true, true, SUPPORTED_TRIGGERS);
      verify(front50, never()).getPipelines();
    } else {
      verify(front50, times(1)).getPipelines();
      verify(front50, never()).getPipelines(any(), any(), any());
    }
  }

  @Test
  void getPipelineByIdCallsFront50sGetPipelineEndpoint() {
    String pipelineId = "my-pipeline-id";
    String application = "application";
    String pipelineName = "my-pipeline-name";
    Map<String, Object> pipelineMap =
        Map.of("application", application, "name", pipelineName, "id", pipelineId);
    Pipeline pipeline =
        Pipeline.builder().application(application).name(pipelineName).id(pipelineId).build();

    when(front50.getPipeline(pipelineId)).thenReturn(Calls.response(pipelineMap));

    Optional<Pipeline> result = pipelineCache.getPipelineById(pipelineId);

    verify(front50, times(1)).getPipeline(pipelineId);
    assertThat(result).contains(pipeline);
  }

  @Test
  void getPipelineByNameCallsFront50sGetPipelineByNameEndpoint() {
    String pipelineId = "my-pipeline-id";
    String application = "application";
    String pipelineName = "my-pipeline-name";
    Map<String, Object> pipelineMap =
        Map.of("application", application, "name", pipelineName, "id", pipelineId);
    Pipeline pipeline =
        Pipeline.builder().application(application).name(pipelineName).id(pipelineId).build();

    when(front50.getPipelineByName(application, pipelineName))
        .thenReturn(Calls.response(pipelineMap));

    Optional<Pipeline> result = pipelineCache.getPipelineByName(application, pipelineName);

    verify(front50, times(1)).getPipelineByName(application, pipelineName);
    assertThat(result).contains(pipeline);
  }

  @Test
  void weCanSerializePipelinesWithTriggersThatHaveAParent() {
    Trigger trigger = Trigger.builder().id("123-456").build();
    Pipeline pipeline =
        Pipeline.builder()
            .application("app")
            .name("pipe")
            .id("idPipe")
            .triggers(List.of(trigger))
            .build();
    Pipeline decorated = PipelineCache.decorateTriggers(List.of(pipeline)).get(0);

    assertThat(decorated.getTriggers().get(0).getParent()).isEqualTo(decorated);

    assertThatCode(() -> EchoObjectMapper.getInstance().writeValueAsString(decorated))
        .doesNotThrowAnyException();
  }

  @Test
  void canHandlePipelinesWithoutTriggers() {
    Pipeline pipeline =
        Pipeline.builder().application("app").name("pipe").id("idPipe").triggers(List.of()).build();
    Pipeline decorated = PipelineCache.decorateTriggers(List.of(pipeline)).get(0);

    assertThat(decorated.getTriggers()).isEmpty();

    assertThatCode(() -> EchoObjectMapper.getInstance().writeValueAsString(decorated))
        .doesNotThrowAnyException();
  }

  @Test
  void disabledTriggersAndTriggersForDisabledPipelinesDoNotAppearInTriggerIndex()
      throws TimeoutException {
    Map<String, Object> enabledTriggerMap = Map.of("type", "git", "enabled", true);
    Map<String, Object> disabledTriggerMap = Map.of("type", "jenkins", "enabled", false);

    Map<String, Object> enabledPipelineMap =
        Map.of(
            "application", "app",
            "name", "pipe",
            "id", "enabledPipeId",
            "disabled", false,
            "triggers", List.of(enabledTriggerMap, disabledTriggerMap));
    Map<String, Object> disabledPipelineMap =
        Map.of(
            "application", "app",
            "name", "pipe",
            "id", "disabledPipeId",
            "disabled", true,
            "triggers", List.of(enabledTriggerMap));

    when(front50.getPipelines())
        .thenReturn(Calls.response(List.of(enabledPipelineMap, disabledPipelineMap)));

    pipelineCache.start();
    pipelineCache.pollPipelineConfigs();

    Map<String, List<Trigger>> triggers = pipelineCache.getEnabledTriggersSync();

    // we only get the enabled trigger for the enabled pipeline
    assertThat(triggers).hasSize(1);
    assertThat(triggers.get("git")).hasSize(1);
    assertThat(triggers.get("git").get(0).getParent().getId()).isEqualTo("enabledPipeId");
  }

  @Test
  void triggerIndexingSupportsPipelinesWithNullTriggers() throws TimeoutException {
    Map<String, Object> pipelineMap = Map.of("application", "app", "name", "pipe", "id", "idPipe");

    when(front50.getPipelines()).thenReturn(Calls.response(List.of(pipelineMap)));

    pipelineCache.start();
    pipelineCache.pollPipelineConfigs();

    assertThat(pipelineCache.getEnabledTriggersSync()).isEmpty();
  }

  @Test
  void triggerIndexingSupportsTriggersWithNullType() throws TimeoutException {
    Map<String, Object> triggerMap = new java.util.HashMap<>();
    triggerMap.put("type", null);
    triggerMap.put("enabled", true);
    Map<String, Object> pipelineMap =
        Map.of(
            "application", "app",
            "name", "pipe",
            "id", "idPipe",
            "triggers", List.of(triggerMap));

    when(front50.getPipelines()).thenReturn(Calls.response(List.of(pipelineMap)));

    pipelineCache.start();
    pipelineCache.pollPipelineConfigs();

    assertThat(pipelineCache.getEnabledTriggersSync()).isEmpty();
  }
}
