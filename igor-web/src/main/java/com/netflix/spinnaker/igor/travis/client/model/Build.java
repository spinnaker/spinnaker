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

package com.netflix.spinnaker.igor.travis.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.igor.travis.client.model.v3.TravisBuildState;
import java.time.Instant;
import java.util.List;
import org.simpleframework.xml.Default;
import org.simpleframework.xml.Root;

@Default
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Root(name = "builds")
public class Build {
  @JsonProperty("commit_id")
  private int commitId;

  private int duration;
  private int id;

  @JsonProperty("repository_id")
  private int repositoryId;

  private int number;
  private TravisBuildState state;

  @JsonProperty("finished_at")
  private Instant finishedAt;

  @JsonProperty("pull_request")
  private Boolean pullRequest;

  @JsonProperty(value = "job_ids")
  private List<Integer> job_ids;

  private Config config;

  public long getTimestamp() {
    if (finishedAt == null) {
      return 0;
    }
    return finishedAt.toEpochMilli();
  }

  public int getCommitId() {
    return commitId;
  }

  public void setCommitId(int commitId) {
    this.commitId = commitId;
  }

  public int getDuration() {
    return duration;
  }

  public void setDuration(int duration) {
    this.duration = duration;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
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

  public Boolean getPullRequest() {
    return pullRequest;
  }

  public void setPullRequest(Boolean pullRequest) {
    this.pullRequest = pullRequest;
  }

  public List<Integer> getJob_ids() {
    return job_ids;
  }

  public void setJob_ids(List<Integer> job_ids) {
    this.job_ids = job_ids;
  }

  public Config getConfig() {
    return config;
  }

  public void setConfig(Config config) {
    this.config = config;
  }
}
