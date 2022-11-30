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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.echo.scm.bitbucket.server.BitbucketServerEventHandler;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BitbucketWebhookEventHandler implements GitWebhookHandler {

  private final ObjectMapper objectMapper;
  private final BitbucketServerEventHandler bitbucketServerEventHandler;

  public BitbucketWebhookEventHandler(BitbucketServerEventHandler bitbucketServerEventHandler) {
    this.objectMapper = EchoObjectMapper.getInstance();
    this.bitbucketServerEventHandler = bitbucketServerEventHandler;
  }

  public boolean handles(String source) {
    return "bitbucket".equals(source);
  }

  public boolean shouldSendEvent(Event event) {
    if (event.rawContent.isEmpty()) {
      return false;
    }

    if (event.content.containsKey("hash")
        && event.content.get("hash").toString().startsWith("000000000")) {
      return false;
    }

    return true;
  }

  public void handle(Event event, Map postedEvent, HttpHeaders headers) {
    if (event.rawContent.isEmpty() || !event.content.containsKey("event_type")) {
      log.info("Handling Bitbucket Server ping.");
      return;
    }

    if (looksLikeBitbucketCloud(event)) {
      handleBitbucketCloudEvent(event, postedEvent);
    } else if (bitbucketServerEventHandler.looksLikeBitbucketServer(event)) {
      bitbucketServerEventHandler.handleBitbucketServerEvent(event);
    } else {
      // Could not determine what type of Bitbucket event this was.
      log.info(
          "Could not determine Bitbucket type {}",
          kv("event_type", event.content.get("event_type")));
      return;
    }

    String fullRepoName = getFullRepoName(event);

    if (StringUtils.isNotEmpty(fullRepoName)) {
      log.info(
          "Webhook event received {} {} {} {} {} {}",
          kv("type", "git"),
          kv("event_type", event.content.get("event_type").toString()),
          kv(
              "hook_id",
              event.content.containsKey("hook_id") ? event.content.get("hook_id").toString() : ""),
          kv("repository", fullRepoName),
          kv(
              "request_id",
              event.content.containsKey("request_id")
                  ? event.content.get("request_id").toString()
                  : ""),
          kv("branch", event.content.get("branch")));
    } else {
      log.info(
          "Webhook event received {} {} {} {} {}",
          kv("type", "git"),
          kv("event_type", event.content.get("event_type").toString()),
          kv(
              "hook_id",
              event.content.containsKey("hook_id") ? event.content.get("hook_id").toString() : ""),
          kv(
              "request_id",
              event.content.containsKey("request_id")
                  ? event.content.get("request_id").toString()
                  : ""),
          kv(
              "branch",
              event.content.containsKey("branch") ? event.content.get("branch").toString() : ""));
    }
  }

  private boolean looksLikeBitbucketCloud(Event event) {
    String eventType = event.content.get("event_type").toString();
    return (eventType.equals("repo:push") || eventType.equals("pullrequest:fulfilled"));
  }

  private String getFullRepoName(Event event) {
    if (looksLikeBitbucketCloud(event)) {
      return ((Map<String, Object>) event.content.get("repository")).get("full_name").toString();
    }

    String eventType = event.content.get("event_type").toString();
    switch (eventType) {
      case "repo:refs_changed":
        return ((Map<String, Object>) event.content.get("repository")).get("name").toString();
      case "pr:merged":
        Map<String, Object> toRef =
            (Map<String, Object>)
                ((Map<String, Object>) event.content.get("pullRequest")).get("toRef");
        if (toRef != null) {
          Map<String, Object> repo = (Map<String, Object>) toRef.get("repository");
          if (repo != null && repo.containsKey("name")) {
            return repo.get("name").toString();
          } else {
            return "";
          }
        } else {
          return "";
        }
      default:
        return "";
    }
  }

  private void handleBitbucketCloudEvent(Event event, Map postedEvent) {
    String repoProject = "";
    String slug = "";
    String hash = "";
    String branch = "";
    String action = "";

    BitbucketCloudEvent bitbucketCloudEvent =
        objectMapper.convertValue(postedEvent, BitbucketCloudEvent.class);
    if (bitbucketCloudEvent.repository != null) {
      slug = StringUtils.defaultIfEmpty(bitbucketCloudEvent.repository.fullName, "");
      if (bitbucketCloudEvent.repository.owner != null) {
        repoProject = StringUtils.defaultIfEmpty(bitbucketCloudEvent.repository.owner.username, "");
      }
    }
    if (bitbucketCloudEvent.pullRequest != null) {
      BitbucketCloudEvent.PullRequest pullRequest = bitbucketCloudEvent.pullRequest;
      if (pullRequest.mergeCommit != null) {
        hash = StringUtils.defaultIfEmpty(pullRequest.mergeCommit.hash, "");
      }
      if (pullRequest.destination != null && pullRequest.destination.branch != null) {
        branch = StringUtils.defaultIfEmpty(pullRequest.destination.branch.name, "");
      }
    } else if (bitbucketCloudEvent.push != null) {
      BitbucketCloudEvent.Push push = bitbucketCloudEvent.push;
      if (!push.changes.isEmpty()) {
        BitbucketCloudEvent.Change change = push.changes.get(0);
        if (change.newObj != null) {
          branch = StringUtils.defaultIfEmpty(change.newObj.name, "");
        }
        if (!change.commits.isEmpty()) {
          BitbucketCloudEvent.Commit commit = change.commits.get(0);
          hash = StringUtils.defaultIfEmpty(commit.hash, "");
        }
      }
    }
    action = StringUtils.defaultIfEmpty(event.content.get("event_type").toString(), "");

    event.content.put("repoProject", repoProject);
    event.content.put("slug", slug);
    event.content.put("hash", hash);
    event.content.put("branch", branch);
    event.content.put("action", action);
  }

  @Data
  private static class BitbucketCloudEvent {
    Repository repository;

    @JsonProperty("pullrequest")
    PullRequest pullRequest;

    Push push;

    @Data
    private static class Repository {
      @JsonProperty("full_name")
      String fullName;

      Owner owner;
    }

    @Data
    private static class Owner {
      String username;
    }

    @Data
    private static class PullRequest {
      @JsonProperty("merge_commit")
      MergeCommit mergeCommit;

      Destination destination;
    }

    @Data
    private static class MergeCommit {
      String hash;
    }

    @Data
    private static class Push {
      List<Change> changes;
    }

    @Data
    private static class Change {
      List<Commit> commits;

      @JsonProperty("new")
      NewObj newObj;
    }

    @Data
    private static class Commit {
      String hash;
    }

    @Data
    private static class NewObj {
      String name;
    }

    @Data
    private static class Destination {
      Branch branch;
    }

    @Data
    private static class Branch {
      String name;
    }
  }
}
