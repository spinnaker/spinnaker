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

@JsonTypeName("docker")
public class DockerTrigger extends Trigger {

  private final String repository;
  private final String tag;

  @JsonCreator
  public DockerTrigger(
    @JsonProperty("repository") @Nonnull String repository,
    @JsonProperty("tag") @Nullable String tag,
    @JsonProperty("user") @Nullable String user,
    @JsonProperty("parameters") @Nullable Map<String, Object> parameters,
    @JsonProperty("artifacts") @Nullable List<Artifact> artifacts
  ) {
    super(user, parameters, artifacts);
    this.repository = repository;
    this.tag = tag;
  }

  public @Nonnull String getRepository() {
    return repository;
  }

  public @Nullable String getTag() {
    return tag;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    DockerTrigger that = (DockerTrigger) o;
    return Objects.equals(repository, that.repository) &&
      Objects.equals(tag, that.tag);
  }

  @Override public int hashCode() {
    return Objects.hash(super.hashCode(), repository, tag);
  }

  @Override public String toString() {
    return "DockerTrigger{" +
      super.toString() +
      ", repository=" + repository +
      ", tag=" + tag +
      "}";
  }
}
