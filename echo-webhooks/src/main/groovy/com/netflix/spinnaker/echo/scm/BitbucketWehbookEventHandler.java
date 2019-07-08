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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.model.Event;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BitbucketWehbookEventHandler implements GitWebhookHandler {

  private ObjectMapper objectMapper;

  public BitbucketWehbookEventHandler() {
    this.objectMapper =
        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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

  public void handle(Event event, Map postedEvent) {
    if (event.rawContent.isEmpty() || !event.content.containsKey("event_type")) {
      log.info("Handling Bitbucket Server ping.");
      return;
    }

    if (looksLikeBitbucketCloud(event)) {
      handleBitbucketCloudEvent(event, postedEvent);
    } else if (looksLikeBitbucketServer(event)) {
      handleBitbucketServerEvent(event, postedEvent);
    } else {
      // Could not determine what type of Bitbucket event this was.
      log.info(
          "Could not determine Bitbucket type {}",
          kv("event_type", event.content.get("event_type")));
      return;
    }

    String fullRepoName = getFullRepoName(event);

    if (fullRepoName != "") {
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

  private boolean looksLikeBitbucketServer(Event event) {
    String eventType = event.content.get("event_type").toString();
    return (eventType.equals("repo:refs_changed") || eventType.equals("pr:merged"));
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

    BitbucketCloudEvent bitbucketCloudEvent =
        objectMapper.convertValue(postedEvent, BitbucketCloudEvent.class);
    if (bitbucketCloudEvent.repository != null) {
      slug = emptyOrDefault(bitbucketCloudEvent.repository.fullName, "");
      if (bitbucketCloudEvent.repository.owner != null) {
        repoProject = emptyOrDefault(bitbucketCloudEvent.repository.owner.username, "");
      }
    }
    if (bitbucketCloudEvent.pullRequest != null) {
      BitbucketCloudEvent.PullRequest pullRequest = bitbucketCloudEvent.pullRequest;
      if (pullRequest.mergeCommit != null) {
        hash = emptyOrDefault(pullRequest.mergeCommit.hash, "");
      }
      if (pullRequest.destination != null && pullRequest.destination.branch != null) {
        branch = emptyOrDefault(pullRequest.destination.branch.name, "");
      }
    } else if (bitbucketCloudEvent.push != null) {
      BitbucketCloudEvent.Push push = bitbucketCloudEvent.push;
      if (!push.changes.isEmpty()) {
        BitbucketCloudEvent.Change change = push.changes.get(0);
        if (change.newObj != null) {
          branch = emptyOrDefault(change.newObj.name, "");
        }
        if (!change.commits.isEmpty()) {
          BitbucketCloudEvent.Commit commit = change.commits.get(0);
          hash = emptyOrDefault(commit.hash, "");
        }
      }
    }

    event.content.put("repoProject", repoProject);
    event.content.put("slug", slug);
    event.content.put("hash", hash);
    event.content.put("branch", branch);
  }

  private void handleBitbucketServerEvent(Event event, Map postedEvent) {
    String repoProject = "";
    String slug = "";
    String hash = "";
    String branch = "";

    if (!event.content.containsKey("event_type")) {
      return;
    }

    String eventType = event.content.get("event_type").toString();
    if (eventType.equals("repo:refs_changed")) {
      BitbucketServerRefsChangedEvent refsChangedEvent =
          objectMapper.convertValue(event.content, BitbucketServerRefsChangedEvent.class);
      if (refsChangedEvent.repository != null) {
        repoProject = emptyOrDefault(refsChangedEvent.repository.project.key, "");
        slug = emptyOrDefault(refsChangedEvent.repository.slug, "");
      }
      if (!refsChangedEvent.changes.isEmpty()) {
        BitbucketServerRefsChangedEvent.Change change = refsChangedEvent.changes.get(0);
        hash = emptyOrDefault(change.toHash, "");
        if (change.ref != null) {
          branch = emptyOrDefault(change.ref.id, "").replace("refs/heads/", "");
        }
      }
    } else if (eventType.equals("pr:merged")) {
      BitbucketServerPrMergedEvent prMergedEvent =
          objectMapper.convertValue(event.content, BitbucketServerPrMergedEvent.class);
      if (prMergedEvent.pullRequest != null && prMergedEvent.pullRequest.toRef != null) {
        BitbucketServerPrMergedEvent.Ref toRef = prMergedEvent.pullRequest.toRef;
        branch = emptyOrDefault(toRef.id, "").replace("refs/heads/", "");
        if (toRef.repository != null) {
          repoProject = emptyOrDefault(toRef.repository.project.key, "");
          slug = emptyOrDefault(toRef.repository.slug, "");
        }
      }
      if (prMergedEvent.pullRequest != null && prMergedEvent.pullRequest.properties != null) {
        BitbucketServerPrMergedEvent.Properties properties = prMergedEvent.pullRequest.properties;
        if (properties.mergeCommit != null) {
          hash = emptyOrDefault(properties.mergeCommit.id, "");
        }
      }
    }

    event.content.put("repoProject", repoProject);
    event.content.put("slug", slug);
    event.content.put("hash", hash);
    event.content.put("branch", branch);
  }

  private String emptyOrDefault(String test, String def) {
    return !test.isEmpty() ? test : def;
  }

  @Data
  private static class BitbucketServerPrMergedEvent {
    PullRequest pullRequest;

    @Data
    private static class Properties {
      MergeCommit mergeCommit;
    }

    @Data
    private static class MergeCommit {
      String id;
    }

    @Data
    private static class PullRequest {
      Ref toRef;
      Properties properties;
    }

    @Data
    private static class Ref {
      String id;
      Repository repository;
    }

    @Data
    private static class Project {
      String key;
    }

    @Data
    private static class Repository {
      String name;
      String slug;
      Project project;
    }
  }

  @Data
  private static class BitbucketServerRefsChangedEvent {
    List<Change> changes;
    Repository repository;

    @Data
    private static class Project {
      String key;
    }

    @Data
    private static class Change {
      String toHash;
      Ref ref;
    }

    @Data
    private static class Ref {
      String id;
    }

    @Data
    private static class Repository {
      String name;
      String slug;
      Project project;
    }
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
