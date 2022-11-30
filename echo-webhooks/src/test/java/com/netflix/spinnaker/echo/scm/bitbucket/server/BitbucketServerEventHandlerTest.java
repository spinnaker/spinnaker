/*
 * Copyright 2022 Armory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.echo.scm.bitbucket.server;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.api.events.Metadata;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.echo.scm.BitbucketWebhookEventHandler;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class BitbucketServerEventHandlerTest {

  @Test
  void testHandleBitbucketServerPrOpenedEvent() throws IOException {
    File file = getPayloadFile("/bitbucket-server/bitbucket_server_pr_opened_payload.json");
    String rawPayload = new String(Files.readAllBytes(file.toPath()));

    ObjectMapper mapper = EchoObjectMapper.getInstance();
    Map<String, Object> payload = mapper.readValue(rawPayload, new TypeReference<>() {});

    Event event = new Event();
    Metadata metadata = new Metadata();
    metadata.setType("git");
    metadata.setSource("bitbucket");
    event.details = metadata;
    event.rawContent = rawPayload;
    event.payload = payload;
    event.content = payload;
    event.content.put("event_type", "pr:opened");

    BitbucketServerEventHandler serverEventHandler = new BitbucketServerEventHandler();

    BitbucketWebhookEventHandler handler = new BitbucketWebhookEventHandler(serverEventHandler);

    assertThatCode(() -> handler.handle(event, payload, HttpHeaders.EMPTY))
        .doesNotThrowAnyException();

    assertThat(event.content)
        .contains(
            entry("action", "pr:opened"),
            entry("repoProject", "RT"),
            entry("slug", "repo-test"),
            entry("hash", "1eb0b0f6e1725c4040f2605e1d58c9f9d2095658"),
            entry("branch", "feature-branch"));
  }

  @Test
  void testBitbucketServerRefsChangedEvent() throws IOException {
    File file = getPayloadFile("/bitbucket-server/bitbucket_server_repo_refs_changed_payload.json");
    String rawPayload = new String(Files.readAllBytes(file.toPath()));

    ObjectMapper mapper = EchoObjectMapper.getInstance();
    Map<String, Object> payload = mapper.readValue(rawPayload, new TypeReference<>() {});

    Event event = new Event();
    Metadata metadata = new Metadata();
    metadata.setType("git");
    metadata.setSource("bitbucket");
    event.details = metadata;
    event.rawContent = rawPayload;
    event.payload = payload;
    event.content = payload;
    event.content.put("event_type", "repo:refs_changed");

    BitbucketServerEventHandler serverEventHandler = new BitbucketServerEventHandler();

    BitbucketWebhookEventHandler handler = new BitbucketWebhookEventHandler(serverEventHandler);

    assertThatCode(() -> handler.handle(event, payload, HttpHeaders.EMPTY))
        .doesNotThrowAnyException();

    assertThat(event.content)
        .contains(
            entry("action", "repo:refs_changed"),
            entry("repoProject", "RT"),
            entry("slug", "repo-test"),
            entry("hash", "9065c394975cf8750a83abb6e54ba27245a38926"),
            entry("branch", "feature-branch"));
  }

  @Test
  void testBitbucketServerFromRefUpdatedEvent() throws IOException {
    File file =
        getPayloadFile("/bitbucket-server/bitbucket_server_pr_from_ref_updated_payload.json");
    String rawPayload = new String(Files.readAllBytes(file.toPath()));

    ObjectMapper mapper = EchoObjectMapper.getInstance();
    Map<String, Object> payload = mapper.readValue(rawPayload, new TypeReference<>() {});

    Event event = new Event();
    Metadata metadata = new Metadata();
    metadata.setType("git");
    metadata.setSource("bitbucket");
    event.details = metadata;
    event.rawContent = rawPayload;
    event.payload = payload;
    event.content = payload;
    event.content.put("event_type", "pr:from_ref_updated");

    BitbucketServerEventHandler serverEventHandler = new BitbucketServerEventHandler();

    BitbucketWebhookEventHandler handler = new BitbucketWebhookEventHandler(serverEventHandler);

    assertThatCode(() -> handler.handle(event, payload, HttpHeaders.EMPTY))
        .doesNotThrowAnyException();

    assertThat(event.content)
        .contains(
            entry("action", "pr:from_ref_updated"),
            entry("repoProject", "RT"),
            entry("slug", "repo-test"),
            entry("hash", "9065c394975cf8750a83abb6e54ba27245a38926"),
            entry("branch", "feature-branch"));
  }

  @Test
  void testHandleBitbucketServerPrMergedEvent() throws IOException {
    File file = getPayloadFile("/bitbucket-server/bitbucket_server_pr_merged_payload.json");
    String rawPayload = new String(Files.readAllBytes(file.toPath()));

    ObjectMapper mapper = EchoObjectMapper.getInstance();
    Map<String, Object> payload = mapper.readValue(rawPayload, new TypeReference<>() {});

    Event event = new Event();
    Metadata metadata = new Metadata();
    metadata.setType("git");
    metadata.setSource("bitbucket");
    event.details = metadata;
    event.rawContent = rawPayload;
    event.payload = payload;
    event.content = payload;
    event.content.put("event_type", "pr:merged");

    BitbucketServerEventHandler serverEventHandler = new BitbucketServerEventHandler();

    BitbucketWebhookEventHandler handler = new BitbucketWebhookEventHandler(serverEventHandler);

    assertThatCode(() -> handler.handle(event, payload, HttpHeaders.EMPTY))
        .doesNotThrowAnyException();

    assertThat(event.content)
        .contains(
            entry("action", "pr:merged"),
            entry("repoProject", "RT"),
            entry("slug", "repo-test"),
            entry("hash", "3e325b06e6f13b948dbb33ae8177cfa78a043ac5"),
            entry("branch", "main"));
  }

  @Test
  void testHandleBitbucketServerPrDeletedEvent() throws IOException {
    File file = getPayloadFile("/bitbucket-server/bitbucket_server_pr_deleted_payload.json");
    String rawPayload = new String(Files.readAllBytes(file.toPath()));

    ObjectMapper mapper = EchoObjectMapper.getInstance();
    Map<String, Object> payload = mapper.readValue(rawPayload, new TypeReference<>() {});

    Event event = new Event();
    Metadata metadata = new Metadata();
    metadata.setType("git");
    metadata.setSource("bitbucket");
    event.details = metadata;
    event.rawContent = rawPayload;
    event.payload = payload;
    event.content = payload;
    event.content.put("event_type", "pr:deleted");

    BitbucketServerEventHandler serverEventHandler = new BitbucketServerEventHandler();

    BitbucketWebhookEventHandler handler = new BitbucketWebhookEventHandler(serverEventHandler);

    assertThatCode(() -> handler.handle(event, payload, HttpHeaders.EMPTY))
        .doesNotThrowAnyException();

    assertThat(event.content)
        .contains(
            entry("action", "pr:deleted"),
            entry("repoProject", "RT"),
            entry("slug", "repo-test"),
            entry("hash", "a87ec312573d13fa35b9d877bbf03be0385ae314"),
            entry("branch", "my-feature-branch"));
  }

  @Test
  void testHandleBitbucketServerPrDeclinedEvent() throws IOException {
    File file = getPayloadFile("/bitbucket-server/bitbucket_server_pr_declined_payload.json");
    String rawPayload = new String(Files.readAllBytes(file.toPath()));

    ObjectMapper mapper = EchoObjectMapper.getInstance();
    Map<String, Object> payload = mapper.readValue(rawPayload, new TypeReference<>() {});

    Event event = new Event();
    Metadata metadata = new Metadata();
    metadata.setType("git");
    metadata.setSource("bitbucket");
    event.details = metadata;
    event.rawContent = rawPayload;
    event.payload = payload;
    event.content = payload;
    event.content.put("event_type", "pr:declined");

    BitbucketServerEventHandler serverEventHandler = new BitbucketServerEventHandler();

    BitbucketWebhookEventHandler handler = new BitbucketWebhookEventHandler(serverEventHandler);

    assertThatCode(() -> handler.handle(event, payload, HttpHeaders.EMPTY))
        .doesNotThrowAnyException();

    assertThat(event.content)
        .contains(
            entry("action", "pr:declined"),
            entry("repoProject", "RT"),
            entry("slug", "repo-test"),
            entry("hash", "a87ec312573d13fa35b9d877bbf03be0385ae314"),
            entry("branch", "my-feature-branch"));
  }

  private File getPayloadFile(String name) {
    return new File(Objects.requireNonNull(getClass().getResource(name)).getFile());
  }
}
