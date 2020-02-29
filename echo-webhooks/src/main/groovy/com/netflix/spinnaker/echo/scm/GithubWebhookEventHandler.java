/*
 * Copyright 2019 Netflix, Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.echo.scm.github.GithubPullRequestEvent;
import com.netflix.spinnaker.echo.scm.github.GithubPushEvent;
import com.netflix.spinnaker.echo.scm.github.GithubWebhookEvent;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class GithubWebhookEventHandler implements GitWebhookHandler {

  private ObjectMapper objectMapper;

  public GithubWebhookEventHandler() {
    this.objectMapper = EchoObjectMapper.getInstance();
  }

  public boolean handles(String source) {
    return "github".equals(source);
  }

  public boolean shouldSendEvent(Event event) {
    if (event.content.containsKey("hook_id")) {
      return false;
    }

    return true;
  }

  public void handle(Event event, Map postedEvent) {
    if (!shouldSendEvent(event)) {
      log.info(
          "Webhook ping received from github {} {} {}",
          kv(
              "hook_id",
              event.content.get("hook_id"),
              kv(
                      "repository",
                      ((Map<String, Object>) event.content.get("repository")).get("full_name"))
                  .toString()));
      return;
    }

    GithubWebhookEvent webhookEvent = null;
    String githubEvent = "";

    // TODO: Detect based on header rather than body key - depends on Gate properly passing headers
    // `x-github-event`: `push` or `pull_request`
    if (event.content.containsKey("pull_request")) {
      webhookEvent = objectMapper.convertValue(event.content, GithubPullRequestEvent.class);
      githubEvent = "pull_request";
    } else {
      // Default to 'Push'
      webhookEvent = objectMapper.convertValue(event.content, GithubPushEvent.class);
      githubEvent = "push";
    }

    String fullRepoName = webhookEvent.getFullRepoName(event, postedEvent);
    Map<String, String> results = new HashMap<>();
    results.put("repoProject", webhookEvent.getRepoProject(event, postedEvent));
    results.put("slug", webhookEvent.getSlug(event, postedEvent));
    results.put("hash", webhookEvent.getHash(event, postedEvent));
    results.put("branch", webhookEvent.getBranch(event, postedEvent));
    results.put(
        "action", githubEvent.concat(":").concat(webhookEvent.getAction(event, postedEvent)));
    event.content.putAll(results);

    log.info(
        "Github Webhook event received: {} {} {} {} {} {}",
        kv("githubEvent", githubEvent),
        kv("repository", fullRepoName),
        kv("project", results.get("repoProject")),
        kv("slug", results.get("slug")),
        kv("branch", results.get("branch")),
        kv("hash", results.get("hash")));
  }
}
