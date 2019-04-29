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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.igor.build.model.GenericGitRevision;
import java.time.Instant;
import org.apache.commons.lang.StringUtils;
import org.simpleframework.xml.Default;
import org.simpleframework.xml.Root;

@Default
@Root(name = "commits")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Commit {
  private int id;
  private String sha;
  private String branch;
  private String message;

  @JsonProperty("author_name")
  private String authorName;

  @JsonProperty("compare_url")
  private String compareUrl;

  @JsonProperty("committed_at")
  private Instant timestamp;

  public GenericGitRevision getGenericGitRevision() {
    return GenericGitRevision.builder()
        .name(branch)
        .branch(branch)
        .sha1(sha)
        .committer(authorName)
        .compareUrl(compareUrl)
        .message(message)
        .timestamp(timestamp)
        .build();
  }

  public boolean isTag() {
    return compareUrl != null
        && StringUtils.substringAfterLast(compareUrl, "/compare/").matches(branch);
  }

  public String getBranchNameWithTagHandling() {
    if (isTag()) {
      return "tags";
    }

    return branch;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public String getSha() {
    return sha;
  }

  public void setSha(String sha) {
    this.sha = sha;
  }

  public String getBranch() {
    return branch;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getAuthorName() {
    return authorName;
  }

  public void setAuthorName(String authorName) {
    this.authorName = authorName;
  }

  public String getCompareUrl() {
    return compareUrl;
  }

  public void setCompareUrl(String compareUrl) {
    this.compareUrl = compareUrl;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Instant timestamp) {
    this.timestamp = timestamp;
  }
}
