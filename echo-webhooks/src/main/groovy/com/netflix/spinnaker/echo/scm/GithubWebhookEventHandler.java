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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.model.Event;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class GithubWebhookEventHandler implements GitWebhookHandler {

  private ObjectMapper objectMapper;

  public GithubWebhookEventHandler() {
    this.objectMapper =
        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  public boolean handles(String source) {
    return "github".equals(source);
  }

  public boolean shouldSendEvent(Event event) {
    return !event.content.containsKey("hook_id");
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

    GithubWebhookEvent githubWebhookEvent =
        objectMapper.convertValue(postedEvent, GithubWebhookEvent.class);

    event.content.put("hash", githubWebhookEvent.after);
    if (githubWebhookEvent.ref == null) {
      event.content.put("branch", "");
    } else {
      event.content.put("branch", githubWebhookEvent.ref.replace("refs/heads/", ""));
    }
    event.content.put("repoProject", githubWebhookEvent.repository.owner.name);
    event.content.put("slug", githubWebhookEvent.repository.name);
  }

  @Data
  private static class GithubWebhookEvent {
    String after;
    String ref;
    GithubWebhookRepository repository;
  }

  @Data
  private static class GithubWebhookRepository {
    GithubOwner owner;
    String name;
  }

  @Data
  private static class GithubOwner {
    String name;
  }
}
