/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.echo.scm.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.echo.model.Event;
import java.util.Map;
import java.util.Optional;
import lombok.Data;

@Data
public class GithubPushEvent implements GithubWebhookEvent {
  Repository repository;
  String after;
  String ref;

  public static final String ACTION = "push";

  // `.repository.full_name`
  @Override
  public String getFullRepoName(Event event, Map postedEvent) {
    return Optional.of(this)
        .map(GithubPushEvent::getRepository)
        .map(Repository::getFullName)
        .orElse("");
  }

  // `.repository.owner.name`
  @Override
  public String getRepoProject(Event event, Map postedEvent) {
    return Optional.of(this)
        .map(GithubPushEvent::getRepository)
        .map(Repository::getOwner)
        .map(RepositoryOwner::getName)
        .orElse("");
  }

  // `.repository.name`
  @Override
  public String getSlug(Event event, Map postedEvent) {
    return Optional.of(this)
        .map(GithubPushEvent::getRepository)
        .map(Repository::getName)
        .orElse("");
  }

  // `.after`
  @Override
  public String getHash(Event event, Map postedEvent) {
    return Optional.of(this).map(GithubPushEvent::getAfter).orElse("");
  }

  // `.ref`, remove `refs/heads/`
  @Override
  public String getBranch(Event event, Map postedEvent) {
    // Replace on "" still returns "", which is fine
    return Optional.of(this).map(GithubPushEvent::getRef).orElse("").replace("refs/heads/", "");
  }

  @Override
  public String getAction(Event event, Map postedEvent) {
    return ACTION;
  }

  @Data
  private static class Repository {
    RepositoryOwner owner;
    String name;

    @JsonProperty("full_name")
    String fullName;
  }

  @Data
  private static class RepositoryOwner {
    String name;
  }
}
