/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.travis.client.model.v3;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.igor.build.model.GenericGitRevision;
import java.time.Instant;
import java.util.List;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Getter
@Setter
@XmlRootElement(name = "builds")
public class V3Build {

  private V3Branch branch;

  @JsonProperty("commit_id")
  private long commitId;

  private V3Commit commit;

  private int duration;

  @JsonProperty("event_type")
  private String eventType;

  @EqualsAndHashCode.Include private long id;

  private V3Repository repository;

  @JsonProperty("repository_id")
  private long repositoryId;

  private long number;

  @EqualsAndHashCode.Include private TravisBuildState state;

  @JsonProperty("finished_at")
  private Instant finishedAt;

  @JsonProperty("log_complete")
  private Boolean logComplete;

  private List<V3Job> jobs;

  public long getTimestamp() {
    return finishedAt.toEpochMilli();
  }

  public String branchedRepoSlug() {
    if (commit.isPullRequest()) {
      return repository.getSlug() + "/pull_request_" + branch.getName();
    }

    if (commit.isTag()) {
      return repository.getSlug() + "/tags";
    }

    return repository.getSlug() + "/" + branch.getName();
  }

  public GenericGitRevision genericGitRevision() {
    return GenericGitRevision.builder()
        .name(branch.getName())
        .branch(branch.getName())
        .sha1(commit.getSha())
        .build();
  }

  public boolean spinnakerTriggered() {
    return ("api".equals(eventType)
        && commit != null
        && commit.getMessage() != null
        && (commit.getMessage().startsWith("Triggered from spinnaker")
            || commit.getMessage().startsWith("Triggered from Spinnaker")));
  }

  public String toString() {
    String tmpSlug = "unknown/repository";
    if (repository != null) {
      tmpSlug = repository.getSlug();
    }

    return "[" + tmpSlug + ":" + number + ":" + state + "]";
  }
}
