/*
 * Copyright 2025 Harness, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.netflix.spinnaker.echo.model.trigger.AbstractOCIRegistryEvent;
import com.netflix.spinnaker.echo.model.trigger.HelmOciEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
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

public class HelmOciEventHadlerTest {
  private NoopRegistry registry = new NoopRegistry();
  private ObjectMapper objectMapper = EchoObjectMapper.getInstance();
  private TestEventHandlerSupport testEventSupport = new TestEventHandlerSupport();
  private FiatPermissionEvaluator fiatPermissionEvaluator;
  private AtomicInteger nextId = new AtomicInteger(1);

  private HelmOciEventHandler eventHandler;

  @BeforeEach
  public void setUp() {
    fiatPermissionEvaluator = mock(FiatPermissionEvaluator.class);
    when(fiatPermissionEvaluator.hasPermission(
            any(String.class), any(String.class), eq("APPLICATION"), eq("EXECUTE")))
        .thenReturn(true);

    eventHandler = new HelmOciEventHandler(registry, objectMapper, fiatPermissionEvaluator);
  }

  // Override the createHelmOciEvent method from RetrofitStubs
  private HelmOciEvent createHelmOciEvent(String tag) {
    return createHelmOciEvent(tag, null);
  }

  // Create HelmOciEvent with digest
  private HelmOciEvent createHelmOciEvent(String tag, String digest) {
    HelmOciEvent event = new HelmOciEvent();
    event.setContent(
        new AbstractOCIRegistryEvent.Content("account", "registry", "repository", tag, digest));
    // Initialize details if null
    if (event.getDetails() == null) {
      event.setDetails(new Metadata());
    }
    event.getDetails().setType(HelmOciEvent.TYPE);
    event.getDetails().setSource("junit");
    return event;
  }

  @Test
  @DisplayName("Triggers pipelines for successful builds")
  public void triggersPipelinesForSuccessfulBuilds() throws TimeoutException {
    // given
    Trigger trigger = getStaticEnabledHelmOciTrigger();
    Pipeline pipeline = createPipelineWith(trigger);
    PipelineCache pipelines = testEventSupport.pipelineCache(pipeline);
    HelmOciEvent event = createHelmOciEvent(trigger.getTag());

    // when
    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    // then
    assertEquals(1, matchingPipelines.size());
    assertEquals(pipeline.getApplication(), matchingPipelines.get(0).getApplication());
    assertEquals(pipeline.getName(), matchingPipelines.get(0).getName());
  }

  @Test
  @DisplayName("Attaches helm oci trigger to the pipeline")
  public void attachesHelmOciTriggerToPipeline() throws TimeoutException {
    // given
    Trigger trigger = getStaticEnabledHelmOciTrigger();
    HelmOciEvent event = createHelmOciEvent(trigger.getTag());
    Artifact artifact =
        Artifact.builder()
            .type("helm/image")
            .name(event.getContent().getRegistry() + "/" + event.getContent().getRepository())
            .version(event.getContent().getTag())
            .reference(
                event.getContent().getRegistry()
                    + "/"
                    + event.getContent().getRepository()
                    + ":"
                    + event.getContent().getTag())
            .build();
    Pipeline pipeline =
        createPipelineWith(
            getStaticEnabledJenkinsTrigger(),
            getStaticNonJenkinsTrigger(),
            trigger,
            getStaticDisabledHelmOciTrigger());
    PipelineCache pipelines = testEventSupport.pipelineCache(pipeline);

    // when
    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    // then
    assertEquals(1, matchingPipelines.size());
    assertEquals(trigger.getType(), matchingPipelines.get(0).getTrigger().getType());
    assertEquals(trigger.getAccount(), matchingPipelines.get(0).getTrigger().getAccount());
    assertEquals(trigger.getRepository(), matchingPipelines.get(0).getTrigger().getRepository());
    assertEquals(trigger.getTag(), matchingPipelines.get(0).getTrigger().getTag());
    assertEquals(1, matchingPipelines.get(0).getReceivedArtifacts().size());
    assertEquals(artifact, matchingPipelines.get(0).getReceivedArtifacts().get(0));
  }

  @Test
  @DisplayName("Attaches helm oci trigger digest to the pipeline")
  public void attachesHelmOciTriggerDigestToPipeline() throws TimeoutException {
    // given
    Trigger trigger = getStaticEnabledHelmOciTrigger();
    HelmOciEvent event = createHelmOciEvent("tag", "sha123");
    Artifact artifact =
        Artifact.builder()
            .type("helm/image")
            .name(event.getContent().getRegistry() + "/" + event.getContent().getRepository())
            .version(event.getContent().getTag())
            .reference(
                event.getContent().getRegistry()
                    + "/"
                    + event.getContent().getRepository()
                    + ":"
                    + event.getContent().getTag())
            .build();
    Pipeline pipeline =
        createPipelineWith(
            getStaticEnabledJenkinsTrigger(),
            getStaticNonJenkinsTrigger(),
            trigger,
            getStaticDisabledHelmOciTrigger());
    PipelineCache pipelines = testEventSupport.pipelineCache(pipeline);

    // when
    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    // then
    assertEquals(1, matchingPipelines.size());
    assertEquals(trigger.getType(), matchingPipelines.get(0).getTrigger().getType());
    assertEquals(trigger.getAccount(), matchingPipelines.get(0).getTrigger().getAccount());
    assertEquals(trigger.getRepository(), matchingPipelines.get(0).getTrigger().getRepository());
    assertEquals("tag", matchingPipelines.get(0).getTrigger().getTag());
    assertEquals("sha123", matchingPipelines.get(0).getTrigger().getDigest());
    assertEquals(1, matchingPipelines.get(0).getReceivedArtifacts().size());
    assertEquals(artifact, matchingPipelines.get(0).getReceivedArtifacts().get(0));
  }

  @Test
  @DisplayName("An event can trigger multiple pipelines")
  public void eventCanTriggerMultiplePipelines() throws TimeoutException {
    // given
    Trigger trigger = getStaticEnabledHelmOciTrigger();
    HelmOciEvent event = createHelmOciEvent(trigger.getTag());
    List<Pipeline> pipelineList =
        Arrays.asList(
            Pipeline.builder()
                .application("application")
                .name("pipeline1")
                .id("id")
                .triggers(Collections.singletonList(trigger))
                .build(),
            Pipeline.builder()
                .application("application")
                .name("pipeline2")
                .id("id")
                .triggers(Collections.singletonList(trigger))
                .build());
    PipelineCache pipelines = testEventSupport.pipelineCache(pipelineList);

    // when
    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    // then
    assertEquals(pipelineList.size(), matchingPipelines.size());
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("provideTriggerAndDescriptionData")
  @DisplayName("Does not trigger certain pipelines")
  public void doesNotTriggerCertainPipelines(Trigger trigger, String description)
      throws TimeoutException {
    // given
    Pipeline pipeline = createPipelineWith(trigger);
    PipelineCache pipelines = testEventSupport.pipelineCache(pipeline);
    HelmOciEvent event = createHelmOciEvent(getStaticEnabledHelmOciTrigger().getTag());

    // when
    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    // then
    assertEquals(0, matchingPipelines.size());
  }

  private static Stream<Arguments> provideTriggerAndDescriptionData() {
    return Stream.of(
        Arguments.of(getStaticDisabledHelmOciTrigger(), "disabled helm oci trigger"),
        Arguments.of(getStaticNonJenkinsTrigger(), "non-HelmOci"));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("provideHelmOciTriggerAndDescriptionData")
  @DisplayName("Does not trigger certain pipelines for helm oci")
  public void doesNotTriggerCertainPipelinesForHelmOci(Trigger trigger, String description)
      throws TimeoutException {
    // given
    Pipeline pipeline = createPipelineWith(trigger);
    PipelineCache pipelines = testEventSupport.pipelineCache(pipeline);
    HelmOciEvent event = createHelmOciEvent(getStaticEnabledHelmOciTrigger().getTag());

    // when
    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    // then
    assertEquals(0, matchingPipelines.size());
  }

  private static Stream<Arguments> provideHelmOciTriggerAndDescriptionData() {
    return Stream.of(
        Arguments.of(getStaticDisabledHelmOciTrigger(), "disabled helm oci trigger"),
        Arguments.of(
            getStaticEnabledHelmOciTrigger().withAccount("notRegistry"), "different registry"),
        Arguments.of(
            getStaticEnabledHelmOciTrigger().withRepository("notRepository"),
            "different repository"));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("provideMissingFieldData")
  @DisplayName(
      "Does not trigger a pipeline that has an enabled helm oci trigger with missing field")
  public void doesNotTriggerPipelineWithMissingField(Trigger trigger, String field)
      throws TimeoutException {
    // given
    Pipeline goodPipeline = createPipelineWith(getStaticEnabledHelmOciTrigger());
    Pipeline badPipeline = createPipelineWith(trigger);
    PipelineCache pipelines = testEventSupport.pipelineCache(badPipeline, goodPipeline);
    HelmOciEvent event = createHelmOciEvent(getStaticEnabledHelmOciTrigger().getTag());

    // when
    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    // then
    assertEquals(1, matchingPipelines.size());
    assertEquals(goodPipeline.getId(), matchingPipelines.get(0).getId());
  }

  private static Stream<Arguments> provideMissingFieldData() {
    return Stream.of(
        Arguments.of(getStaticEnabledHelmOciTrigger().withAccount(null), "account"),
        Arguments.of(getStaticEnabledHelmOciTrigger().withRepository(null), "repository"));
  }

  @Test
  @DisplayName("Triggers a pipeline that has an enabled helm oci trigger with regex")
  public void triggersPipelineWithRegex() throws TimeoutException {
    // given
    Trigger trigger = getStaticEnabledHelmOciTrigger().withTag("\\d+");
    Pipeline pipeline = createPipelineWith(trigger);
    PipelineCache pipelines = testEventSupport.pipelineCache(pipeline);
    HelmOciEvent event = createHelmOciEvent("2");

    // when
    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    // then
    assertEquals(1, matchingPipelines.size());
    assertEquals(pipeline.getId(), matchingPipelines.get(0).getId());
  }

  private Pipeline createPipelineWith(Trigger... triggers) {
    return Pipeline.builder()
        .application("application")
        .name("name")
        .id(String.valueOf(nextId.getAndIncrement()))
        .triggers(List.of(triggers))
        .build();
  }

  private static Trigger getStaticEnabledHelmOciTrigger() {
    return Trigger.builder()
        .enabled(true)
        .type("helm/oci")
        .account("account")
        .repository("repository")
        .tag("tag")
        .build();
  }

  private static Trigger getStaticDisabledHelmOciTrigger() {
    return Trigger.builder()
        .enabled(false)
        .type("helm/oci")
        .account("account")
        .repository("repository")
        .tag("tag")
        .build();
  }

  private static Trigger getStaticNonJenkinsTrigger() {
    return Trigger.builder().enabled(true).type("not jenkins").master("master").job("job").build();
  }

  private static Trigger getStaticEnabledJenkinsTrigger() {
    return Trigger.builder().enabled(true).type("jenkins").master("master").job("job").build();
  }
}
