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
import java.time.Instant;
import org.simpleframework.xml.Default;
import org.simpleframework.xml.Root;

@Default
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Root(strict = false)
public class Repo {
  private int id;
  private String slug;
  private String description;

  @JsonProperty("last_build_id")
  private int lastBuildId;

  @JsonProperty("last_build_number")
  private int lastBuildNumber;

  @JsonProperty("last_build_state")
  private String lastBuildState;

  @JsonProperty("last_build_duration")
  private int lastBuildDuration;

  @JsonProperty("last_build_started_at")
  private Instant lastBuildStartedAt;

  @JsonProperty("last_build_finished_at")
  private Instant lastBuildFinishedAt;

  @JsonProperty("github_language")
  private String githubLanguage;

  public long timestamp() {
    return lastBuildFinishedAt.toEpochMilli();
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public String getSlug() {
    return slug;
  }

  public void setSlug(String slug) {
    this.slug = slug;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public int getLastBuildId() {
    return lastBuildId;
  }

  public void setLastBuildId(int lastBuildId) {
    this.lastBuildId = lastBuildId;
  }

  public int getLastBuildNumber() {
    return lastBuildNumber;
  }

  public void setLastBuildNumber(int lastBuildNumber) {
    this.lastBuildNumber = lastBuildNumber;
  }

  public String getLastBuildState() {
    return lastBuildState;
  }

  public void setLastBuildState(String lastBuildState) {
    this.lastBuildState = lastBuildState;
  }

  public int getLastBuildDuration() {
    return lastBuildDuration;
  }

  public void setLastBuildDuration(int lastBuildDuration) {
    this.lastBuildDuration = lastBuildDuration;
  }

  public Instant getLastBuildStartedAt() {
    return lastBuildStartedAt;
  }

  public void setLastBuildStartedAt(Instant lastBuildStartedAt) {
    this.lastBuildStartedAt = lastBuildStartedAt;
  }

  public Instant getLastBuildFinishedAt() {
    return lastBuildFinishedAt;
  }

  public void setLastBuildFinishedAt(Instant lastBuildFinishedAt) {
    this.lastBuildFinishedAt = lastBuildFinishedAt;
  }

  public String getGithubLanguage() {
    return githubLanguage;
  }

  public void setGithubLanguage(String githubLanguage) {
    this.githubLanguage = githubLanguage;
  }
}
