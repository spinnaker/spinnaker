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
import com.netflix.spinnaker.echo.model.trigger.CDEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CDEventsWebhookHandlerTest {

  private static final Trigger enabledCDEventsTrigger =
      Trigger.builder().enabled(true).type("cdevents").build();
  private static final Trigger disabledCDEventsTrigger =
      Trigger.builder().enabled(false).type("cdevents").build();
  private static final Trigger enabledWebhookTrigger =
      Trigger.builder().enabled(true).type("webhook").build();

  private static final List<ExpectedArtifact> goodExpectedArtifacts =
      List.of(
          ExpectedArtifact.builder()
              .matchArtifact(Artifact.builder().name("myArtifact").type("artifactType").build())
              .id("goodId")
              .build());

  private final NoopRegistry registry = new NoopRegistry();
  private final ObjectMapper objectMapper = EchoObjectMapper.getInstance();
  private final TestEventHandlerSupport handlerSupport = new TestEventHandlerSupport();
  private FiatPermissionEvaluator fiatPermissionEvaluator;
  private CDEventsWebhookHandler eventHandler;

  @BeforeEach
  void setUp() {
    fiatPermissionEvaluator = mock(FiatPermissionEvaluator.class);
    when(fiatPermissionEvaluator.hasPermission(
            any(String.class), any(String.class), eq("APPLICATION"), eq("EXECUTE")))
        .thenReturn(true);
    eventHandler = new CDEventsWebhookHandler(registry, objectMapper, fiatPermissionEvaluator);
  }

  private static Pipeline createPipelineWith(
      List<ExpectedArtifact> expectedArtifacts, Trigger... triggers) {
    return Pipeline.builder()
        .application("application")
        .name("name")
        .id("1")
        .triggers(new ArrayList<>(List.of(triggers)))
        .expectedArtifacts(expectedArtifacts)
        .build();
  }

  private static CDEvent createCDEvent(String source) {
    return createCDEvent(source, Map.of());
  }

  private static CDEvent createCDEvent(String source, Map<String, Object> payload) {
    CDEvent res = new CDEvent();
    Metadata details = new Metadata();
    details.setType(CDEvent.TYPE);
    details.setSource(source);
    res.setDetails(details);
    res.setPayload(payload);
    res.setContent(
        EchoObjectMapper.getInstance().convertValue(payload, WebhookContent.Content.class));
    return res;
  }

  private static CDEvent createCDEventRequestHeaders(
      String source, Map<String, Object> payload, TreeMap<String, List<String>> requestHeaders) {
    CDEvent res = new CDEvent();
    Metadata details = new Metadata();
    details.setType(CDEvent.TYPE);
    details.setSource(source);
    details.setRequestHeaders(requestHeaders);
    res.setDetails(details);
    res.setPayload(payload);
    res.setContent(
        EchoObjectMapper.getInstance().convertValue(payload, WebhookContent.Content.class));
    return res;
  }

  @Test
  void triggersPipelinesForSuccessfulBuildsForCDEvent() throws TimeoutException {
    Trigger trigger =
        enabledCDEventsTrigger
            .withSource("pipelineRunFinished")
            .withPayloadConstraints(Map.of("foo", "bar"))
            .withExpectedArtifactIds(List.of("goodId"));
    CDEvent event =
        createCDEvent(
            "pipelineRunFinished",
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
  void attachesCdeventsTriggerToThePipeline() throws TimeoutException {
    CDEvent event = createCDEvent("pipelineRunStarted");
    Pipeline pipeline =
        createPipelineWith(
            List.of(),
            enabledCDEventsTrigger.withSource("pipelineRunStarted"),
            disabledCDEventsTrigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).hasSize(1);
    assertThat(matchingPipelines.get(0).getTrigger().getType())
        .isEqualTo(enabledCDEventsTrigger.getType());
  }

  @Test
  void triggersPipelineOnMatchingAttributeConstraints() throws TimeoutException {
    Trigger trigger =
        enabledCDEventsTrigger
            .withSource("artifactPublished")
            .withAttributeConstraints(Map.of("ce-type", "dev.cdevents.artifactPublished"))
            .withPayloadConstraints(Map.of("foo", "bar"))
            .withExpectedArtifactIds(List.of("goodId"));
    Pipeline pipeline = createPipelineWith(goodExpectedArtifacts, trigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);

    TreeMap<String, List<String>> requestHeaders = new TreeMap<>();
    requestHeaders.put("ce-type", List.of("dev.cdevents.artifactPublished"));
    CDEvent event =
        createCDEventRequestHeaders(
            "artifactPublished",
            Map.of(
                "foo",
                "bar",
                "artifacts",
                List.of(Map.of("name", "myArtifact", "type", "artifactType"))),
            requestHeaders);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).hasSize(1);
    assertThat(matchingPipelines.get(0).getApplication()).isEqualTo(pipeline.getApplication());
    assertThat(matchingPipelines.get(0).getName()).isEqualTo(pipeline.getName());
  }

  private static Stream<Arguments> doesNotTriggerParams() {
    return Stream.of(
        Arguments.of(disabledCDEventsTrigger, "disabled cdevents trigger"),
        Arguments.of(enabledCDEventsTrigger.withSource("wrongName"), "different source name"),
        Arguments.of(
            enabledCDEventsTrigger
                .withSource("artifactPackaged")
                .withPayloadConstraints(Map.of("foo", "bar")),
            "unsatisfied payload constraints"),
        Arguments.of(
            enabledWebhookTrigger
                .withSource("artifactPackaged")
                .withExpectedArtifactIds(List.of("goodId")),
            "unmatched expected artifact"));
  }

  @ParameterizedTest(name = "does not trigger {1} pipelines for CDEvent")
  @MethodSource("doesNotTriggerParams")
  void doesNotTriggerPipelinesForCDEvent(Trigger trigger, String description)
      throws TimeoutException {
    Pipeline pipeline = createPipelineWith(goodExpectedArtifacts, trigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);
    CDEvent event = createCDEvent("artifactPackaged");

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).isEmpty();
  }
}
