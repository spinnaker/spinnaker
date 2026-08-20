/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.echo.api.events.Metadata;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.ArtifactoryEvent;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArtifactoryEventHandlerTest {

  private final TestEventHandlerSupport handlerSupport = new TestEventHandlerSupport();
  private FiatPermissionEvaluator fiatPermissionEvaluator;
  private ArtifactoryEventHandler eventHandler;

  @BeforeEach
  void setUp() {
    fiatPermissionEvaluator = mock(FiatPermissionEvaluator.class);
    when(fiatPermissionEvaluator.hasPermission(
            any(String.class), any(String.class), eq("APPLICATION"), eq("EXECUTE")))
        .thenReturn(true);
    eventHandler =
        new ArtifactoryEventHandler(
            new NoopRegistry(), EchoObjectMapper.getInstance(), fiatPermissionEvaluator);
  }

  private static ArtifactoryEvent createArtifactoryEvent() {
    ArtifactoryEvent artifactoryEvent = new ArtifactoryEvent();

    ArtifactoryEvent.Content content = new ArtifactoryEvent.Content();
    content.setName("artifactorySearchName");
    content.setArtifact(createArtifactoryArtifact());

    Metadata metadata = new Metadata();
    metadata.setType(ArtifactoryEvent.TYPE);

    artifactoryEvent.setContent(content);
    artifactoryEvent.setDetails(metadata);
    return artifactoryEvent;
  }

  private static Artifact createArtifactoryArtifact() {
    return Artifact.builder()
        .type("maven/file")
        .reference("com.test.spinnaker:artifact:0.1.0-dev")
        .name("com.test.spinnaker:artifact")
        .version("0.1.0-dev")
        .provenance("repo")
        .build();
  }

  private static ExpectedArtifact createMavenExpectedArtifact() {
    return ExpectedArtifact.builder()
        .matchArtifact(
            Artifact.builder()
                .type("maven/file")
                .uuid("1234")
                .name("com.test.spinnaker:artifact")
                .reference("com.test.spinnaker:artifact:0.2.0-dev")
                .build())
        .build();
  }

  private static ExpectedArtifact createMavenExpectedArtifactWithRegex(String regex) {
    return ExpectedArtifact.builder()
        .id("1234")
        .matchArtifact(
            Artifact.builder()
                .type("maven/file")
                .name("com.test.spinnaker:artifact")
                .reference(regex)
                .build())
        .build();
  }

  private static Pipeline createPipelineWith(List<Trigger> triggers) {
    return Pipeline.builder().application("application").name("name").triggers(triggers).build();
  }

  private static Pipeline createPipelineWith(
      List<ExpectedArtifact> expectedArtifacts, List<Trigger> triggers) {
    return Pipeline.builder()
        .application("application")
        .name("name")
        .triggers(triggers)
        .expectedArtifacts(expectedArtifacts)
        .build();
  }

  private static Trigger createEnabledArtifactoryTrigger() {
    return createEnabledArtifactoryTrigger("artifactorySearchName");
  }

  private static Trigger createEnabledArtifactoryTrigger(String searchName) {
    return Trigger.builder()
        .enabled(true)
        .type("artifactory")
        .artifactorySearchName(searchName)
        .build();
  }

  private static Trigger createEnabledArtifactoryTriggerWithExpectedArtifactIdAndSearchName(
      String expectedArtifactId) {
    return Trigger.builder()
        .enabled(true)
        .type("artifactory")
        .artifactorySearchName("artifactorySearchName")
        .expectedArtifactIds(Collections.singletonList(expectedArtifactId))
        .build();
  }

  private static Trigger createDisabledArtifactoryTrigger() {
    return Trigger.builder()
        .enabled(false)
        .type("artifactory")
        .artifactorySearchName("artifactorySearchName")
        .build();
  }

  private static Trigger createEnabledGitTrigger() {
    return Trigger.builder()
        .enabled(true)
        .type("git")
        .source("bitbucket")
        .project("project")
        .slug("slug")
        .build();
  }

  private static Trigger createDisabledGitTrigger() {
    return Trigger.builder()
        .enabled(false)
        .type("git")
        .source("bitbucket")
        .project("project")
        .slug("slug")
        .build();
  }

  @Test
  void getMatchingPipelinesTriggersEnabledArtifactoryPipeline() throws TimeoutException {
    ArtifactoryEvent artifactoryEvent = createArtifactoryEvent();
    Pipeline artifactoryPipeline =
        createPipelineWith(Collections.singletonList(createEnabledArtifactoryTrigger()));

    List<Pipeline> matchingPipelines =
        eventHandler.getMatchingPipelines(
            artifactoryEvent, handlerSupport.pipelineCache(artifactoryPipeline));

    assertThat(matchingPipelines).hasSize(1);
    assertThat(matchingPipelines.get(0).getApplication())
        .isEqualTo(artifactoryPipeline.getApplication());
    assertThat(matchingPipelines.get(0).getName()).isEqualTo(artifactoryPipeline.getName());
  }

  @Test
  void getMatchingPipelinesAttachesArtifactoryTriggerAndReceivedArtifactsToPipeline()
      throws TimeoutException {
    ArtifactoryEvent artifactoryEvent = createArtifactoryEvent();
    Pipeline artifactoryPipeline =
        createPipelineWith(
            List.of(
                createEnabledArtifactoryTrigger(),
                createDisabledArtifactoryTrigger(),
                createEnabledGitTrigger(),
                createDisabledGitTrigger()));
    Trigger expectedTrigger = createEnabledArtifactoryTrigger();
    Artifact expectedArtifact = createArtifactoryArtifact();

    List<Pipeline> matchingPipelines =
        eventHandler.getMatchingPipelines(
            artifactoryEvent, handlerSupport.pipelineCache(artifactoryPipeline));

    assertThat(matchingPipelines).hasSize(1);
    assertThat(matchingPipelines.get(0).getTrigger().getType())
        .isEqualTo(expectedTrigger.getType());
    assertThat(matchingPipelines.get(0).getTrigger().getArtifactorySearchName())
        .isEqualTo(expectedTrigger.getArtifactorySearchName());
    assertThat(matchingPipelines.get(0).getTrigger().isEnabled()).isTrue();
    assertThat(matchingPipelines.get(0).getReceivedArtifacts().get(0)).isEqualTo(expectedArtifact);
  }

  @Test
  void getMatchingPipelinesCanTriggerMultiplePipelines() throws TimeoutException {
    ArtifactoryEvent artifactoryEvent = createArtifactoryEvent();
    Pipeline artifactoryPipeline =
        createPipelineWith(Collections.singletonList(createEnabledArtifactoryTrigger()));

    List<Pipeline> matchingPipelines =
        eventHandler.getMatchingPipelines(
            artifactoryEvent,
            handlerSupport.pipelineCache(
                artifactoryPipeline,
                artifactoryPipeline.withName("another pipeline with the same trigger")));

    assertThat(matchingPipelines).hasSize(2);
  }

  @Test
  void getMatchingPipelinesDoesNotTriggerDisabledArtifactoryPipeline() throws TimeoutException {
    ArtifactoryEvent artifactoryEvent = createArtifactoryEvent();
    Pipeline artifactoryPipeline =
        createPipelineWith(Collections.singletonList(createDisabledArtifactoryTrigger()));

    List<Pipeline> matchingPipelines =
        eventHandler.getMatchingPipelines(
            artifactoryEvent, handlerSupport.pipelineCache(artifactoryPipeline));

    assertThat(matchingPipelines).isEmpty();
  }

  @Test
  void getMatchingPipelinesDoesNotTriggerEnabledNonArtifactoryPipeline() throws TimeoutException {
    ArtifactoryEvent artifactoryEvent = createArtifactoryEvent();
    Pipeline artifactoryPipeline =
        createPipelineWith(Collections.singletonList(createEnabledGitTrigger()));

    List<Pipeline> matchingPipelines =
        eventHandler.getMatchingPipelines(
            artifactoryEvent, handlerSupport.pipelineCache(artifactoryPipeline));

    assertThat(matchingPipelines).isEmpty();
  }

  @Test
  void getMatchingPipelinesDoesNotTriggerEnabledArtifactoryPipelineWithDifferentSearchName()
      throws TimeoutException {
    ArtifactoryEvent artifactoryEvent = createArtifactoryEvent();
    Pipeline artifactoryPipeline =
        createPipelineWith(
            Collections.singletonList(createEnabledArtifactoryTrigger("groovy-name")));

    List<Pipeline> matchingPipelines =
        eventHandler.getMatchingPipelines(
            artifactoryEvent, handlerSupport.pipelineCache(artifactoryPipeline));

    assertThat(matchingPipelines).isEmpty();
  }

  @Test
  void getMatchingPipelinesDoesNotTriggerEnabledArtifactoryPipelineWithDifferentExpectedArtifact()
      throws TimeoutException {
    ExpectedArtifact expectedArtifact = createMavenExpectedArtifact();
    ArtifactoryEvent artifactoryEvent = createArtifactoryEvent();
    Trigger trigger = createEnabledArtifactoryTriggerWithExpectedArtifactIdAndSearchName("1234");
    Pipeline artifactoryPipeline =
        createPipelineWith(
            Collections.singletonList(expectedArtifact), Collections.singletonList(trigger));

    List<Pipeline> matchingPipelines =
        eventHandler.getMatchingPipelines(
            artifactoryEvent, handlerSupport.pipelineCache(artifactoryPipeline));

    assertThat(matchingPipelines).isEmpty();
  }

  @Test
  void
      getMatchingPipelinesDoesNotTriggerEnabledArtifactoryPipelineWithExpectedArtifactRegexNoMatch()
          throws TimeoutException {
    ExpectedArtifact expectedArtifact =
        createMavenExpectedArtifactWithRegex("com.test.spinnaker:artifact:1.1.*");
    ArtifactoryEvent artifactoryEvent = createArtifactoryEvent();
    Trigger trigger = createEnabledArtifactoryTriggerWithExpectedArtifactIdAndSearchName("1234");
    Pipeline artifactoryPipeline =
        createPipelineWith(
            Collections.singletonList(expectedArtifact), Collections.singletonList(trigger));

    List<Pipeline> matchingPipelines =
        eventHandler.getMatchingPipelines(
            artifactoryEvent, handlerSupport.pipelineCache(artifactoryPipeline));

    assertThat(matchingPipelines).isEmpty();
  }

  @Test
  void getMatchingPipelinesTriggersEnabledArtifactoryPipelineWithExpectedArtifactRegexMatch()
      throws TimeoutException {
    ExpectedArtifact expectedArtifact =
        createMavenExpectedArtifactWithRegex("com.test.spinnaker:artifact:.*");
    ArtifactoryEvent artifactoryEvent = createArtifactoryEvent();
    Trigger trigger = createEnabledArtifactoryTriggerWithExpectedArtifactIdAndSearchName("1234");
    Pipeline artifactoryPipeline =
        createPipelineWith(
            Collections.singletonList(expectedArtifact), Collections.singletonList(trigger));

    List<Pipeline> matchingPipelines =
        eventHandler.getMatchingPipelines(
            artifactoryEvent, handlerSupport.pipelineCache(artifactoryPipeline));

    assertThat(matchingPipelines).hasSize(1);
  }
}
