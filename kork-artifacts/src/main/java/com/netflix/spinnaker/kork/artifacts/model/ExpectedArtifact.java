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
import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
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

    defaultArtifact = store(defaultArtifact);
    boundArtifact = store(boundArtifact);
    matchArtifact = store(matchArtifact);

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
    if (!matchTypes(matchArtifact.getType(), other.getType())) {
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

    return matches(matchArtifact.getReference(), other.getReference());
  }

  private boolean matches(@Nullable String us, @Nullable String other) {
    if (StringUtils.isEmpty(us)) {
      return true;
    }

    if (other == null) {
      return false;
    }

    // The strict equals is mostly to ensure base64 references can be compared
    // against each other, since the '+' is a completely valid base64 character.
    // The '+' in regex has the meaning of one or more, and will change the
    // semantics of comparing two references.
    //
    // So rather than having references implement its own matching, users may
    // rely on matching artifacts with some regex. To be backwards compatible we
    // will do a strict comparison as well as pattern matching.
    return us.equals(other) || patternMatches(us, other);
  }

  /**
   * Checks to see if artifact types are compatible/matchable. This handles the four known cases of:
   *
   * <pre>
   * type_a matches type_b
   * type_a is embedded/base and type_b is remote/base64
   * type_b is embedded/base and type_a is remote/base64
   * and false otherwise
   * </pre>
   */
  private boolean matchTypes(String us, String other) {
    if (matches(us, other)) {
      return true;
    }

    if (us.equals(ArtifactTypes.EMBEDDED_BASE64.getMimeType())) {
      return other.equals(ArtifactTypes.REMOTE_BASE64.getMimeType());
    }

    if (other.equals(ArtifactTypes.EMBEDDED_BASE64.getMimeType())) {
      return us.equals(ArtifactTypes.REMOTE_BASE64.getMimeType());
    }

    return false;
  }

  private boolean patternMatches(String us, String other) {
    return Pattern.compile(us).matcher(other).matches();
  }

  /** Helper store method to easily store the artifact if needed */
  private static Artifact store(Artifact artifact) {
    ArtifactStore storage = ArtifactStore.getInstance();
    if (artifact == null
        || storage == null
        || !ArtifactTypes.EMBEDDED_BASE64.getMimeType().equals(artifact.getType())) {
      return artifact;
    }

    if (artifact.getReference() != null && !artifact.getReference().isEmpty()) {
      return storage.store(artifact);
    }

    return artifact;
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static final class ExpectedArtifactBuilder {}
}
