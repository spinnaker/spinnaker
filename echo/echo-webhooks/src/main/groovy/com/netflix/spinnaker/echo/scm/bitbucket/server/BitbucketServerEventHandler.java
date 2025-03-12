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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class BitbucketServerEventHandler {

  private final List<String> bitbucketServerEventTypes =
      List.of(
          "pr:opened",
          "repo:refs_changed",
          "pr:from_ref_updated",
          "pr:merged",
          "pr:declined",
          "pr:deleted");

  private String repoProject;
  private String slug;
  private String hash;
  private String branch;

  private final ObjectMapper objectMapper = EchoObjectMapper.getInstance();

  public boolean looksLikeBitbucketServer(Event event) {
    String eventType = event.content.get("event_type").toString();
    return bitbucketServerEventTypes.contains(eventType);
  }

  public void handleBitbucketServerEvent(Event event) {

    if (!event.content.containsKey("event_type")) {
      return;
    }

    String eventType = event.content.get("event_type").toString();

    switch (eventType) {
      case "pr:opened":
        handlePrOpenedEvent(event);
        break;

      case "repo:refs_changed":
        handleRepoRefsChangedEvent(event);
        break;

      case "pr:from_ref_updated":
        handlePrFromRefUpdatedEvent(event);
        break;

      case "pr:merged":
        handlePrMergedEvent(event);
        break;

      case "pr:declined":
        handlePrDeclinedEvent(event);
        break;

      case "pr:deleted":
        handlePrDeletedEvent(event);
        break;

      default: // Do nothing
        break;
    }

    event.content.put("repoProject", repoProject);
    event.content.put("slug", slug);
    event.content.put("hash", hash);
    event.content.put("branch", branch);
    event.content.put("action", eventType);
  }

  private void handlePrOpenedEvent(Event event) {
    BitbucketServerPrEvent prOpenedEvent =
        objectMapper.convertValue(event.content, BitbucketServerPrEvent.class);

    if (prOpenedEvent.getPullRequest() != null
        && prOpenedEvent.getPullRequest().getFromRef() != null) {

      BitbucketServerPrEvent.Ref fromRef = prOpenedEvent.getPullRequest().getFromRef();
      branch = StringUtils.defaultIfEmpty(fromRef.getId(), "").replace("refs/heads/", "");

      if (fromRef.getRepository() != null) {
        repoProject = StringUtils.defaultIfEmpty(fromRef.getRepository().getProject().getKey(), "");
        slug = StringUtils.defaultIfEmpty(fromRef.getRepository().getSlug(), "");
      }

      if (fromRef.getLatestCommit() != null) {
        hash = StringUtils.defaultIfEmpty(fromRef.latestCommit, "");
      }
    }
  }

  private void handleRepoRefsChangedEvent(Event event) {
    BitbucketServerRepoEvent refsChangedEvent =
        objectMapper.convertValue(event.content, BitbucketServerRepoEvent.class);

    if (refsChangedEvent.repository != null) {
      repoProject = StringUtils.defaultIfEmpty(refsChangedEvent.repository.project.key, "");
      slug = StringUtils.defaultIfEmpty(refsChangedEvent.repository.slug, "");
    }

    if (!refsChangedEvent.changes.isEmpty()) {
      BitbucketServerRepoEvent.Change change = refsChangedEvent.changes.get(0);
      hash = StringUtils.defaultIfEmpty(change.toHash, "");
      if (change.ref != null) {
        branch = StringUtils.defaultIfEmpty(change.ref.id, "").replace("refs/heads/", "");
      }
    }
  }

  private void handlePrFromRefUpdatedEvent(Event event) {
    BitbucketServerPrEvent fromRefUpdatedEvent =
        objectMapper.convertValue(event.content, BitbucketServerPrEvent.class);

    if (fromRefUpdatedEvent.getPullRequest() != null
        && fromRefUpdatedEvent.getPullRequest().getFromRef() != null) {

      BitbucketServerPrEvent.Ref fromRef = fromRefUpdatedEvent.getPullRequest().getFromRef();
      branch = StringUtils.defaultIfEmpty(fromRef.getId(), "").replace("refs/heads/", "");

      if (fromRef.getRepository() != null) {
        repoProject = StringUtils.defaultIfEmpty(fromRef.getRepository().getProject().getKey(), "");
        slug = StringUtils.defaultIfEmpty(fromRef.getRepository().getSlug(), "");
      }

      if (fromRef.getLatestCommit() != null) {
        hash = StringUtils.defaultIfEmpty(fromRef.latestCommit, "");
      }
    }
  }

  private void handlePrMergedEvent(Event event) {
    BitbucketServerPrEvent prMergedEvent =
        objectMapper.convertValue(event.content, BitbucketServerPrEvent.class);

    if (prMergedEvent.getPullRequest() != null && prMergedEvent.getPullRequest().toRef != null) {
      BitbucketServerPrEvent.Ref toRef = prMergedEvent.getPullRequest().toRef;
      branch = StringUtils.defaultIfEmpty(toRef.getId(), "").replace("refs/heads/", "");
      if (toRef.getRepository() != null) {
        repoProject = StringUtils.defaultIfEmpty(toRef.getRepository().getProject().getKey(), "");
        slug = StringUtils.defaultIfEmpty(toRef.getRepository().getSlug(), "");
      }
    }

    if (prMergedEvent.getPullRequest() != null
        && prMergedEvent.getPullRequest().getProperties() != null) {
      BitbucketServerPrEvent.Properties properties = prMergedEvent.getPullRequest().getProperties();
      if (properties.getMergeCommit() != null) {
        hash = StringUtils.defaultIfEmpty(properties.getMergeCommit().getId(), "");
      }
    }
  }

  private void handlePrDeclinedEvent(Event event) {
    BitbucketServerPrEvent prDeclinedEvent =
        objectMapper.convertValue(event.content, BitbucketServerPrEvent.class);

    if (prDeclinedEvent.getPullRequest() != null
        && prDeclinedEvent.getPullRequest().getFromRef() != null) {

      BitbucketServerPrEvent.Ref fromRef = prDeclinedEvent.getPullRequest().getFromRef();
      branch = StringUtils.defaultIfEmpty(fromRef.getId(), "").replace("refs/heads/", "");

      if (fromRef.getRepository() != null) {
        repoProject = StringUtils.defaultIfEmpty(fromRef.getRepository().getProject().getKey(), "");
        slug = StringUtils.defaultIfEmpty(fromRef.getRepository().getSlug(), "");
      }

      if (fromRef.getLatestCommit() != null) {
        hash = StringUtils.defaultIfEmpty(fromRef.latestCommit, "");
      }
    }
  }

  private void handlePrDeletedEvent(Event event) {
    BitbucketServerPrEvent prDeletedEvent =
        objectMapper.convertValue(event.content, BitbucketServerPrEvent.class);

    if (prDeletedEvent.getPullRequest() != null
        && prDeletedEvent.getPullRequest().getFromRef() != null) {

      BitbucketServerPrEvent.Ref fromRef = prDeletedEvent.getPullRequest().getFromRef();
      branch = StringUtils.defaultIfEmpty(fromRef.getId(), "").replace("refs/heads/", "");

      if (fromRef.getRepository() != null) {
        repoProject = StringUtils.defaultIfEmpty(fromRef.getRepository().getProject().getKey(), "");
        slug = StringUtils.defaultIfEmpty(fromRef.getRepository().getSlug(), "");
      }

      if (fromRef.getLatestCommit() != null) {
        hash = StringUtils.defaultIfEmpty(fromRef.latestCommit, "");
      }
    }
  }
}
