/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.orca.clouddriver.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.ParametersAreNullableByDefault;
import lombok.Builder;
import lombok.Value;

@JsonDeserialize(builder = Manifest.ManifestBuilder.class)
@NonnullByDefault
@Value
public final class Manifest {
  private final ImmutableMap<String, Object> manifest;
  private final ImmutableList<Artifact> artifacts;
  private final Status status;
  private final String name;
  private final ImmutableList<String> warnings;
  private final ImmutableList<Object> events;

  @Builder(toBuilder = true)
  @ParametersAreNullableByDefault
  private Manifest(
      Map<String, Object> manifest,
      List<Artifact> artifacts,
      Status status,
      String name,
      List<String> warnings,
      List<Object> events) {
    this.manifest =
        Optional.ofNullable(manifest).map(ImmutableMap::copyOf).orElseGet(ImmutableMap::of);
    this.artifacts =
        Optional.ofNullable(artifacts).map(ImmutableList::copyOf).orElseGet(ImmutableList::of);
    this.status = Optional.ofNullable(status).orElseGet(() -> Status.builder().build());
    this.name = Optional.ofNullable(name).orElse("");
    this.warnings =
        Optional.ofNullable(warnings).map(ImmutableList::copyOf).orElseGet(ImmutableList::of);
    this.events =
        Optional.ofNullable(events).map(ImmutableList::copyOf).orElseGet(ImmutableList::of);
  }

  @JsonDeserialize(builder = Manifest.Status.StatusBuilder.class)
  @Value
  public static final class Status {
    private final Condition stable;
    private final Condition failed;

    @Builder(toBuilder = true)
    @ParametersAreNullableByDefault
    private Status(Condition stable, Condition failed) {
      this.stable = Optional.ofNullable(stable).orElseGet(Condition::emptyFalse);
      this.failed = Optional.ofNullable(failed).orElseGet(Condition::emptyFalse);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class StatusBuilder {}
  }

  @Value
  public static final class Condition {
    private static final Condition TRUE = new Condition(true, "");
    private static final Condition FALSE = new Condition(false, "");

    private final boolean state;
    private final String message;

    @ParametersAreNullableByDefault
    public Condition(
        @JsonProperty("state") boolean state, @JsonProperty("message") String message) {
      this.state = state;
      this.message = Optional.ofNullable(message).orElse("");
    }

    public static Condition emptyFalse() {
      return FALSE;
    }

    public static Condition emptyTrue() {
      return TRUE;
    }
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static final class ManifestBuilder {}
}
