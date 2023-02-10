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
import java.util.Optional;
import lombok.Data;

@Data
public class GithubPullRequestEvent implements GithubWebhookEvent {
  private Repository repository;

  @JsonProperty("pull_request")
  private PullRequest pullRequest;

  private String action;

  // `.repository.full_name`
  @Override
  public String getFullRepoName() {
    return Optional.of(this)
        .map(GithubPullRequestEvent::getRepository)
        .map(Repository::getFullName)
        .orElse("");
  }

  // `.repository.owner.login`
  @Override
  public String getRepoProject() {
    return Optional.of(this)
        .map(GithubPullRequestEvent::getRepository)
        .map(Repository::getOwner)
        .map(RepositoryOwner::getLogin)
        .orElse("");
  }

  // `.repository.name`
  @Override
  public String getSlug() {
    return Optional.of(this)
        .map(GithubPullRequestEvent::getRepository)
        .map(Repository::getName)
        .orElse("");
  }

  // `.pull_request.head.sha`
  // TODO
  @Override
  public String getHash() {
    return Optional.of(this)
        .map(GithubPullRequestEvent::getPullRequest)
        .map(PullRequest::getHead)
        .map(PullRequestHead::getSha)
        .orElse("");
  }

  // `.pull_request.head.ref`
  // TODO
  @Override
  public String getBranch() {
    // Replace on "" still returns "", which is fine
    return Optional.of(this)
        .map(GithubPullRequestEvent::getPullRequest)
        .map(PullRequest::getHead)
        .map(PullRequestHead::getRef)
        .orElse("");
  }

  @Override
  public String getAction() {
    return action == null ? "" : action;
  }

  @Data
  private static class Repository {
    private RepositoryOwner owner;
    private String name;

    @JsonProperty("full_name")
    private String fullName;
  }

  @Data
  private static class RepositoryOwner {
    private String login;
  }

  @Data
  public static class PullRequest {
    private PullRequestHead head;
    private Boolean draft;
    private int number;
    private String state;
    private String title;
  }

  @Data
  private static class PullRequestHead {
    private String ref;
    private String sha;
  }
}
