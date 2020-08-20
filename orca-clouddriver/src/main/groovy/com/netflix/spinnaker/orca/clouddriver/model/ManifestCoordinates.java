/*
 * Copyright 2020 Google, Inc.
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.base.Strings;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNullableByDefault;
import lombok.Builder;
import lombok.Value;

@JsonDeserialize(builder = ManifestCoordinates.ManifestCoordinatesBuilder.class)
@NonnullByDefault
@Value
public final class ManifestCoordinates {
  private final String kind;
  private final String namespace;
  private final String name;

  @Builder(toBuilder = true)
  @ParametersAreNullableByDefault
  private ManifestCoordinates(@Nonnull String kind, String namespace, String name) {
    this.kind = Objects.requireNonNull(kind);
    this.namespace = Strings.nullToEmpty(namespace);
    this.name = Strings.nullToEmpty(name);
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static final class ManifestCoordinatesBuilder {}

  @JsonIgnore
  public String getFullResourceName() {
    return String.join(" ", this.kind, this.name);
  }
}
