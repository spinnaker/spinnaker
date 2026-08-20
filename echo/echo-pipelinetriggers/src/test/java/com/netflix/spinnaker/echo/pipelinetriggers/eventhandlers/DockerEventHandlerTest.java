/*
 * Copyright 2016 Google, Inc.
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
import com.netflix.spinnaker.echo.model.trigger.DockerEvent;
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

public class DockerEventHandlerTest {
  private NoopRegistry registry = new NoopRegistry();
  private ObjectMapper objectMapper = EchoObjectMapper.getInstance();
  private TestEventHandlerSupport testEventSupport = new TestEventHandlerSupport();
  private FiatPermissionEvaluator fiatPermissionEvaluator;
  private AtomicInteger nextId = new AtomicInteger(1);

  private DockerEventHandler eventHandler;

  @BeforeEach
  public void setUp() {
    fiatPermissionEvaluator = mock(FiatPermissionEvaluator.class);
    when(fiatPermissionEvaluator.hasPermission(
            any(String.class), any(String.class), eq("APPLICATION"), eq("EXECUTE")))
        .thenReturn(true);

    eventHandler = new DockerEventHandler(registry, objectMapper, fiatPermissionEvaluator);
  }

  private DockerEvent createDockerEvent(String tag) {
    return createDockerEvent(tag, null);
  }

  private DockerEvent createDockerEvent(String tag, String digest) {
    DockerEvent event = new DockerEvent();
    event.setContent(
        new AbstractOCIRegistryEvent.Content("account", "registry", "repository", tag, digest));
    if (event.getDetails() == null) {
      event.setDetails(new Metadata());
    }
    event.getDetails().setType(DockerEvent.TYPE);
    event.getDetails().setSource("junit");
    return event;
  }

  @ParameterizedTest(name = "triggers pipelines for successful builds for {1}")
  @MethodSource("provideTriggerAndTypeData")
  public void triggersPipelinesForSuccessfulBuilds(
      DockerEvent event, Trigger trigger, String triggerType) throws TimeoutException {
    // given
    Pipeline pipeline = createPipelineWith(trigger);
    PipelineCache pipelines = testEventSupport.pipelineCache(pipeline);

    // when
    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    // then
    assertEquals(1, matchingPipelines.size());
    assertEquals(pipeline.getApplication(), matchingPipelines.get(0).getApplication());
    assertEquals(pipeline.getName(), matchingPipelines.get(0).getName());
  }

  private static Stream<Arguments> provideTriggerAndTypeData() {
    Trigger trigger = getStaticEnabledDockerTrigger();
    return Stream.of(Arguments.of(createStaticDockerEvent(trigger.getTag()), trigger, "docker"));
  }

  @Test
  @DisplayName("Attaches docker trigger to the pipeline")
  public void attachesDockerTriggerToPipeline() throws TimeoutException {
    // given
    Trigger trigger = getStaticEnabledDockerTrigger();
    DockerEvent event = createDockerEvent(trigger.getTag());
    Artifact artifact =
        Artifact.builder()
            .type("docker/image")
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
            getStaticDisabledDockerTrigger());
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
  @DisplayName("Attaches docker trigger digest to the pipeline")
  public void attachesDockerTriggerDigestToPipeline() throws TimeoutException {
    // given
    Trigger trigger = getStaticEnabledDockerTrigger();
    DockerEvent event = createDockerEvent("tag", "sha123");
    Artifact artifact =
        Artifact.builder()
            .type("docker/image")
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
            getStaticDisabledDockerTrigger());
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
    Trigger trigger = getStaticEnabledDockerTrigger();
    DockerEvent event = createDockerEvent(trigger.getTag());
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
    DockerEvent event = createDockerEvent(getStaticEnabledDockerTrigger().getTag());

    // when
    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    // then
    assertEquals(0, matchingPipelines.size());
  }

  private static Stream<Arguments> provideTriggerAndDescriptionData() {
    return Stream.of(
        Arguments.of(getStaticDisabledDockerTrigger(), "disabled docker trigger"),
        Arguments.of(getStaticNonJenkinsTrigger(), "non-Jenkins"));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("provideDockerTriggerAndDescriptionData")
  @DisplayName("Does not trigger certain pipelines for docker")
  public void doesNotTriggerCertainPipelinesForDocker(Trigger trigger, String description)
      throws TimeoutException {
    // given
    Pipeline pipeline = createPipelineWith(trigger);
    PipelineCache pipelines = testEventSupport.pipelineCache(pipeline);
    DockerEvent event = createDockerEvent(getStaticEnabledDockerTrigger().getTag());

    // when
    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    // then
    assertEquals(0, matchingPipelines.size());
  }

  private static Stream<Arguments> provideDockerTriggerAndDescriptionData() {
    return Stream.of(
        Arguments.of(getStaticDisabledDockerTrigger(), "disabled docker trigger"),
        Arguments.of(
            getStaticEnabledDockerTrigger().withAccount("notRegistry"), "different registry"),
        Arguments.of(
            getStaticEnabledDockerTrigger().withRepository("notRepository"),
            "different repository"));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("provideMissingFieldData")
  @DisplayName("Does not trigger a pipeline that has an enabled docker trigger with missing field")
  public void doesNotTriggerPipelineWithMissingField(Trigger trigger, String field)
      throws TimeoutException {
    // given
    Pipeline goodPipeline = createPipelineWith(getStaticEnabledDockerTrigger());
    Pipeline badPipeline = createPipelineWith(trigger);
    PipelineCache pipelines = testEventSupport.pipelineCache(badPipeline, goodPipeline);
    DockerEvent event = createDockerEvent(getStaticEnabledDockerTrigger().getTag());

    // when
    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    // then
    assertEquals(1, matchingPipelines.size());
    assertEquals(goodPipeline.getId(), matchingPipelines.get(0).getId());
  }

  private static Stream<Arguments> provideMissingFieldData() {
    return Stream.of(
        Arguments.of(getStaticEnabledDockerTrigger().withAccount(null), "account"),
        Arguments.of(getStaticEnabledDockerTrigger().withRepository(null), "repository"));
  }

  @Test
  @DisplayName("Triggers a pipeline that has an enabled docker trigger with regex")
  public void triggersPipelineWithRegex() throws TimeoutException {
    // given
    Trigger trigger = getStaticEnabledDockerTrigger().withTag("\\d+");
    Pipeline pipeline = createPipelineWith(trigger);
    PipelineCache pipelines = testEventSupport.pipelineCache(pipeline);
    DockerEvent event = createDockerEvent("2");

    // when
    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    // then
    assertEquals(1, matchingPipelines.size());
    assertEquals(pipeline.getId(), matchingPipelines.get(0).getId());
  }

  @Test
  @DisplayName("Triggers a pipeline that has an enabled docker trigger with empty string for regex")
  public void triggersPipelineWithEmptyStringRegex() throws TimeoutException {
    // given
    Trigger trigger = getStaticEnabledDockerTrigger().withTag("");
    Pipeline pipeline = createPipelineWith(trigger);
    PipelineCache pipelines = testEventSupport.pipelineCache(pipeline);
    DockerEvent event = createDockerEvent("2");

    // when
    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    // then
    assertEquals(1, matchingPipelines.size());
    assertEquals(pipeline.getId(), matchingPipelines.get(0).getId());
  }

  @Test
  @DisplayName(
      "Triggers a pipeline that has an enabled docker trigger with only whitespace for regex")
  public void triggersPipelineWithWhitespaceRegex() throws TimeoutException {
    // given
    Trigger trigger = getStaticEnabledDockerTrigger().withTag(" \t");
    Pipeline pipeline = createPipelineWith(trigger);
    PipelineCache pipelines = testEventSupport.pipelineCache(pipeline);
    DockerEvent event = createDockerEvent("2");

    // when
    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    // then
    assertEquals(1, matchingPipelines.size());
    assertEquals(pipeline.getId(), matchingPipelines.get(0).getId());
  }

  @Test
  @DisplayName("Does not trigger a pipeline that has an enabled docker trigger with regex")
  public void doesNotTriggerPipelineWithRegex() throws TimeoutException {
    // given
    Trigger trigger = getStaticEnabledDockerTrigger().withTag("\\d+");
    Pipeline pipeline = createPipelineWith(trigger);
    PipelineCache pipelines = testEventSupport.pipelineCache(pipeline);
    DockerEvent event = createDockerEvent(getStaticEnabledDockerTrigger().getTag());

    // when
    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    // then
    assertEquals(0, matchingPipelines.size());
  }

  private Pipeline createPipelineWith(Trigger... triggers) {
    return Pipeline.builder()
        .application("application")
        .name("name")
        .id(String.valueOf(nextId.getAndIncrement()))
        .triggers(List.of(triggers))
        .build();
  }

  private static DockerEvent createStaticDockerEvent(String tag) {
    DockerEvent event = new DockerEvent();
    event.setContent(
        new AbstractOCIRegistryEvent.Content("account", "registry", "repository", tag, null));
    event.setDetails(new Metadata());
    event.getDetails().setType(DockerEvent.TYPE);
    event.getDetails().setSource("junit");
    return event;
  }

  private static Trigger getStaticEnabledDockerTrigger() {
    return Trigger.builder()
        .enabled(true)
        .type("docker")
        .account("account")
        .repository("repository")
        .tag("tag")
        .build();
  }

  private static Trigger getStaticDisabledDockerTrigger() {
    return Trigger.builder()
        .enabled(false)
        .type("docker")
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
