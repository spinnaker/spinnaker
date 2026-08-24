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

package com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.echo.api.events.Metadata;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.WebhookContent;
import com.netflix.spinnaker.echo.model.trigger.WebhookEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WebhookEventHandlerTest {

  private static final Trigger enabledWebhookTrigger =
      Trigger.builder().enabled(true).type("webhook").build();
  private static final Trigger disabledWebhookTrigger =
      Trigger.builder().enabled(false).type("webhook").build();

  private static final List<ExpectedArtifact> goodExpectedArtifacts =
      List.of(
          ExpectedArtifact.builder()
              .matchArtifact(Artifact.builder().name("myArtifact").type("artifactType").build())
              .id("goodId")
              .build());

  private final NoopRegistry registry = new NoopRegistry();
  private final ObjectMapper objectMapper = EchoObjectMapper.getInstance();
  private final TestEventHandlerSupport handlerSupport = new TestEventHandlerSupport();
  private final AtomicInteger nextId = new AtomicInteger(1);
  private FiatPermissionEvaluator fiatPermissionEvaluator;
  private WebhookEventHandler eventHandler;

  @BeforeEach
  void setUp() {
    fiatPermissionEvaluator = mock(FiatPermissionEvaluator.class);
    when(fiatPermissionEvaluator.hasPermission(
            any(String.class), any(String.class), eq("APPLICATION"), eq("EXECUTE")))
        .thenReturn(true);
    eventHandler = new WebhookEventHandler(registry, objectMapper, fiatPermissionEvaluator);
  }

  private Pipeline createPipelineWith(
      List<ExpectedArtifact> expectedArtifacts, Trigger... triggers) {
    return Pipeline.builder()
        .application("application")
        .name("name")
        .id(String.valueOf(nextId.getAndIncrement()))
        .triggers(List.of(triggers))
        .expectedArtifacts(expectedArtifacts)
        .build();
  }

  private static WebhookEvent createWebhookEvent(String source) {
    return createWebhookEvent(source, Map.of());
  }

  private static WebhookEvent createWebhookEvent(String source, Map<String, Object> payload) {
    WebhookEvent res = new WebhookEvent();
    Metadata details = new Metadata();
    details.setType(WebhookEvent.TYPE);
    details.setSource(source);
    res.setDetails(details);
    res.setPayload(payload);
    res.setContent(
        EchoObjectMapper.getInstance().convertValue(payload, WebhookContent.Content.class));
    return res;
  }

  @Test
  void triggersPipelinesForSuccessfulBuildsForWebhook() throws TimeoutException {
    Trigger trigger =
        enabledWebhookTrigger
            .withSource("myCIServer")
            .withPayloadConstraints(Map.of("foo", "bar"))
            .withExpectedArtifactIds(List.of("goodId"));
    WebhookEvent event =
        createWebhookEvent(
            "myCIServer",
            Map.of(
                "foo",
                "bar",
                "artifacts",
                List.of(Map.of("name", "myArtifact", "type", "artifactType"))));

    Pipeline pipeline = createPipelineWith(goodExpectedArtifacts, trigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).hasSize(1);
    assertThat(matchingPipelines.get(0).getApplication()).isEqualTo(pipeline.getApplication());
    assertThat(matchingPipelines.get(0).getName()).isEqualTo(pipeline.getName());
  }

  @Test
  void attachesWebhookTriggerToThePipeline() throws TimeoutException {
    WebhookEvent event = createWebhookEvent("myCIServer");
    Pipeline pipeline =
        createPipelineWith(
            List.of(), enabledWebhookTrigger.withSource("myCIServer"), disabledWebhookTrigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).hasSize(1);
    assertThat(matchingPipelines.get(0).getTrigger().getType())
        .isEqualTo(enabledWebhookTrigger.getType());
  }

  private static Stream<Arguments> doesNotTriggerParams() {
    return Stream.of(
        Arguments.of(disabledWebhookTrigger, "disabled webhook trigger"),
        Arguments.of(enabledWebhookTrigger.withSource("wrongName"), "different source name"),
        Arguments.of(
            enabledWebhookTrigger
                .withSource("myCIServer")
                .withPayloadConstraints(Map.of("foo", "bar")),
            "unsatisfied payload constraints"),
        Arguments.of(
            enabledWebhookTrigger
                .withSource("myCIServer")
                .withExpectedArtifactIds(List.of("goodId")),
            "unmatched expected artifact"));
  }

  @ParameterizedTest(name = "does not trigger {1} pipelines for webhook")
  @MethodSource("doesNotTriggerParams")
  void doesNotTriggerPipelinesForWebhook(Trigger trigger, String description)
      throws TimeoutException {
    Pipeline pipeline = createPipelineWith(goodExpectedArtifacts, trigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);
    WebhookEvent event = createWebhookEvent("myCIServer");

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).isEmpty();
  }
}
