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

package com.netflix.spinnaker.kork.artifacts.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.base.Strings;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNullableByDefault;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

@JsonDeserialize(builder = ExpectedArtifact.ExpectedArtifactBuilder.class)
@NonnullByDefault
@Value
public final class ExpectedArtifact {
  private final Artifact matchArtifact;
  private final boolean usePriorArtifact;
  private final boolean useDefaultArtifact;
  @Nullable private final Artifact defaultArtifact;
  private final String id; // UUID to use this ExpectedArtifact by reference in Pipelines.
  @Nullable private final Artifact boundArtifact;

  @Builder(toBuilder = true)
  @ParametersAreNullableByDefault
  private ExpectedArtifact(
      Artifact matchArtifact,
      boolean usePriorArtifact,
      boolean useDefaultArtifact,
      Artifact defaultArtifact,
      String id,
      Artifact boundArtifact) {
    this.matchArtifact =
        Optional.ofNullable(matchArtifact).orElseGet(() -> Artifact.builder().build());
    this.usePriorArtifact = usePriorArtifact;
    this.useDefaultArtifact = useDefaultArtifact;
    this.defaultArtifact = defaultArtifact;
    this.id = Strings.nullToEmpty(id);
    this.boundArtifact = boundArtifact;
  }

  /**
   * Decide if the "matchArtifact" matches the incoming artifact. Any fields not specified in the
   * "matchArtifact" are not compared.
   *
   * @param other is the artifact to match against
   * @return true i.f.f. the artifacts match
   */
  public boolean matches(Artifact other) {
    String thisType = matchArtifact.getType();
    String otherType = other.getType();
    if (!matches(thisType, otherType)) {
      return false;
    }

    String thisName = matchArtifact.getName();
    String otherName = other.getName();
    if (!matches(thisName, otherName)) {
      return false;
    }

    String thisVersion = matchArtifact.getVersion();
    String otherVersion = other.getVersion();
    if (!matches(thisVersion, otherVersion)) {
      return false;
    }

    String thisLocation = matchArtifact.getLocation();
    String otherLocation = other.getLocation();
    if (!matches(thisLocation, otherLocation)) {
      return false;
    }

    String thisReference = matchArtifact.getReference();
    String otherReference = other.getReference();
    if (!matches(thisReference, otherReference)) {
      return false;
    }

    // Explicitly avoid matching on UUID, provenance & artifactAccount

    return true;
  }

  private boolean matches(@Nullable String us, @Nullable String other) {
    return StringUtils.isEmpty(us) || (other != null && patternMatches(us, other));
  }

  private boolean patternMatches(String us, String other) {
    return Pattern.compile(us).matcher(other).matches();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static final class ExpectedArtifactBuilder {}
}
