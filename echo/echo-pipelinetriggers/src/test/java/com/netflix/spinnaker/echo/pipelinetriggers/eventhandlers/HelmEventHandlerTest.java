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
import com.netflix.spinnaker.echo.model.trigger.HelmEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HelmEventHandlerTest {
  private final NoopRegistry registry = new NoopRegistry();
  private final ObjectMapper objectMapper = EchoObjectMapper.getInstance();
  private final TestEventHandlerSupport testEventSupport = new TestEventHandlerSupport();
  private final AtomicInteger nextId = new AtomicInteger(1);

  private FiatPermissionEvaluator fiatPermissionEvaluator;
  private HelmEventHandler eventHandler;

  @BeforeEach
  void setUp() {
    fiatPermissionEvaluator = mock(FiatPermissionEvaluator.class);
    when(fiatPermissionEvaluator.hasPermission(
            any(String.class), any(String.class), eq("APPLICATION"), eq("EXECUTE")))
        .thenReturn(true);

    eventHandler = new HelmEventHandler(registry, objectMapper, fiatPermissionEvaluator);
  }

  private static HelmEvent createHelmEvent() {
    return createHelmEvent("1.0.0");
  }

  private static HelmEvent createHelmEvent(String version) {
    HelmEvent event = new HelmEvent();
    event.setContent(new HelmEvent.Content("account", "chart", version, "digest"));
    Metadata details = new Metadata();
    details.setType(HelmEvent.TYPE);
    details.setSource("junit");
    event.setDetails(details);
    return event;
  }

  private static Trigger getStaticEnabledHelmTrigger() {
    return Trigger.builder()
        .enabled(true)
        .type("helm")
        .account("account")
        .version("1.0.0")
        .digest("digest")
        .build();
  }

  private static Trigger getStaticDisabledHelmTrigger() {
    return Trigger.builder()
        .enabled(false)
        .type("helm")
        .account("account")
        .version("1.0.0")
        .digest("digest")
        .build();
  }

  private static Trigger getStaticNonJenkinsTrigger() {
    return Trigger.builder().enabled(true).type("not jenkins").master("master").job("job").build();
  }

  private Pipeline createPipelineWith(Trigger... triggers) {
    return Pipeline.builder()
        .application("application")
        .name("name")
        .id(String.valueOf(nextId.getAndIncrement()))
        .triggers(List.of(triggers))
        .build();
  }

  @ParameterizedTest(name = "honors pipeline trigger semver ({2})")
  @MethodSource("provideSemverData")
  void honorsPipelineTriggerSemver(HelmEvent event, Trigger trigger, boolean matches)
      throws TimeoutException {
    Pipeline pipeline = createPipelineWith(trigger);
    PipelineCache pipelines = testEventSupport.pipelineCache(pipeline);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).hasSize(matches ? 1 : 0);
  }

  private static Stream<Arguments> provideSemverData() {
    return Stream.of(
        Arguments.of(
            createHelmEvent("1.0.0"), getStaticEnabledHelmTrigger().withVersion(null), true),
        Arguments.of(createHelmEvent("1.0.0"), getStaticEnabledHelmTrigger().withVersion(""), true),
        Arguments.of(
            createHelmEvent("1.0.1"), getStaticEnabledHelmTrigger().withVersion("~1.0.0"), true),
        Arguments.of(
            createHelmEvent("1.1.0"), getStaticEnabledHelmTrigger().withVersion("~1.0.0"), false),
        Arguments.of(
            createHelmEvent("1.0.1"), getStaticEnabledHelmTrigger().withVersion("^1.0.0"), true),
        Arguments.of(
            createHelmEvent("1.1.0"), getStaticEnabledHelmTrigger().withVersion("^1.0.0"), true),
        Arguments.of(
            createHelmEvent("1.0.0"), getStaticEnabledHelmTrigger().withVersion("1.0.0"), true),
        Arguments.of(
            createHelmEvent("1.0.1"), getStaticEnabledHelmTrigger().withVersion("1.0.0"), false));
  }

  @Test
  @DisplayName("An event can trigger multiple pipelines")
  void eventCanTriggerMultiplePipelines() throws TimeoutException {
    HelmEvent event = createHelmEvent("1.0.0");
    List<Pipeline> pipelineList =
        Arrays.asList(
            Pipeline.builder()
                .application("application")
                .name("pipeline1")
                .id("id")
                .triggers(Collections.singletonList(getStaticEnabledHelmTrigger()))
                .build(),
            Pipeline.builder()
                .application("application")
                .name("pipeline2")
                .id("id")
                .triggers(Collections.singletonList(getStaticEnabledHelmTrigger()))
                .build());
    PipelineCache pipelines = testEventSupport.pipelineCache(pipelineList);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).hasSize(pipelineList.size());
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("provideDoesNotTriggerData")
  @DisplayName("Does not trigger certain pipelines")
  void doesNotTriggerCertainPipelines(Trigger trigger, String description) throws TimeoutException {
    Pipeline pipeline = createPipelineWith(trigger);
    PipelineCache pipelines = testEventSupport.pipelineCache(pipeline);
    HelmEvent event = createHelmEvent();

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).isEmpty();
  }

  private static Stream<Arguments> provideDoesNotTriggerData() {
    return Stream.of(
        Arguments.of(getStaticDisabledHelmTrigger(), "disabled Helm trigger"),
        Arguments.of(getStaticNonJenkinsTrigger(), "non-Helm"),
        Arguments.of(getStaticEnabledHelmTrigger().withAccount("FAKE"), "wrong account"),
        Arguments.of(getStaticEnabledHelmTrigger().withAccount(null), "no account"));
  }
}
