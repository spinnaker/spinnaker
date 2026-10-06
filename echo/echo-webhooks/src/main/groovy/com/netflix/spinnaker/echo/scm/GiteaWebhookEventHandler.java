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

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Handles webhooks posted by Gitea (and Forgejo) to {@code /webhooks/git/gitea}. Push, branch/tag
 * create/delete and pull request events are supported; the event kind is detected from the payload,
 * as it is for GitHub.
 *
 * <p>The resulting actions use the same shape as the GitHub handler: {@code push}, {@code
 * pull_request:<action>}, and {@code branch:<create|delete>} / {@code tag:<create|delete>}.
 */
@Component
@Slf4j
public class GiteaWebhookEventHandler implements GitWebhookHandler {
  private static final String HEADS_PREFIX = "refs/heads/";

  private final ObjectMapper objectMapper;

  public GiteaWebhookEventHandler() {
    this.objectMapper = EchoObjectMapper.getInstance();
  }

  @Override
  public boolean handles(String source) {
    return "gitea".equals(source);
  }

  @Override
  public boolean shouldSendEvent(Event event) {
    return true;
  }

  @Override
  public void handle(Event event, Map postedEvent, HttpHeaders headers) {
    GiteaWebhookEvent webhookEvent =
        objectMapper.convertValue(postedEvent, GiteaWebhookEvent.class);

    Map<String, String> results = new HashMap<>();
    results.put("repoProject", webhookEvent.repoProject());
    results.put(
        "slug", webhookEvent.repository == null ? "" : orEmpty(webhookEvent.repository.name));

    if (webhookEvent.pullRequest != null) {
      GiteaPullRequest pullRequest = webhookEvent.pullRequest;
      GiteaRef head = pullRequest.head == null ? new GiteaRef() : pullRequest.head;
      results.put("hash", orEmpty(head.sha));
      results.put("branch", orEmpty(head.ref));
      results.put("action", "pull_request:" + orEmpty(webhookEvent.action));
      results.put("number", String.valueOf(pullRequest.number));
      results.put("state", orEmpty(pullRequest.state));
      results.put("title", orEmpty(pullRequest.title));
      if (pullRequest.draft != null) {
        results.put("draft", String.valueOf(pullRequest.draft));
      }
    } else if (webhookEvent.refType != null) {
      // create/delete of a branch or tag; the kind of event is only present in the headers.
      String giteaEvent = StringUtils.defaultIfEmpty(headers.getFirst("x-gitea-event"), "create");
      results.put("hash", orEmpty(webhookEvent.sha));
      results.put("branch", orEmpty(webhookEvent.ref).replace(HEADS_PREFIX, ""));
      results.put("action", webhookEvent.refType + ":" + giteaEvent);
    } else {
      results.put("hash", orEmpty(webhookEvent.after));
      results.put("branch", orEmpty(webhookEvent.ref).replace(HEADS_PREFIX, ""));
      results.put("action", "push");
    }

    event.content.putAll(results);

    log.info(
        "Gitea Webhook event received: {} {} {} {} {}",
        kv("action", results.get("action")),
        kv("project", results.get("repoProject")),
        kv("slug", results.get("slug")),
        kv("branch", results.get("branch")),
        kv("hash", results.get("hash")));
  }

  private static String orEmpty(String value) {
    return value == null ? "" : value;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class GiteaWebhookEvent {
    private String action;
    private String after;
    private String ref;
    private String sha;

    @JsonProperty("ref_type")
    private String refType;

    @JsonProperty("pull_request")
    private GiteaPullRequest pullRequest;

    private GiteaRepository repository;

    /** Owner login, falling back to the {@code owner/} prefix of the full repository name. */
    String repoProject() {
      return Optional.ofNullable(repository)
          .map(
              r -> {
                if (r.owner != null && StringUtils.isNotEmpty(r.owner.login)) {
                  return r.owner.login;
                }
                if (r.owner != null && StringUtils.isNotEmpty(r.owner.username)) {
                  return r.owner.username;
                }
                return StringUtils.substringBefore(orEmpty(r.fullName), "/");
              })
          .orElse("");
    }
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class GiteaRepository {
    private String name;
    private GiteaOwner owner;

    @JsonProperty("full_name")
    private String fullName;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class GiteaOwner {
    private String login;
    private String username;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class GiteaPullRequest {
    private int number;
    private String state;
    private String title;
    private Boolean draft;
    private GiteaRef head;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class GiteaRef {
    private String ref;
    private String sha;
  }
}
