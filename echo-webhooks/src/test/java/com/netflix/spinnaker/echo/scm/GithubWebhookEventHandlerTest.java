/*
 * Copyright 2022 Schibsted ASA.
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

package com.netflix.spinnaker.echo.scm;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.api.events.Metadata;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class GithubWebhookEventHandlerTest {

  @Test
  void canHandlePushPayload() throws IOException {
    File file = new File(getClass().getResource("/github_push_payload.json").getFile());
    String rawPayload = new String(Files.readAllBytes(file.toPath()));

    ObjectMapper mapper = EchoObjectMapper.getInstance();
    Map<String, Object> payload = mapper.readValue(rawPayload, new TypeReference<>() {});

    Event event = new Event();
    Metadata metadata = new Metadata();
    metadata.setType("git");
    metadata.setSource("github");
    event.details = metadata;
    event.rawContent = rawPayload;
    event.payload = payload;
    event.content = payload;

    GithubWebhookEventHandler handler = new GithubWebhookEventHandler();
    assertThatCode(() -> handler.handle(event, payload, HttpHeaders.EMPTY))
        .doesNotThrowAnyException();

    assertThat(event.content)
        .contains(
            entry("repoProject", "jervi"),
            entry("slug", "expert-waffle"),
            entry("hash", "da68b0f38d58d68196651e808317bfae708fa9f0"),
            entry("branch", "main"),
            entry("action", "push:push"));
  }

  @Test
  void canHandlePrPayload() throws IOException {
    File file = new File(getClass().getResource("/github_pr_payload.json").getFile());
    String rawPayload = new String(Files.readAllBytes(file.toPath()));

    ObjectMapper mapper = EchoObjectMapper.getInstance();
    Map<String, Object> payload = mapper.readValue(rawPayload, new TypeReference<>() {});

    Event event = new Event();
    Metadata metadata = new Metadata();
    metadata.setType("git");
    metadata.setSource("github");
    event.details = metadata;
    event.rawContent = rawPayload;
    event.payload = payload;
    event.content = payload;

    HttpHeaders headers = new HttpHeaders();
    headers.add("x-github-event", "pull_request");

    GithubWebhookEventHandler handler = new GithubWebhookEventHandler();
    assertThatCode(() -> handler.handle(event, payload, headers)).doesNotThrowAnyException();

    assertThat(event.content)
        .contains(
            entry("repoProject", "jervi"),
            entry("slug", "expert-waffle"),
            entry("hash", "ba3b59b3cf776e3eda0b17a8094ce78c2f2e498c"),
            entry("branch", "add-waffle-recipe"),
            entry("action", "pull_request:opened"),
            entry("number", "1"),
            entry("draft", "false"),
            entry("state", "open"),
            entry("title", "Add waffle recipe"));
  }

  @Test
  void canHandleBranchPayload() throws IOException {
    File file = new File(getClass().getResource("/github_branch_payload.json").getFile());
    String rawPayload = new String(Files.readAllBytes(file.toPath()));

    ObjectMapper mapper = EchoObjectMapper.getInstance();
    Map<String, Object> payload = mapper.readValue(rawPayload, new TypeReference<>() {});

    Event event = new Event();
    Metadata metadata = new Metadata();
    metadata.setType("git");
    metadata.setSource("github");
    event.details = metadata;
    event.rawContent = rawPayload;
    event.payload = payload;
    event.content = payload;

    HttpHeaders headers = new HttpHeaders();
    headers.add("x-github-event", "delete");

    GithubWebhookEventHandler handler = new GithubWebhookEventHandler();
    assertThatCode(() -> handler.handle(event, payload, headers)).doesNotThrowAnyException();

    assertThat(event.content)
        .contains(
            entry("repoProject", "jervi"),
            entry("slug", "expert-waffle"),
            entry("hash", ""),
            entry("branch", "add-waffle-recipe"),
            entry("action", "branch:delete"));
  }
}
