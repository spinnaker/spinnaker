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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.artifacts.ArtifactExtractor;
import com.netflix.spinnaker.echo.events.EventPropagator;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.echo.scm.GiteaWebhookEventHandler;
import com.netflix.spinnaker.echo.scm.GithubWebhookEventHandler;
import com.netflix.spinnaker.echo.scm.ScmWebhookHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;

/**
 * The headers handed to SCM handlers must be looked up case-insensitively. Wrapping a plain map in
 * {@code new HttpHeaders(map)} makes {@code getFirst("x-github-event")} case-sensitive, so it
 * returned null for the {@code X-GitHub-Event} header Spring received.
 */
class WebhooksControllerGitHeadersTest {
  private final WebhooksController controller = new WebhooksController();
  private final EventPropagator propagator = mock(EventPropagator.class);

  @BeforeEach
  void setUp() {
    ArtifactExtractor artifactExtractor = mock(ArtifactExtractor.class);
    when(artifactExtractor.extractArtifacts(any(), any(), any())).thenReturn(List.of());
    controller.setMapper(EchoObjectMapper.getInstance());
    controller.setPropagator(propagator);
    controller.setArtifactExtractor(artifactExtractor);
    controller.setScmWebhookHandler(
        new ScmWebhookHandler(
            List.of(new GithubWebhookEventHandler(), new GiteaWebhookEventHandler())));
  }

  @Test
  void readsTheGitHubEventHeaderForBranchEvents() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-GitHub-Event", "delete");

    controller.forwardEvent("git", "github", branchPayload(), headers);

    assertThat(propagatedEvent().content).containsEntry("action", "branch:delete");
  }

  @Test
  void readsTheGiteaEventHeaderForBranchEvents() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Gitea-Event", "delete");

    controller.forwardEvent("git", "gitea", branchPayload(), headers);

    assertThat(propagatedEvent().content).containsEntry("action", "branch:delete");
  }

  private Event propagatedEvent() {
    ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
    verify(propagator).processEvent(captor.capture());
    return captor.getValue();
  }

  private static String branchPayload() {
    return """
        {"ref": "feature", "ref_type": "branch", "sha": "abc123",
         "repository": {"name": "echo", "full_name": "spinnaker/echo", "owner": {"login": "spinnaker"}}}
        """;
  }
}
