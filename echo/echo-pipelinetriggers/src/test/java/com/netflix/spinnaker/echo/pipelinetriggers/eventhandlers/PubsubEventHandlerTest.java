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
import com.netflix.spinnaker.echo.model.pubsub.MessageDescription;
import com.netflix.spinnaker.echo.model.pubsub.PubsubSystem;
import com.netflix.spinnaker.echo.model.trigger.PubsubEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PubsubEventHandlerTest {

  private static final Artifact goodArtifact =
      Artifact.builder().name("myArtifact").type("artifactType").build();
  private static final Artifact badArtifact =
      Artifact.builder().name("myBadArtifact").type("artifactType").build();

  private static final List<Artifact> goodArtifacts = List.of(goodArtifact);

  private static final List<ExpectedArtifact> badExpectedArtifacts =
      List.of(ExpectedArtifact.builder().matchArtifact(badArtifact).id("badId").build());

  private static final List<ExpectedArtifact> goodExpectedArtifacts =
      List.of(ExpectedArtifact.builder().matchArtifact(goodArtifact).id("goodId").build());

  private static final List<ExpectedArtifact> goodRegexExpectedArtifacts =
      List.of(
          ExpectedArtifact.builder()
              .matchArtifact(Artifact.builder().name("myArtifact").type("artifact.*").build())
              .id("goodId")
              .build());

  private static final Trigger enabledGooglePubsubTrigger =
      Trigger.builder()
          .enabled(true)
          .type("pubsub")
          .pubsubSystem("google")
          .subscriptionName("projects/project/subscriptions/subscription")
          .expectedArtifactIds(List.of())
          .build();
  private static final Trigger disabledGooglePubsubTrigger =
      Trigger.builder()
          .enabled(false)
          .type("pubsub")
          .pubsubSystem("google")
          .subscriptionName("projects/project/subscriptions/subscription")
          .expectedArtifactIds(List.of())
          .build();

  private final NoopRegistry registry = new NoopRegistry();
  private final ObjectMapper objectMapper = EchoObjectMapper.getInstance();
  private final TestEventHandlerSupport handlerSupport = new TestEventHandlerSupport();
  private final AtomicInteger nextId = new AtomicInteger(1);
  private FiatPermissionEvaluator fiatPermissionEvaluator;
  private PubsubEventHandler eventHandler;

  @BeforeEach
  void setUp() {
    fiatPermissionEvaluator = mock(FiatPermissionEvaluator.class);
    when(fiatPermissionEvaluator.hasPermission(
            any(String.class), any(String.class), eq("APPLICATION"), eq("EXECUTE")))
        .thenReturn(true);
    eventHandler = new PubsubEventHandler(registry, objectMapper, fiatPermissionEvaluator);
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

  private static PubsubEvent createPubsubEvent(
      PubsubSystem pubsubSystem,
      String subscriptionName,
      List<Artifact> artifacts,
      Map<String, Object> payload) {
    PubsubEvent res = new PubsubEvent();

    MessageDescription description =
        MessageDescription.builder()
            .pubsubSystem(pubsubSystem)
            .ackDeadlineSeconds(1)
            .subscriptionName(subscriptionName)
            .artifacts(artifacts)
            .build();

    PubsubEvent.Content content = new PubsubEvent.Content();
    content.setMessageDescription(description);

    Metadata details = new Metadata();
    details.setType(PubsubEventHandler.PUBSUB_TRIGGER_TYPE);
    res.setDetails(details);
    res.setContent(content);
    res.setPayload(payload);
    return res;
  }

  private static List<String> idsOf(List<ExpectedArtifact> expectedArtifacts) {
    return expectedArtifacts.stream().map(ExpectedArtifact::getId).toList();
  }

  private static Stream<Arguments> triggersForGooglePubsubParams() {
    return Stream.of(
        Arguments.of(
            createPubsubEvent(
                PubsubSystem.GOOGLE, "projects/project/subscriptions/subscription", null, Map.of()),
            enabledGooglePubsubTrigger),
        Arguments.of(
            createPubsubEvent(
                PubsubSystem.GOOGLE,
                "projects/project/subscriptions/subscription",
                List.of(),
                Map.of()),
            enabledGooglePubsubTrigger),
        Arguments.of(
            createPubsubEvent(
                PubsubSystem.GOOGLE,
                "projects/project/subscriptions/subscription",
                goodArtifacts,
                Map.of()),
            enabledGooglePubsubTrigger.withExpectedArtifactIds(idsOf(goodExpectedArtifacts))),
        Arguments.of(
            createPubsubEvent(
                PubsubSystem.GOOGLE,
                "projects/project/subscriptions/subscription",
                goodArtifacts,
                Map.of()),
            enabledGooglePubsubTrigger.withExpectedArtifactIds(idsOf(goodRegexExpectedArtifacts))),
        Arguments.of(
            createPubsubEvent(
                PubsubSystem.GOOGLE,
                "projects/project/subscriptions/subscription",
                goodArtifacts,
                Map.of()),
            enabledGooglePubsubTrigger) // Trigger doesn't care about artifacts.
        );
  }

  @ParameterizedTest
  @MethodSource("triggersForGooglePubsubParams")
  void triggersPipelinesForSuccessfulBuildsForGooglePubsub(PubsubEvent event, Trigger trigger)
      throws TimeoutException {
    Pipeline pipeline = createPipelineWith(goodExpectedArtifacts, trigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).hasSize(1);
    assertThat(matchingPipelines.get(0).getApplication()).isEqualTo(pipeline.getApplication());
    assertThat(matchingPipelines.get(0).getName()).isEqualTo(pipeline.getName());
  }

  @Test
  void attachesGooglePubsubTriggerToThePipeline() throws TimeoutException {
    PubsubEvent event =
        createPubsubEvent(
            PubsubSystem.GOOGLE,
            "projects/project/subscriptions/subscription",
            List.of(),
            Map.of());
    Pipeline pipeline =
        createPipelineWith(
            goodExpectedArtifacts, enabledGooglePubsubTrigger, disabledGooglePubsubTrigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).hasSize(1);
    assertThat(matchingPipelines.get(0).getTrigger().getType())
        .isEqualTo(enabledGooglePubsubTrigger.getType());
    assertThat(matchingPipelines.get(0).getTrigger().getPubsubSystem())
        .isEqualTo(enabledGooglePubsubTrigger.getPubsubSystem());
    assertThat(matchingPipelines.get(0).getTrigger().getSubscriptionName())
        .isEqualTo(enabledGooglePubsubTrigger.getSubscriptionName());
  }

  private static Stream<Arguments> doesNotTriggerParams() {
    return Stream.of(
        Arguments.of(disabledGooglePubsubTrigger, "disabled Google pubsub trigger"),
        Arguments.of(
            enabledGooglePubsubTrigger.withSubscriptionName("wrongName"),
            "different subscription name"),
        Arguments.of(
            enabledGooglePubsubTrigger.withPubsubSystem("noogle"), "different subscription name"));
  }

  @ParameterizedTest(name = "does not trigger {1} pipelines for Google pubsub")
  @MethodSource("doesNotTriggerParams")
  void doesNotTriggerPipelinesForGooglePubsub(Trigger trigger, String description)
      throws TimeoutException {
    Pipeline pipeline = createPipelineWith(goodExpectedArtifacts, trigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);
    PubsubEvent event =
        createPubsubEvent(
            PubsubSystem.GOOGLE,
            "projects/project/subscriptions/subscription",
            List.of(),
            Map.of());

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).isEmpty();
  }

  @Test
  void doesNotTriggerPipelinesContainingArtifactsForGooglePubsub() throws TimeoutException {
    Trigger trigger =
        enabledGooglePubsubTrigger.withExpectedArtifactIds(idsOf(badExpectedArtifacts));
    Pipeline pipeline = createPipelineWith(goodExpectedArtifacts, trigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);
    PubsubEvent event =
        createPubsubEvent(
            PubsubSystem.GOOGLE,
            "projects/project/subscriptions/subscription",
            goodArtifacts,
            Map.of());

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).isEmpty();
  }

  private static Stream<Arguments> missingFieldParams() {
    return Stream.of(
        Arguments.of(enabledGooglePubsubTrigger.withSubscriptionName(null), "subscriptionName"),
        Arguments.of(enabledGooglePubsubTrigger.withPubsubSystem(null), "pubsubSystem"));
  }

  @ParameterizedTest(
      name = "does not trigger a pipeline that has an enabled pubsub trigger with missing {1}")
  @MethodSource("missingFieldParams")
  void doesNotTriggerPipelineWithMissingField(Trigger trigger, String field)
      throws TimeoutException {
    PubsubEvent event =
        createPubsubEvent(
            PubsubSystem.GOOGLE,
            "projects/project/subscriptions/subscription",
            List.of(),
            Map.of());
    Pipeline goodPipeline = createPipelineWith(goodExpectedArtifacts, enabledGooglePubsubTrigger);
    Pipeline badPipeline = createPipelineWith(goodExpectedArtifacts, trigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(badPipeline, goodPipeline);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).hasSize(1);
    assertThat(matchingPipelines.get(0).getId()).isEqualTo(goodPipeline.getId());
  }

  private static PubsubEvent eventWithPayloadAndAttributes(
      Map<String, Object> payload, Map<String, String> attributes) {
    PubsubEvent event = new PubsubEvent();
    MessageDescription description =
        MessageDescription.builder()
            .pubsubSystem(PubsubSystem.GOOGLE)
            .ackDeadlineSeconds(1)
            .subscriptionName("projects/project/subscriptions/subscription")
            .messagePayload("{\"key\":\"value\"}")
            .messageAttributes(attributes)
            .build();
    PubsubEvent.Content content = new PubsubEvent.Content();
    content.setMessageDescription(description);
    event.setPayload(payload);
    event.setContent(content);
    Metadata details = new Metadata();
    details.setType(PubsubEventHandler.PUBSUB_TRIGGER_TYPE);
    details.setAttributes(attributes);
    event.setDetails(details);
    return event;
  }

  private static Stream<Arguments> payloadConstraintParams() {
    return Stream.of(
        Arguments.of(enabledGooglePubsubTrigger, 1),
        Arguments.of(enabledGooglePubsubTrigger.withPayloadConstraints(Map.of("key", "value")), 1),
        Arguments.of(
            enabledGooglePubsubTrigger.withPayloadConstraints(Map.of("key", "wrongValue")), 0));
  }

  @ParameterizedTest
  @MethodSource("payloadConstraintParams")
  void conditionallyTriggersPipelineOnPayloadConstraints(Trigger trigger, int callCount)
      throws TimeoutException {
    Pipeline pipeline = createPipelineWith(goodExpectedArtifacts, trigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);
    PubsubEvent event = eventWithPayloadAndAttributes(Map.of("key", "value"), null);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).hasSize(callCount);
    if (callCount > 0) {
      assertThat(matchingPipelines.get(0).getApplication()).isEqualTo(pipeline.getApplication());
      assertThat(matchingPipelines.get(0).getName()).isEqualTo(pipeline.getName());
    }
  }

  private static Stream<Arguments> attributeConstraintParams() {
    return Stream.of(
        Arguments.of(enabledGooglePubsubTrigger, 1),
        Arguments.of(
            enabledGooglePubsubTrigger.withAttributeConstraints(Map.of("key", "value")), 1),
        Arguments.of(
            enabledGooglePubsubTrigger.withAttributeConstraints(Map.of("key", "wrongValue")), 0));
  }

  @ParameterizedTest
  @MethodSource("attributeConstraintParams")
  void conditionallyTriggersPipelineOnAttributeConstraints(Trigger trigger, int callCount)
      throws TimeoutException {
    Pipeline pipeline = createPipelineWith(goodExpectedArtifacts, trigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);
    PubsubEvent event =
        eventWithPayloadAndAttributes(Map.of("key", "value"), Map.of("key", "value"));

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).hasSize(callCount);
    if (callCount > 0) {
      assertThat(matchingPipelines.get(0).getApplication()).isEqualTo(pipeline.getApplication());
      assertThat(matchingPipelines.get(0).getName()).isEqualTo(pipeline.getName());
    }
  }

  @Test
  void setsLinkDetailsIfDefined() {
    Trigger trigger = enabledGooglePubsubTrigger;

    PubsubEvent event = new PubsubEvent();
    MessageDescription description =
        MessageDescription.builder()
            .pubsubSystem(PubsubSystem.GOOGLE)
            .ackDeadlineSeconds(1)
            .subscriptionName("projects/project/subscriptions/subscription")
            .messagePayload("{\"key\":\"value\"}")
            .messageAttributes(Map.of("key", "value"))
            .build();
    PubsubEvent.Content content = new PubsubEvent.Content();
    content.setMessageDescription(description);
    event.setContent(content);

    String link = "https://sample.com";
    String linkText = "someLinkText";
    event.setPayload(Map.of("link", link, "linkText", linkText));

    Function<Trigger, Trigger> triggerBuilder = eventHandler.buildTrigger(event);
    Trigger outputTrigger = triggerBuilder.apply(trigger);

    assertThat(outputTrigger.getLink()).isEqualTo(link);
    assertThat(outputTrigger.getLinkText()).isEqualTo(linkText);
  }
}
