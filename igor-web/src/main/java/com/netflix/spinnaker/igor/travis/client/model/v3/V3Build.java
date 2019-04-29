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
import com.netflix.spinnaker.igor.travis.client.model.Config;
import java.time.Instant;
import java.util.List;
import org.simpleframework.xml.Default;
import org.simpleframework.xml.Root;

@Default
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Root(name = "builds")
public class V3Build {
  private V3Branch branch;

  @JsonProperty("commit_id")
  private int commitId;

  private V3Commit commit;
  private int duration;

  @JsonProperty("event_type")
  private String eventType;

  private int id;
  private V3Repository repository;

  @JsonProperty("repository_id")
  private int repositoryId;

  private int number;
  private TravisBuildState state;

  @JsonProperty("finished_at")
  private Instant finishedAt;

  private List<V3Job> jobs;
  private Config config;

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

  public V3Branch getBranch() {
    return branch;
  }

  public void setBranch(V3Branch branch) {
    this.branch = branch;
  }

  public int getCommitId() {
    return commitId;
  }

  public void setCommitId(int commitId) {
    this.commitId = commitId;
  }

  public V3Commit getCommit() {
    return commit;
  }

  public void setCommit(V3Commit commit) {
    this.commit = commit;
  }

  public int getDuration() {
    return duration;
  }

  public void setDuration(int duration) {
    this.duration = duration;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public V3Repository getRepository() {
    return repository;
  }

  public void setRepository(V3Repository repository) {
    this.repository = repository;
  }

  public int getRepositoryId() {
    return repositoryId;
  }

  public void setRepositoryId(int repositoryId) {
    this.repositoryId = repositoryId;
  }

  public int getNumber() {
    return number;
  }

  public void setNumber(int number) {
    this.number = number;
  }

  public TravisBuildState getState() {
    return state;
  }

  public void setState(TravisBuildState state) {
    this.state = state;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(Instant finishedAt) {
    this.finishedAt = finishedAt;
  }

  public List<V3Job> getJobs() {
    return jobs;
  }

  public void setJobs(List<V3Job> jobs) {
    this.jobs = jobs;
  }

  public Config getConfig() {
    return config;
  }

  public void setConfig(Config config) {
    this.config = config;
  }
}
