/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;

@JsonTypeName("git")
public class GitTrigger extends Trigger {

  private final String source;
  private final String project;
  private final String branch;
  private final String slug;

  @JsonCreator
  public GitTrigger(
    @JsonProperty("source") @Nonnull String source,
    @JsonProperty("project") @Nonnull String project,
    @JsonProperty("branch") @Nonnull String branch,
    @JsonProperty("slug") @Nonnull String slug,
    @JsonProperty("user") @Nullable String user,
    @JsonProperty("parameters") @Nullable Map<String, Object> parameters,
    @JsonProperty("artifacts") @Nullable List<Artifact> artifacts
  ) {
    super(user, parameters, artifacts);
    this.source = source;
    this.project = project;
    this.branch = branch;
    this.slug = slug;
  }

  public String getSource() {
    return source;
  }

  public String getProject() {
    return project;
  }

  public String getBranch() {
    return branch;
  }

  public String getSlug() {
    return slug;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    GitTrigger that = (GitTrigger) o;
    return Objects.equals(source, that.source) &&
      Objects.equals(project, that.project) &&
      Objects.equals(branch, that.branch) &&
      Objects.equals(slug, that.slug);
  }

  @Override public int hashCode() {
    return Objects.hash(super.hashCode(), source, project, branch, slug);
  }

  @Override public String toString() {
    return "GitTrigger{" +
      super.toString() +
      ", source='" + source + '\'' +
      ", project='" + project + '\'' +
      ", branch='" + branch + '\'' +
      ", slug='" + slug + '\'' +
      '}';
  }
}
