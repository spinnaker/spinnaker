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

package com.netflix.spinnaker.echo.scm;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.api.events.Metadata;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import tools.jackson.core.type.TypeReference;

class GiteaWebhookEventHandlerTest {
  private final GiteaWebhookEventHandler handler = new GiteaWebhookEventHandler();

  private Event handle(String json, HttpHeaders headers) throws Exception {
    Map<String, Object> payload =
        EchoObjectMapper.getInstance().readValue(json, new TypeReference<>() {});
    Event event = new Event();
    Metadata metadata = new Metadata();
    metadata.setType("git");
    metadata.setSource("gitea");
    event.details = metadata;
    event.payload = payload;
    event.content = payload;
    handler.handle(event, payload, headers);
    return event;
  }

  @Test
  void handlesOnlyGitea() {
    assertThat(handler.handles("gitea")).isTrue();
    assertThat(handler.handles("github")).isFalse();
  }

  @Test
  void handlesAPushEvent() throws Exception {
    Event event =
        handle(
            """
            {
              "ref": "refs/heads/main",
              "after": "da1560886d4f094c3e6c9ef40349f7d38b5d27d7",
              "repository": {
                "name": "echo",
                "full_name": "spinnaker/echo",
                "owner": {"login": "spinnaker", "username": "spinnaker"}
              }
            }
            """,
            HttpHeaders.EMPTY);

    assertThat(event.content)
        .contains(
            Map.entry("hash", "da1560886d4f094c3e6c9ef40349f7d38b5d27d7"),
            Map.entry("repoProject", "spinnaker"),
            Map.entry("slug", "echo"),
            Map.entry("branch", "main"),
            Map.entry("action", "push"));
  }

  @Test
  void fallsBackToTheFullNamePrefixWhenThereIsNoOwner() throws Exception {
    Event event =
        handle(
            """
            {"ref": "refs/heads/main", "after": "abc",
             "repository": {"name": "echo", "full_name": "spinnaker/echo"}}
            """,
            HttpHeaders.EMPTY);

    assertThat(event.content).contains(Map.entry("repoProject", "spinnaker"));
  }

  @Test
  void handlesAPullRequestEvent() throws Exception {
    Event event =
        handle(
            """
            {
              "action": "synchronized",
              "number": 7,
              "pull_request": {
                "number": 7,
                "state": "open",
                "title": "Add a thing",
                "head": {"ref": "feature/thing", "sha": "1111111111111111111111111111111111111111"}
              },
              "repository": {
                "name": "echo",
                "full_name": "spinnaker/echo",
                "owner": {"login": "spinnaker"}
              }
            }
            """,
            HttpHeaders.EMPTY);

    assertThat(event.content)
        .contains(
            Map.entry("hash", "1111111111111111111111111111111111111111"),
            Map.entry("branch", "feature/thing"),
            Map.entry("repoProject", "spinnaker"),
            Map.entry("slug", "echo"),
            Map.entry("action", "pull_request:synchronized"),
            Map.entry("number", "7"),
            Map.entry("state", "open"),
            Map.entry("title", "Add a thing"))
        .doesNotContainKey("draft");
  }

  @ParameterizedTest
  @CsvSource({
    "branch, create, branch:create",
    "branch, delete, branch:delete",
    "tag, create, tag:create"
  })
  void handlesBranchAndTagCreateAndDeleteEvents(
      String refType, String eventHeader, String expectedAction) throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Gitea-Event", eventHeader);

    Event event =
        handle(
            """
            {"ref": "v1.0", "ref_type": "%s", "sha": "abc123",
             "repository": {"name": "echo", "full_name": "spinnaker/echo", "owner": {"login": "spinnaker"}}}
            """
                .formatted(refType),
            headers);

    assertThat(event.content)
        .contains(
            Map.entry("action", expectedAction),
            Map.entry("hash", "abc123"),
            Map.entry("branch", "v1.0"));
  }
}
