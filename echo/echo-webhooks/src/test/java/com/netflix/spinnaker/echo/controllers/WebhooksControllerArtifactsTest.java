/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.echo.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.artifacts.ArtifactExtractor;
import com.netflix.spinnaker.echo.events.EventPropagator;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.echo.scm.BitbucketWebhookEventHandler;
import com.netflix.spinnaker.echo.scm.GiteaWebhookEventHandler;
import com.netflix.spinnaker.echo.scm.GithubWebhookEventHandler;
import com.netflix.spinnaker.echo.scm.GitlabWebhookEventHandler;
import com.netflix.spinnaker.echo.scm.ScmWebhookHandler;
import com.netflix.spinnaker.echo.scm.StashWebhookEventHandler;
import com.netflix.spinnaker.echo.scm.bitbucket.server.BitbucketServerEventHandler;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;

/**
 * The artifacts of a git webhook come from the artifact extractors, so artifacts the sender puts in
 * the payload must not reach the pipeline triggers.
 */
class WebhooksControllerArtifactsTest {
  // Carries the fields that each of the git handlers reads, along with an artifacts key.
  private static final String PAYLOAD_WITH_ARTIFACTS =
      """
      {"artifacts": [{"type": "github/file", "name": "x", "reference": "https://other.example.com/x"}],
       "ref": "refs/heads/main", "after": "abc123", "object_kind": "push",
       "project": {"name": "echo", "namespace": "spinnaker"},
       "refChanges": [{"toHash": "abc123", "refId": "refs/heads/main"}],
       "repository": {"name": "echo", "slug": "echo", "full_name": "spinnaker/echo",
                      "project": {"key": "spinnaker"}, "owner": {"login": "spinnaker"}}}
      """;

  private final WebhooksController controller = new WebhooksController();
  private final EventPropagator propagator = mock(EventPropagator.class);
  private final ArtifactExtractor artifactExtractor = mock(ArtifactExtractor.class);
  private final Artifact derived =
      Artifact.builder().type("github/file").name("derived").reference("https://derived").build();

  @BeforeEach
  void setUp() {
    when(artifactExtractor.extractArtifacts(any(), any(), any())).thenReturn(List.of(derived));
    controller.setMapper(EchoObjectMapper.getInstance());
    controller.setPropagator(propagator);
    controller.setArtifactExtractor(artifactExtractor);
    controller.setScmWebhookHandler(
        new ScmWebhookHandler(
            List.of(
                new GithubWebhookEventHandler(),
                new GitlabWebhookEventHandler(),
                new BitbucketWebhookEventHandler(new BitbucketServerEventHandler()),
                new StashWebhookEventHandler(),
                new GiteaWebhookEventHandler())));
  }

  @ParameterizedTest
  @ValueSource(strings = {"github", "gitlab", "bitbucket", "stash", "gitea"})
  void ignoresArtifactsSuppliedInAGitPayload(String source) {
    controller.forwardEvent("git", source, PAYLOAD_WITH_ARTIFACTS, new HttpHeaders());

    assertThat(propagatedEvent().content).containsEntry("artifacts", List.of(derived));
  }

  @Test
  void doesNotPassSuppliedArtifactsToTheExtractor() {
    controller.forwardEvent("git", "github", PAYLOAD_WITH_ARTIFACTS, new HttpHeaders());

    ArgumentCaptor<java.util.Map> payload = ArgumentCaptor.forClass(java.util.Map.class);
    verify(artifactExtractor).extractArtifacts(eq("git"), eq("github"), payload.capture());
    assertThat(payload.getValue()).doesNotContainKey("artifacts");
  }

  @Test
  void leavesNoArtifactsWhenTheExtractorFindsNone() {
    when(artifactExtractor.extractArtifacts(any(), any(), any())).thenReturn(List.of());

    controller.forwardEvent("git", "github", PAYLOAD_WITH_ARTIFACTS, new HttpHeaders());

    assertThat(propagatedEvent().content.get("artifacts")).isEqualTo(List.of());
  }

  private Event propagatedEvent() {
    ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
    verify(propagator).processEvent(captor.capture());
    return captor.getValue();
  }
}
