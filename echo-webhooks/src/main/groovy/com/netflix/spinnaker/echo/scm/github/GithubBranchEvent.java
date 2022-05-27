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

package com.netflix.spinnaker.echo.scm.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import lombok.Data;

@Data
public class GithubBranchEvent implements GithubWebhookEvent {
  private Repository repository;
  private String ref;
  private String action;

  // `.repository.full_name`
  @Override
  public String getFullRepoName() {
    return Optional.of(this)
        .map(GithubBranchEvent::getRepository)
        .map(Repository::getFullName)
        .orElse("");
  }

  // `.repository.owner.login`
  @Override
  public String getRepoProject() {
    return Optional.of(this)
        .map(GithubBranchEvent::getRepository)
        .map(Repository::getOwner)
        .map(RepositoryOwner::getLogin)
        .orElse("");
  }

  // `.repository.name`
  @Override
  public String getSlug() {
    return Optional.of(this)
        .map(GithubBranchEvent::getRepository)
        .map(Repository::getName)
        .orElse("");
  }

  // `.sha` (doesn't exist, but it's required by the interface)
  @Override
  public String getHash() {
    return "";
  }

  // `.ref`
  @Override
  public String getBranch() {
    return Optional.of(this).map(GithubBranchEvent::getRef).orElse("");
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
    String login;
  }
}
