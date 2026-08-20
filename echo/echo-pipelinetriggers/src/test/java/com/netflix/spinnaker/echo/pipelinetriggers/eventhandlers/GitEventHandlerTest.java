/*
 * Copyright 2016 Netflix, Inc.
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

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.echo.api.events.Metadata;
import com.netflix.spinnaker.echo.config.PipelineTriggerConfiguration;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.GitEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GitEventHandlerTest {
  private final NoopRegistry registry = new NoopRegistry();
  private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
      EchoObjectMapper.getInstance();
  private final TestEventHandlerSupport handlerSupport = new TestEventHandlerSupport();
  private final AtomicInteger nextId = new AtomicInteger(1);

  private FiatPermissionEvaluator fiatPermissionEvaluator;
  private PipelineTriggerConfiguration pipelineTriggerConfiguration;
  private GitEventHandler eventHandler;

  @BeforeEach
  void setUp() {
    fiatPermissionEvaluator = mock(FiatPermissionEvaluator.class);
    when(fiatPermissionEvaluator.hasPermission(
            any(String.class), any(String.class), eq("APPLICATION"), eq("EXECUTE")))
        .thenReturn(true);
    pipelineTriggerConfiguration = mock(PipelineTriggerConfiguration.class);
    eventHandler =
        new GitEventHandler(
            registry, objectMapper, fiatPermissionEvaluator, pipelineTriggerConfiguration);
  }

  private Pipeline createPipelineWith(Trigger... triggers) {
    return Pipeline.builder()
        .application("application")
        .name("name")
        .id(String.valueOf(nextId.getAndIncrement()))
        .triggers(List.of(triggers))
        .build();
  }

  private static GitEvent createGitEvent(String eventSource) {
    GitEvent res = new GitEvent();
    res.setContent(new GitEvent.Content("project", "slug", "hash", "master", "action", List.of()));
    Metadata details = new Metadata();
    details.setType(GitEvent.TYPE);
    details.setSource(eventSource);
    res.setDetails(details);
    return res;
  }

  private static Trigger enabledStashTrigger() {
    return Trigger.builder()
        .enabled(true)
        .type("git")
        .source("stash")
        .project("project")
        .slug("slug")
        .build();
  }

  private static Trigger disabledStashTrigger() {
    return Trigger.builder()
        .enabled(false)
        .type("git")
        .source("stash")
        .project("project")
        .slug("slug")
        .build();
  }

  private static Trigger enabledBitBucketTrigger() {
    return Trigger.builder()
        .enabled(true)
        .type("git")
        .source("bitbucket")
        .project("project")
        .slug("slug")
        .build();
  }

  private static Trigger disabledBitBucketTrigger() {
    return Trigger.builder()
        .enabled(false)
        .type("git")
        .source("bitbucket")
        .project("project")
        .slug("slug")
        .build();
  }

  private static Trigger enabledGithubTrigger() {
    return Trigger.builder()
        .enabled(true)
        .type("git")
        .source("github")
        .project("project")
        .slug("slug")
        .build();
  }

  private static Trigger nonJenkinsTrigger() {
    return Trigger.builder().enabled(true).type("not jenkins").master("master").job("job").build();
  }

  private static Stream<Arguments> successfulBuildParams() {
    return Stream.of(
        Arguments.of(createGitEvent("stash"), enabledStashTrigger()),
        Arguments.of(createGitEvent("bitbucket"), enabledBitBucketTrigger()));
  }

  @ParameterizedTest(name = "triggers pipelines for successful builds for {1}")
  @MethodSource("successfulBuildParams")
  void triggersPipelinesForSuccessfulBuilds(GitEvent event, Trigger trigger)
      throws TimeoutException {
    Pipeline pipeline = createPipelineWith(trigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).hasSize(1);
    assertThat(matchingPipelines.get(0).getApplication()).isEqualTo(pipeline.getApplication());
    assertThat(matchingPipelines.get(0).getName()).isEqualTo(pipeline.getName());
  }

  @Test
  void attachesStashTriggerToThePipeline() throws TimeoutException {
    GitEvent event = createGitEvent("stash");
    Pipeline pipeline =
        createPipelineWith(
            Trigger.builder().enabled(true).type("jenkins").master("master").job("job").build(),
            nonJenkinsTrigger(),
            enabledStashTrigger(),
            disabledStashTrigger());
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).hasSize(1);
    assertThat(matchingPipelines.get(0).getTrigger().getType())
        .isEqualTo(enabledStashTrigger().getType());
    assertThat(matchingPipelines.get(0).getTrigger().getProject())
        .isEqualTo(enabledStashTrigger().getProject());
    assertThat(matchingPipelines.get(0).getTrigger().getSlug())
        .isEqualTo(enabledStashTrigger().getSlug());
    assertThat(matchingPipelines.get(0).getTrigger().getHash()).isEqualTo(event.getHash());
    assertThat(matchingPipelines.get(0).getTrigger().getAction()).isEqualTo(event.getAction());
  }

  @Test
  void attachesBitbucketTriggerToThePipeline() throws TimeoutException {
    GitEvent event = createGitEvent("bitbucket");
    Pipeline pipeline =
        createPipelineWith(
            Trigger.builder().enabled(true).type("jenkins").master("master").job("job").build(),
            nonJenkinsTrigger(),
            enabledBitBucketTrigger(),
            disabledBitBucketTrigger());
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).hasSize(1);
    assertThat(matchingPipelines.get(0).getTrigger().getType())
        .isEqualTo(enabledBitBucketTrigger().getType());
    assertThat(matchingPipelines.get(0).getTrigger().getProject())
        .isEqualTo(enabledBitBucketTrigger().getProject());
    assertThat(matchingPipelines.get(0).getTrigger().getSlug())
        .isEqualTo(enabledBitBucketTrigger().getSlug());
    assertThat(matchingPipelines.get(0).getTrigger().getHash()).isEqualTo(event.getHash());
    assertThat(matchingPipelines.get(0).getTrigger().getAction()).isEqualTo(event.getAction());
  }

  @Test
  void eventCanTriggerMultiplePipelines() throws TimeoutException {
    GitEvent event = createGitEvent("stash");
    List<Pipeline> pipelines =
        List.of(
            Pipeline.builder()
                .application("application")
                .name("pipeline1")
                .id("id")
                .triggers(List.of(enabledStashTrigger()))
                .build(),
            Pipeline.builder()
                .application("application")
                .name("pipeline2")
                .id("id")
                .triggers(List.of(enabledStashTrigger()))
                .build());
    PipelineCache cache = handlerSupport.pipelineCache(pipelines);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, cache);

    assertThat(matchingPipelines).hasSize(pipelines.size());
  }

  private static Stream<Arguments> doesNotTriggerParams() {
    return Stream.of(
        Arguments.of(disabledStashTrigger(), "disabled stash trigger"),
        Arguments.of(nonJenkinsTrigger(), "non-Jenkins"));
  }

  @ParameterizedTest(name = "does not trigger {1} pipelines")
  @MethodSource("doesNotTriggerParams")
  void doesNotTriggerPipelines(Trigger trigger, String description) throws TimeoutException {
    Pipeline pipeline = createPipelineWith(trigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);
    GitEvent event = createGitEvent("stash");

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).isEmpty();
  }

  private static Stream<Arguments> doesNotTriggerStashParams() {
    return Stream.of(
        Arguments.of(disabledStashTrigger(), "disabled stash trigger"),
        Arguments.of(enabledStashTrigger().withSlug("notSlug"), "different slug"),
        Arguments.of(enabledStashTrigger().withSource("github"), "different source"),
        Arguments.of(enabledStashTrigger().withProject("notProject"), "different project"),
        Arguments.of(enabledStashTrigger().withBranch("notMaster"), "different branch"));
  }

  @ParameterizedTest(name = "does not trigger {1} pipelines for stash")
  @MethodSource("doesNotTriggerStashParams")
  void doesNotTriggerPipelinesForStash(Trigger trigger, String description)
      throws TimeoutException {
    Pipeline pipeline = createPipelineWith(trigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);
    GitEvent event = createGitEvent("stash");

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).isEmpty();
  }

  private static Stream<Arguments> doesNotTriggerBitbucketParams() {
    return Stream.of(
        Arguments.of(disabledBitBucketTrigger(), "disabled bitbucket trigger"),
        Arguments.of(enabledBitBucketTrigger().withSlug("notSlug"), "different slug"),
        Arguments.of(enabledBitBucketTrigger().withSource("github"), "different source"),
        Arguments.of(enabledBitBucketTrigger().withProject("notProject"), "different project"),
        Arguments.of(enabledBitBucketTrigger().withBranch("notMaster"), "different branch"));
  }

  @ParameterizedTest(name = "does not trigger {1} pipelines for bitbucket")
  @MethodSource("doesNotTriggerBitbucketParams")
  void doesNotTriggerPipelinesForBitbucket(Trigger trigger, String description)
      throws TimeoutException {
    Pipeline pipeline = createPipelineWith(trigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);
    GitEvent event = createGitEvent("bitbucket");

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).isEmpty();
  }

  private static Stream<Arguments> missingFieldStashParams() {
    return Stream.of(
        Arguments.of(enabledStashTrigger().withSlug(null), "slug"),
        Arguments.of(enabledStashTrigger().withProject(null), "project"),
        Arguments.of(enabledStashTrigger().withSource(null), "source"));
  }

  @ParameterizedTest(
      name = "does not trigger a pipeline that has an enabled stash trigger with missing {1}")
  @MethodSource("missingFieldStashParams")
  void doesNotTriggerPipelineWithMissingStashField(Trigger trigger, String field)
      throws TimeoutException {
    GitEvent event = createGitEvent("stash");
    Pipeline goodPipeline = createPipelineWith(enabledStashTrigger());
    Pipeline badPipeline = createPipelineWith(trigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(List.of(goodPipeline, badPipeline));

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).hasSize(1);
    assertThat(matchingPipelines.get(0).getId()).isEqualTo(goodPipeline.getId());
  }

  private static Stream<Arguments> missingFieldBitbucketParams() {
    return Stream.of(
        Arguments.of(enabledBitBucketTrigger().withSlug(null), "slug"),
        Arguments.of(enabledBitBucketTrigger().withProject(null), "project"),
        Arguments.of(enabledBitBucketTrigger().withSource(null), "source"));
  }

  @ParameterizedTest(
      name = "does not trigger a pipeline that has an enabled bitbucket trigger with missing {1}")
  @MethodSource("missingFieldBitbucketParams")
  void doesNotTriggerPipelineWithMissingBitbucketField(Trigger trigger, String field)
      throws TimeoutException {
    GitEvent event = createGitEvent("bitbucket");
    Pipeline goodPipeline = createPipelineWith(enabledBitBucketTrigger());
    Pipeline badPipeline = createPipelineWith(trigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(List.of(badPipeline, goodPipeline));

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).hasSize(1);
    assertThat(matchingPipelines.get(0).getId()).isEqualTo(goodPipeline.getId());
  }

  private static Stream<Arguments> branchMatchParams() {
    return Stream.of(
        Arguments.of("whatever", null, "no branch set in trigger"),
        Arguments.of("whatever", "", "empty string in trigger"),
        Arguments.of("master", "master", "branches are identical"),
        Arguments.of("ref/origin/master", "ref/origin/master", "branches have slashes"),
        Arguments.of("regex12345", "regex.*", "branches match pattern"));
  }

  @ParameterizedTest(name = "triggers events on branch when {2}")
  @MethodSource("branchMatchParams")
  void triggersEventsOnBranch(String eventBranch, String triggerBranch, String description)
      throws TimeoutException {
    GitEvent gitEvent = createGitEvent("stash");
    gitEvent.getContent().setBranch(eventBranch);
    Trigger trigger = enabledStashTrigger().atBranch(triggerBranch);
    Pipeline pipeline = createPipelineWith(trigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(gitEvent, pipelines);

    assertThat(matchingPipelines).hasSize(1);
    assertThat(matchingPipelines.get(0).getApplication()).isEqualTo(pipeline.getApplication());
    assertThat(matchingPipelines.get(0).getName()).isEqualTo(pipeline.getName());
  }

  private static Stream<Arguments> branchMismatchParams() {
    return Stream.of(
        Arguments.of("master", "featureBranch"), Arguments.of("regex12345", "not regex.*"));
  }

  @ParameterizedTest
  @MethodSource("branchMismatchParams")
  void doesNotTriggerEventsOnBranchOnMismatchBranch(String eventBranch, String triggerBranch)
      throws TimeoutException {
    GitEvent gitEvent = createGitEvent("stash");
    gitEvent.getContent().setBranch(eventBranch);
    Trigger trigger = enabledStashTrigger().atBranch(triggerBranch);
    Pipeline pipeline = createPipelineWith(trigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(gitEvent, pipelines);

    assertThat(matchingPipelines).isEmpty();
  }

  private static Stream<Arguments> githubSignatureParams() {
    return Stream.of(
        Arguments.of(null, null, 1),
        Arguments.of("foo", null, 0),
        Arguments.of(null, "foo", 0), // No secret defined in trigger
        Arguments.of("foo", "foo", 0), // Signatures don't match
        Arguments.of(
            "foo",
            "67af18bbedab68252b01902ac0a8d7095ca93692",
            1) // Signatures match! Generated by http://www.freeformatter.com/hmac-generator.html
        );
  }

  @ParameterizedTest
  @MethodSource("githubSignatureParams")
  void computesAndComparesGitHubSignatureIfAvailable(String secret, String signature, int callCount)
      throws TimeoutException {
    GitEvent gitEvent = createGitEvent("github");
    gitEvent.setRawContent("toBeHashed");
    gitEvent.getDetails().setSource("github");
    if (signature != null) {
      gitEvent
          .getDetails()
          .getRequestHeaders()
          .put("X-Hub-Signature", List.of("sha1=" + signature));
    }

    Trigger trigger = enabledGithubTrigger().atSecret(secret).atBranch("master");

    Pipeline pipeline = createPipelineWith(trigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(gitEvent, pipelines);

    assertThat(matchingPipelines).hasSize(callCount);
  }

  private static Stream<Arguments> githubSignatureSharedSecretParams() {
    return Stream.of(
        Arguments.of(null, null, 1),
        Arguments.of("foo", null, 0),
        Arguments.of(null, "foo", 0), // No shared secret defined
        Arguments.of("foo", "foo", 0), // Signatures don't match
        Arguments.of("foo", "67af18bbedab68252b01902ac0a8d7095ca93692", 1) // Signatures match!
        );
  }

  @ParameterizedTest
  @MethodSource("githubSignatureSharedSecretParams")
  void computesAndComparesGitHubSignatureIfAvailableWithASharedSecret(
      String secret, String signature, int callCount) throws TimeoutException {
    GitEvent gitEvent = createGitEvent("github");
    gitEvent.setRawContent("toBeHashed");
    gitEvent.getDetails().setSource("github");
    if (signature != null) {
      gitEvent
          .getDetails()
          .getRequestHeaders()
          .put("X-Hub-Signature", List.of("sha1=" + signature));
    }

    Trigger trigger = enabledGithubTrigger().atBranch("master");

    Pipeline pipeline = createPipelineWith(trigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);
    when(pipelineTriggerConfiguration.getGitSharedSecret()).thenReturn(secret);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(gitEvent, pipelines);

    assertThat(matchingPipelines).hasSize(callCount);
  }

  private static Stream<Arguments> githubActionEventsParams() {
    return Stream.of(
        Arguments.of("pull_request:reopened", List.of("pull_request:opened")),
        Arguments.of("push:push", List.of("pull_request:closed")));
  }

  @ParameterizedTest
  @MethodSource("githubActionEventsParams")
  void doNotMatchOnGithubActionEventsAsAGithubTriggerType(
      String eventAction, List<String> triggerEvents) throws TimeoutException {
    GitEvent gitEvent = createGitEvent("github");
    gitEvent.getContent().setAction(eventAction);
    Trigger trigger = enabledGithubTrigger().atBranch("master").withEvents(triggerEvents);
    Pipeline pipeline = createPipelineWith(trigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(gitEvent, pipelines);

    assertThat(matchingPipelines).isEmpty();
  }

  private static Stream<Arguments> notGithubActionParams() {
    return Stream.of(
        Arguments.of(
            "pull_request:reopened",
            List.of("pull_request:opened", "pull_request:closed", "pull_request:reopened")),
        Arguments.of("pull_request:reopened", List.of()));
  }

  @ParameterizedTest
  @MethodSource("notGithubActionParams")
  void doesTriggerEventsWhenItsNotAGithubAction(String eventAction, List<String> triggerEvents)
      throws TimeoutException {
    GitEvent gitEvent = createGitEvent("github");
    gitEvent.getContent().setAction(eventAction);
    Trigger trigger = enabledGithubTrigger().atBranch("master").withEvents(triggerEvents);
    Pipeline pipeline = createPipelineWith(trigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(gitEvent, pipelines);

    assertThat(matchingPipelines).hasSize(1);
    assertThat(
            matchingPipelines.get(0).getTriggers().get(0).getEvents().contains(eventAction)
                || matchingPipelines.get(0).getTriggers().get(0).getEvents().isEmpty())
        .isTrue();
  }
}
